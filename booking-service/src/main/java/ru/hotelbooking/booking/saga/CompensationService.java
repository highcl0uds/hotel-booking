package ru.hotelbooking.booking.saga;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.hotelbooking.booking.client.HotelCallException;
import ru.hotelbooking.booking.client.HotelClient;
import ru.hotelbooking.booking.client.SlotRejectedException;
import ru.hotelbooking.booking.client.dto.SlotRequest;

/**
 * Компенсирующее действие саги: освобождает слот в Hotel Service. Если Hotel Service недоступен, у брони остаётся
 * {@code compensation_pending = true}, и освобождение позже доставит фоновая задача восстановления.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompensationService {

    private final HotelClient hotelClient;
    private final BookingStateService stateService;

    /** @return {@code true}, если Hotel Service подтвердил освобождение */
    public boolean release(long bookingId, long roomId, LocalDate startDate, LocalDate endDate) {
        SlotRequest slot = SlotRequest.of(bookingId, roomId, startDate, endDate);
        log.info("COMPENSATION_STARTED: releasing room={} requestId={}", roomId, slot.requestId());
        try {
            hotelClient.release(roomId, slot);
            stateService.markCompensated(bookingId);
            log.info("COMPENSATION_DONE: room={} released", roomId);
            return true;
        } catch (SlotRejectedException e) {
            // окончательный ответ, например номера больше нет: ничего не удерживается, и повторы никогда не увенчаются
            // успехом
            stateService.markCompensated(bookingId);
            log.warn("COMPENSATION_SKIPPED: room={} has nothing to release ({})", roomId, e.getCode());
            return true;
        } catch (HotelCallException e) {
            log.warn(
                    "COMPENSATION_DEFERRED: room={} not released yet ({}), the recovery job will retry",
                    roomId,
                    e.getMessage());
            return false;
        }
    }
}
