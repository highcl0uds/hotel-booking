package ru.hotelbooking.booking.service;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hotelbooking.booking.api.dto.BookingResponse;
import ru.hotelbooking.booking.api.dto.CreateBookingRequest;
import ru.hotelbooking.booking.domain.Booking;
import ru.hotelbooking.booking.domain.BookingStatus;
import ru.hotelbooking.booking.domain.CancellationReason;
import ru.hotelbooking.booking.mapper.BookingMapper;
import ru.hotelbooking.booking.repository.BookingRepository;
import ru.hotelbooking.booking.repository.BookingStatusChangeRepository;
import ru.hotelbooking.booking.saga.BookingSaga;
import ru.hotelbooking.booking.saga.BookingStateService;
import ru.hotelbooking.booking.saga.CompensationService;
import ru.hotelbooking.common.error.ConflictException;
import ru.hotelbooking.common.error.NotFoundException;
import ru.hotelbooking.common.security.AuthenticatedUser;
import ru.hotelbooking.common.web.CorrelationHeaders;
import ru.hotelbooking.common.web.PageResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingSaga saga;
    private final BookingStateService stateService;
    private final CompensationService compensationService;
    private final BookingRepository bookingRepository;
    private final BookingStatusChangeRepository historyRepository;
    private final BookingMapper bookingMapper;
    private final Clock clock;

    @Override
    public BookingCreation create(AuthenticatedUser user, CreateBookingRequest request) {
        BookingSaga.SagaResult result = saga.execute(bookingMapper.toDraft(user.id(), request));
        return new BookingCreation(bookingMapper.toResponse(result.booking()), result.created());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> history(AuthenticatedUser user, BookingStatus status, Pageable pageable) {
        Page<Booking> page = status == null
                ? bookingRepository.findByUserId(user.id(), pageable)
                : bookingRepository.findByUserIdAndStatus(user.id(), status, pageable);
        return PageResponse.of(page, bookingMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse get(AuthenticatedUser user, long bookingId) {
        Booking booking = findAccessible(user, bookingId);
        return bookingMapper.toResponse(booking, historyRepository.findByBookingIdOrderByChangedAtAscIdAsc(bookingId));
    }

    @Override
    public void cancel(AuthenticatedUser user, long bookingId) {
        Booking booking = findAccessible(user, bookingId);
        try (var ignored = MDC.putCloseable(CorrelationHeaders.MDC_BOOKING_ID, String.valueOf(bookingId))) {
            switch (booking.getStatus()) {
                case CANCELLED -> log.info("Booking already cancelled, nothing to do");
                case PENDING ->
                    throw new ConflictException(
                            BookingErrorCodes.BOOKING_IN_PROGRESS, "The booking is still being processed");
                case CONFIRMED -> cancelConfirmed(booking);
            }
        }
    }

    /**
     * Сначала бронь отменяется локально (с ожидающей компенсацией), затем освобождается слот. Если Hotel Service
     * недоступен, пользователь всё равно получает отменённую бронь, а освобождение позже доставит фоновая задача
     * восстановления.
     */
    private void cancelConfirmed(Booking booking) {
        if (!booking.getEndDate().isAfter(LocalDate.now(clock))) {
            throw new ConflictException(BookingErrorCodes.BOOKING_COMPLETED, "A completed stay cannot be cancelled");
        }
        if (stateService.cancel(booking.getId(), BookingStatus.CONFIRMED, CancellationReason.CANCELLED_BY_USER, true)) {
            compensationService.release(
                    booking.getId(), booking.getRoomId(), booking.getStartDate(), booking.getEndDate());
        }
    }

    private Booking findAccessible(AuthenticatedUser user, long bookingId) {
        Booking booking =
                bookingRepository.findById(bookingId).orElseThrow(() -> NotFoundException.of("booking", bookingId));
        if (!user.isAdmin() && !booking.isOwnedBy(user.id())) {
            log.warn("User {} tried to access booking {} of another user", user.id(), bookingId);
            throw new AccessDeniedException("Booking %d belongs to another user".formatted(bookingId));
        }
        return booking;
    }
}
