package ru.hotelbooking.booking.client.dto;

import java.time.LocalDate;

/**
 * Тело вызовов удержания и освобождения слота.
 *
 * @param requestId ключ идемпотентности, общий для удержания, всех его повторов и компенсирующего освобождения
 * @param bookingId id корреляции саги
 */
public record SlotRequest(String requestId, Long bookingId, LocalDate startDate, LocalDate endDate) {

    /** Ключ выводится из брони и номера, поэтому все повторы и освобождение используют одно и то же значение. */
    public static SlotRequest of(long bookingId, long roomId, LocalDate startDate, LocalDate endDate) {
        return new SlotRequest("hold-%d-%d".formatted(bookingId, roomId), bookingId, startDate, endDate);
    }
}
