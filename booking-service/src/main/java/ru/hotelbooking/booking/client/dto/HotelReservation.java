package ru.hotelbooking.booking.client.dto;

import java.time.LocalDate;

/**
 * Резерв слота в том виде, в каком его возвращает Hotel Service.
 */
public record HotelReservation(
        Long id,
        Long roomId,
        Long hotelId,
        Long bookingId,
        String requestId,
        LocalDate startDate,
        LocalDate endDate,
        String status) {}
