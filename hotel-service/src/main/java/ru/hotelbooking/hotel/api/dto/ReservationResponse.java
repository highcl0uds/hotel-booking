package ru.hotelbooking.hotel.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import ru.hotelbooking.hotel.domain.ReservationStatus;

public record ReservationResponse(
        Long id,
        Long roomId,
        Long hotelId,
        Long bookingId,
        String requestId,
        LocalDate startDate,
        LocalDate endDate,
        ReservationStatus status,
        Instant createdAt,
        Instant releasedAt) {}
