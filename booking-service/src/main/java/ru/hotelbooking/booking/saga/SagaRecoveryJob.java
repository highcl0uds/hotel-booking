package ru.hotelbooking.booking.saga;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.hotelbooking.booking.config.SagaProperties;
import ru.hotelbooking.booking.domain.Booking;
import ru.hotelbooking.booking.domain.BookingStatus;
import ru.hotelbooking.booking.domain.CancellationReason;
import ru.hotelbooking.common.web.CorrelationHeaders;

/**
 * Доводит прерванные саги до конечного состояния:
 * <ul>
 *   <li>брони, зависшие в PENDING (например, сервис перезапустили посреди саги), отменяются с компенсацией;
 *   <li>освобождения, которые не удалось доставить в Hotel Service, отправляются повторно.
 * </ul>
 * Выполняется в единственном потоке планировщика с фиксированной паузой, поэтому запуски никогда не пересекаются.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaRecoveryJob {

    private final BookingStateService stateService;
    private final CompensationService compensationService;
    private final SagaProperties properties;
    private final Clock clock;

    @Scheduled(
            fixedDelayString = "${app.saga.recovery-interval:PT30S}",
            initialDelayString = "${app.saga.recovery-interval:PT30S}")
    public void recover() {
        cancelStalePending();
        retryPendingCompensations();
    }

    void cancelStalePending() {
        Instant threshold = clock.instant().minus(properties.pendingTimeout());
        for (Booking booking : stateService.findStalePending(threshold, properties.recoveryBatchSize())) {
            withBookingId(booking, () -> {
                boolean slotMayBeHeld = booking.getRoomId() != null;
                if (stateService.cancel(
                                booking.getId(), BookingStatus.PENDING, CancellationReason.SAGA_TIMEOUT, slotMayBeHeld)
                        && slotMayBeHeld) {
                    log.warn("RECOVERY: booking stuck in PENDING since {}, compensating", booking.getCreatedAt());
                    release(booking);
                }
            });
        }
    }

    void retryPendingCompensations() {
        for (Booking booking : stateService.findPendingCompensations(properties.recoveryBatchSize())) {
            withBookingId(booking, () -> {
                log.info("RECOVERY: retrying release of room={}", booking.getRoomId());
                release(booking);
            });
        }
    }

    private void release(Booking booking) {
        compensationService.release(booking.getId(), booking.getRoomId(), booking.getStartDate(), booking.getEndDate());
    }

    private static void withBookingId(Booking booking, Runnable action) {
        try (var ignored = MDC.putCloseable(CorrelationHeaders.MDC_BOOKING_ID, String.valueOf(booking.getId()))) {
            action.run();
        }
    }
}
