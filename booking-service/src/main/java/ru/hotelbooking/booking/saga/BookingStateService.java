package ru.hotelbooking.booking.saga;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.hotelbooking.booking.domain.Booking;
import ru.hotelbooking.booking.domain.BookingStatus;
import ru.hotelbooking.booking.domain.BookingStatusChange;
import ru.hotelbooking.booking.domain.CancellationReason;
import ru.hotelbooking.booking.domain.User;
import ru.hotelbooking.booking.repository.BookingRepository;
import ru.hotelbooking.booking.repository.BookingStatusChangeRepository;
import ru.hotelbooking.booking.repository.UserRepository;
import ru.hotelbooking.booking.service.BookingErrorCodes;
import ru.hotelbooking.common.error.ApiException;

/**
 * Локальные транзакции саги бронирования. Каждый метод — одна короткая транзакция; отсюда нет обращений к другим
 * сервисам. Статус меняется условными обновлениями compare-and-set ({@code ... WHERE status = :expected}), поэтому
 * сага, отмена и фоновая задача восстановления никогда не перезапишут результат друг друга.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingStateService {

    private final BookingRepository bookingRepository;
    private final BookingStatusChangeRepository historyRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<Booking> findByRequest(long userId, UUID requestId) {
        return bookingRepository.findByUserIdAndRequestId(userId, requestId);
    }

    @Transactional(readOnly = true)
    public Booking get(long bookingId) {
        return bookingRepository.findById(bookingId).orElseThrow();
    }

    /** Шаг 1 саги: бронь сохраняется в PENDING и фиксируется в базе до вызова Hotel Service. */
    @Transactional
    public Booking createPending(BookingDraft draft) {
        User user = userRepository
                .findById(draft.userId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, BookingErrorCodes.UNKNOWN_USER, "The user no longer exists"));
        Booking booking = bookingRepository.saveAndFlush(Booking.pending(
                user, draft.requestId(), draft.roomId(), draft.autoSelect(), draft.startDate(), draft.endDate()));
        historyRepository.save(new BookingStatusChange(booking, null, BookingStatus.PENDING, null, clock.instant()));
        log.info("Booking {} status -> PENDING (requestId={})", booking.getId(), draft.requestId());
        return booking;
    }

    @Transactional
    public boolean assignRoom(long bookingId, long roomId) {
        return bookingRepository.assignRoom(bookingId, roomId, clock.instant()) == 1;
    }

    /** PENDING → CONFIRMED; {@code false}, если бронь тем временем вышла из PENDING. */
    @Transactional
    public boolean confirm(long bookingId, Long hotelId) {
        if (bookingRepository.confirm(bookingId, hotelId, clock.instant()) == 0) {
            log.warn("Booking {} could not be confirmed: it is no longer PENDING", bookingId);
            return false;
        }
        record(bookingId, BookingStatus.PENDING, BookingStatus.CONFIRMED, null);
        return true;
    }

    /**
     * {@code from} → CANCELLED.
     *
     * @param compensationPending {@code true}, если слот может удерживаться в Hotel Service и его нужно освободить
     */
    @Transactional
    public boolean cancel(long bookingId, BookingStatus from, CancellationReason reason, boolean compensationPending) {
        if (!from.canTransitionTo(BookingStatus.CANCELLED)) {
            throw new IllegalStateException("Transition %s -> CANCELLED is not allowed".formatted(from));
        }
        int updated = bookingRepository.transition(
                bookingId, from, BookingStatus.CANCELLED, reason, compensationPending, clock.instant());
        if (updated == 0) {
            log.info("Booking {} was not cancelled: it is no longer {}", bookingId, from);
            return false;
        }
        record(bookingId, from, BookingStatus.CANCELLED, reason);
        return true;
    }

    @Transactional
    public void markCompensated(long bookingId) {
        bookingRepository.markCompensated(bookingId, clock.instant());
    }

    @Transactional(readOnly = true)
    public List<Booking> findStalePending(Instant createdBefore, int limit) {
        return bookingRepository.findByStatusAndCreatedAtBeforeOrderById(
                BookingStatus.PENDING, createdBefore, Limit.of(limit));
    }

    @Transactional(readOnly = true)
    public List<Booking> findPendingCompensations(int limit) {
        return bookingRepository.findByCompensationPendingTrueOrderById(Limit.of(limit));
    }

    private void record(long bookingId, BookingStatus from, BookingStatus to, CancellationReason reason) {
        historyRepository.save(new BookingStatusChange(
                bookingRepository.getReferenceById(bookingId), from, to, reason, clock.instant()));
        if (reason != null) {
            log.info("Booking {} status {} -> {} (reason={})", bookingId, from, to, reason);
        } else {
            log.info("Booking {} status {} -> {}", bookingId, from, to);
        }
    }
}
