package ru.hotelbooking.hotel.api.dto;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import ru.hotelbooking.common.validation.DateRangeAware;
import ru.hotelbooking.common.validation.ValidDateRange;

/**
 * Отчётный период {@code [from, to)}.
 */
@ValidDateRange(maxDays = 366, endProperty = "to", message = "to must be after from")
public record OccupancyQuery(
        @Parameter(description = "First day of the period, ISO format", example = "2030-01-01") @NotNull
        LocalDate from,

        @Parameter(description = "Day after the last day of the period, ISO format", example = "2030-02-01") @NotNull
        LocalDate to)
        implements DateRangeAware {

    @Override
    public LocalDate startDate() {
        return from;
    }

    @Override
    public LocalDate endDate() {
        return to;
    }
}
