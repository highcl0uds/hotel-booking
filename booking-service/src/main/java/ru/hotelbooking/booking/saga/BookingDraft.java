package ru.hotelbooking.booking.saga;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import ru.hotelbooking.booking.domain.Booking;

/**
 * Проверенный запрос пользователя на бронирование — входные данные саги.
 */
public record BookingDraft(
        long userId,
        UUID requestId,
        boolean autoSelect,
        Long roomId,
        Long hotelId,
        LocalDate startDate,
        LocalDate endDate) {

    /** Создана ли {@code booking} запросом с теми же параметрами, то есть настоящий ли это повтор. */
    public boolean isRepetitionOf(Booking booking) {
        return autoSelect == booking.isAutoSelect()
                && startDate.equals(booking.getStartDate())
                && endDate.equals(booking.getEndDate())
                && (autoSelect || Objects.equals(roomId, booking.getRoomId()));
    }
}
