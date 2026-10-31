package ru.hotelbooking.hotel.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hotelbooking.common.security.Authorities;
import ru.hotelbooking.hotel.api.dto.ReservationResponse;
import ru.hotelbooking.hotel.api.dto.SlotRequest;
import ru.hotelbooking.hotel.mapper.ReservationMapper;
import ru.hotelbooking.hotel.service.RoomAvailabilityService;

/**
 * Внутренние шаги саги бронирования; их вызывает только Booking Service со служебным токеном (роль INTERNAL).
 * Gateway эти маршруты не публикует.
 */
@Tag(name = "Room availability (INTERNAL)")
@RestController
@RequestMapping("/api/rooms/{id}")
@RequiredArgsConstructor
public class RoomAvailabilityController {

    private final RoomAvailabilityService availabilityService;
    private final ReservationMapper reservationMapper;

    @Operation(
            summary = "Confirm availability and hold the slot",
            description =
                    "Idempotent by requestId. 409 when the room is booked for overlapping dates or out of service.")
    @PostMapping("/confirm-availability")
    @PreAuthorize(Authorities.INTERNAL)
    public ReservationResponse confirmAvailability(@PathVariable long id, @RequestBody @Valid SlotRequest request) {
        return availabilityService.confirmAvailability(id, reservationMapper.toCommand(request));
    }

    @Operation(
            summary = "Compensation: release the slot",
            description = "Idempotent by requestId; releasing an unknown requestId blocks its late hold.")
    @PostMapping("/release")
    @PreAuthorize(Authorities.INTERNAL)
    public ReservationResponse release(@PathVariable long id, @RequestBody @Valid SlotRequest request) {
        return availabilityService.release(id, reservationMapper.toCommand(request));
    }
}
