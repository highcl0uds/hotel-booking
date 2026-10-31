package ru.hotelbooking.hotel.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import ru.hotelbooking.common.validation.DateRangeAware;
import ru.hotelbooking.common.validation.ValidDateRange;

/**
 * Тело внутренних вызовов {@code confirm-availability} и {@code release} от Booking Service.
 *
 * @param requestId ключ идемпотентности; одно и то же значение используется для всех повторов и для компенсирующего
 *     освобождения
 * @param bookingId бронь, которой принадлежит слот (id корреляции саги)
 */
@ValidDateRange(maxDays = 365)
public record SlotRequest(
        @NotBlank @Size(max = 100) String requestId,
        @NotNull Long bookingId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate)
        implements DateRangeAware {}
