package ru.hotelbooking.hotel.api.dto;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record HotelSearchCriteria(
        @Parameter(description = "Exact city, case-insensitive")
        String city,

        @Parameter(description = "Part of the hotel name, case-insensitive")
        String name,

        @Parameter(description = "Minimal number of stars") @Min(1) @Max(5)
        Integer minStars) {}
