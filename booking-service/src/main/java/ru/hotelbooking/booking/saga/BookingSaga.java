package ru.hotelbooking.booking.saga;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.hotelbooking.booking.client.HotelCallException;
import ru.hotelbooking.booking.client.HotelClient;
import ru.hotelbooking.booking.client.SlotRejectedException;
import ru.hotelbooking.booking.client.dto.HotelReservation;
import ru.hotelbooking.booking.client.dto.HotelRoom;
import ru.hotelbooking.booking.client.dto.SlotRequest;
import ru.hotelbooking.booking.config.SagaProperties;
import ru.hotelbooking.booking.domain.Booking;
import ru.hotelbooking.booking.domain.BookingStatus;
import ru.hotelbooking.booking.domain.CancellationReason;
import ru.hotelbooking.booking.service.BookingErrorCodes;
import ru.hotelbooking.common.error.ApiException;
import ru.hotelbooking.common.error.ConflictException;
import ru.hotelbooking.common.error.ServiceUnavailableException;
import ru.hotelbooking.common.web.CorrelationHeaders;

/**
 * Оркестратор саги бронирования (последовательность локальных транзакций без распределённой транзакции):
 *
 * <ol>
 *   <li>локальная транзакция: бронь сохраняется в статусе {@code PENDING};
 *   <li>Hotel Service подтверждает доступность и удерживает слот (тайм-аут, повторы с паузами, circuit breaker);
 *   <li>локальная транзакция: {@code PENDING → CONFIRMED}; при сбое {@code PENDING → CANCELLED} и, если слот мог быть
 *       удержан, компенсирующее освобождение.
 * </ol>
 *
 * <p>Намеренно не {@code @Transactional}: во время удалённых вызовов транзакция БД не держится открытой. Каждый шаг
 * пишется в лог с id брони в MDC, поэтому сагу можно проследить по логам обоих сервисов.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingSaga {

    private final BookingStateService stateService;
    private final CompensationService compensationService;
    private final HotelClient hotelClient;
    private final SagaProperties properties;

    public SagaResult execute(BookingDraft draft) {
        var existing = stateService.findByRequest(draft.userId(), draft.requestId());
        if (existing.isPresent()) {
            return replay(existing.get(), draft);
        }
        Booking booking;
        try {
            booking = stateService.createPending(draft);
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            // тот же запрос обрабатывается другим потоком: вернуть ту бронь
            return replay(
                    stateService
                            .findByRequest(draft.userId(), draft.requestId())
                            .orElseThrow(() -> concurrentDuplicate),
                    draft);
        }

        try (var ignored = MDC.putCloseable(CorrelationHeaders.MDC_BOOKING_ID, String.valueOf(booking.getId()))) {
            long startedAt = System.nanoTime();
            log.info(
                    "SAGA_STARTED: user={}, autoSelect={}, room={}, period=[{}, {})",
                    draft.userId(),
                    draft.autoSelect(),
                    draft.roomId(),
                    draft.startDate(),
                    draft.endDate());
            try {
                Booking confirmed = holdAndConfirm(booking, draft);
                log.info("SAGA_FINISHED: CONFIRMED in {} ms", elapsedMillis(startedAt));
                return new SagaResult(confirmed, true);
            } catch (RuntimeException failure) {
                log.warn("SAGA_FINISHED: CANCELLED in {} ms ({})", elapsedMillis(startedAt), failure.getMessage());
                throw failure;
            }
        }
    }

    private Booking holdAndConfirm(Booking booking, BookingDraft draft) {
        long bookingId = booking.getId();
        List<Long> candidates = candidates(bookingId, draft);
        if (candidates.isEmpty()) {
            throw noRoomsAvailable(bookingId);
        }

        SlotRejectedException lastRejection = null;
        for (long roomId : candidates) {
            if (!stateService.assignRoom(bookingId, roomId)) {
                // фоновая задача восстановления уже отменила бронь: удерживать для неё номера больше нельзя
                throw notConfirmed(bookingId);
            }
            SlotRequest slot = SlotRequest.of(bookingId, roomId, draft.startDate(), draft.endDate());
            log.info("HOLD_REQUESTED: room={}, requestId={}", roomId, slot.requestId());
            try {
                HotelReservation reservation = hotelClient.confirmAvailability(roomId, slot);
                log.info("HOLD_CONFIRMED: room={}, reservation={}", roomId, reservation.id());
                return confirm(bookingId, roomId, draft, reservation);
            } catch (SlotRejectedException rejection) {
                log.info("HOLD_REJECTED: room={}, code={}", roomId, rejection.getCode());
                lastRejection = rejection;
            } catch (HotelCallException failure) {
                // исход неизвестен (тайм-аут/5xx/открытый circuit breaker): слот мог быть удержан, поэтому его нужно
                // освободить
                compensate(bookingId, roomId, draft, CancellationReason.HOTEL_UNAVAILABLE);
                throw new ServiceUnavailableException(
                        BookingErrorCodes.HOTEL_UNAVAILABLE,
                        "Hotel Service is unavailable, the booking was cancelled",
                        context(bookingId),
                        failure);
            }
        }
        if (draft.autoSelect()) {
            // всех кандидатов заняли параллельные запросы (или номера вывели из эксплуатации) уже после выдачи
            // рекомендаций
            throw noRoomsAvailable(bookingId);
        }
        return rejectAll(bookingId, lastRejection);
    }

    private Booking confirm(long bookingId, long roomId, BookingDraft draft, HotelReservation reservation) {
        boolean confirmed;
        try {
            confirmed = stateService.confirm(bookingId, reservation.hotelId());
        } catch (RuntimeException localFailure) {
            log.error("Local confirmation failed, compensating", localFailure);
            compensate(bookingId, roomId, draft, CancellationReason.CONFIRMATION_FAILED);
            throw localFailure;
        }
        if (!confirmed) {
            // фоновая задача восстановления тем временем отменила бронь; слот она тоже освободила (или освободит)
            compensationService.release(bookingId, roomId, draft.startDate(), draft.endDate());
            throw notConfirmed(bookingId);
        }
        return stateService.get(bookingId);
    }

    private static ConflictException notConfirmed(long bookingId) {
        return new ConflictException(
                BookingErrorCodes.BOOKING_NOT_CONFIRMED,
                "The booking expired before it could be confirmed",
                context(bookingId));
    }

    /** Ничего не удержано: бронь отменяется без компенсации. */
    private ConflictException noRoomsAvailable(long bookingId) {
        stateService.cancel(bookingId, BookingStatus.PENDING, CancellationReason.NO_ROOMS_AVAILABLE, false);
        return new ConflictException(
                BookingErrorCodes.NO_ROOMS_AVAILABLE, "No free rooms for the requested dates", context(bookingId));
    }

    private void compensate(long bookingId, long roomId, BookingDraft draft, CancellationReason reason) {
        if (stateService.cancel(bookingId, BookingStatus.PENDING, reason, true)) {
            compensationService.release(bookingId, roomId, draft.startDate(), draft.endDate());
        }
    }

    private Booking rejectAll(long bookingId, SlotRejectedException lastRejection) {
        boolean notFound = lastRejection.isNotFound();
        stateService.cancel(
                bookingId,
                BookingStatus.PENDING,
                notFound ? CancellationReason.ROOM_NOT_FOUND : CancellationReason.ROOM_UNAVAILABLE,
                false);
        throw new ApiException(
                notFound ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT,
                lastRejection.getCode(),
                lastRejection.getMessage(),
                context(bookingId),
                lastRejection);
    }

    /**
     * Явно указанный номер или наименее загруженные свободные номера (times_booked по возрастанию, затем id) для
     * автоподбора.
     */
    private List<Long> candidates(long bookingId, BookingDraft draft) {
        if (!draft.autoSelect()) {
            return List.of(draft.roomId());
        }
        try {
            List<Long> rooms = hotelClient.recommend(draft.hotelId(), draft.startDate(), draft.endDate()).stream()
                    .limit(properties.maxCandidates())
                    .map(HotelRoom::id)
                    .toList();
            log.info("AUTO_SELECT: candidates={}", rooms);
            return rooms;
        } catch (HotelCallException failure) {
            // ещё ничего не удержано: отмена без компенсации
            stateService.cancel(bookingId, BookingStatus.PENDING, CancellationReason.HOTEL_UNAVAILABLE, false);
            throw new ServiceUnavailableException(
                    BookingErrorCodes.HOTEL_UNAVAILABLE,
                    "Hotel Service is unavailable, the booking was cancelled",
                    context(bookingId),
                    failure);
        }
    }

    private SagaResult replay(Booking booking, BookingDraft draft) {
        if (!draft.isRepetitionOf(booking)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    BookingErrorCodes.IDEMPOTENCY_KEY_REUSED,
                    "requestId %s was already used for a booking with other parameters".formatted(draft.requestId()),
                    context(booking.getId()),
                    null);
        }
        log.info(
                "Duplicate booking request {}: returning booking {} ({})",
                booking.getRequestId(),
                booking.getId(),
                booking.getStatus());
        return new SagaResult(booking, false);
    }

    private static Map<String, Object> context(long bookingId) {
        return Map.of("bookingId", bookingId);
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    /**
     * @param created {@code false}, если запрос был повтором и возвращена существующая бронь
     */
    public record SagaResult(Booking booking, boolean created) {}
}
