package ru.hotelbooking.booking.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ru.hotelbooking.booking.domain.BookingStatus;
import ru.hotelbooking.booking.domain.CancellationReason;

/**
 * Бронь; {@code history} (переходы статусов) включается, когда запрашивается одна бронь.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingResponse(
        Long id,
        Long userId,
        UUID requestId,
        Long roomId,
        Long hotelId,
        boolean autoSelect,
        LocalDate startDate,
        LocalDate endDate,
        BookingStatus status,
        CancellationReason cancelReason,
        Instant createdAt,
        Instant updatedAt,
        List<StatusChangeResponse> history) {

    public record StatusChangeResponse(
            BookingStatus from, BookingStatus to, CancellationReason reason, Instant changedAt) {}
}
