package ru.hotelbooking.booking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AuthRequest(
        @Schema(example = "alice") @NotBlank String username,
        @Schema(example = "alice123") @NotBlank String password) {}
