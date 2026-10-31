package ru.hotelbooking.hotel.api.dto;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.LocalDate;
import ru.hotelbooking.common.validation.DateRangeAware;
import ru.hotelbooking.common.validation.ValidDateRange;
import ru.hotelbooking.hotel.domain.RoomType;

/**
 * Параметры поиска номеров. Если заданы {@code startDate}/{@code endDate}, исключаются номера с резервами HELD,
 * пересекающими {@code [startDate, endDate)}.
 */
@ValidDateRange(maxDays = 365)
public record RoomSearchCriteria(
        @Parameter(description = "Only rooms of this hotel") Long hotelId,

        @Parameter(description = "Only hotels in this city, case-insensitive")
        String city,

        @Parameter(description = "Room type") RoomType type,

        @Parameter(description = "Minimal number of guests") @Min(1)
        Integer minCapacity,

        @Parameter(description = "Maximal price per night") @DecimalMin("0")
        BigDecimal maxPrice,

        @Parameter(description = "Check-in date, ISO format", example = "2030-01-10")
        LocalDate startDate,

        @Parameter(description = "Check-out date (exclusive), ISO format", example = "2030-01-12")
        LocalDate endDate)
        implements DateRangeAware {

    @AssertTrue(message = "startDate and endDate must be provided together")
    @Parameter(hidden = true)
    public boolean isPeriodComplete() {
        return (startDate == null) == (endDate == null);
    }
}
