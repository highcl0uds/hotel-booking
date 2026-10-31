package ru.hotelbooking.hotel.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import ru.hotelbooking.hotel.domain.RoomType;

public record RoomRequest(
        @Schema(example = "1") @NotNull Long hotelId,
        @Schema(example = "305") @NotBlank @Size(max = 20) String number,
        @Schema(example = "STANDARD") @NotNull RoomType type,
        @Schema(example = "2") @NotNull @Min(1) @Max(10) Integer capacity,

        @Schema(example = "4500.00") @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2)
        BigDecimal pricePerNight,

        @Schema(description = "Operational availability; true when omitted")
        Boolean available) {

    public boolean availableOrDefault() {
        return available == null || available;
    }
}
