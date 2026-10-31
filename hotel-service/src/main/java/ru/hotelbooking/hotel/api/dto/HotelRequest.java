package ru.hotelbooking.hotel.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HotelRequest(
        @Schema(example = "Volga Riverside") @NotBlank @Size(max = 150)
        String name,

        @Schema(example = "Kazan") @NotBlank @Size(max = 100)
        String city,

        @Schema(example = "Kremlevskaya st., 1") @NotBlank @Size(max = 255)
        String address,

        @Schema(example = "4") @Min(1) @Max(5) Integer stars) {}
