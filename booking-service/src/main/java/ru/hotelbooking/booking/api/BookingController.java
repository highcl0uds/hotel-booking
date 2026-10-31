package ru.hotelbooking.booking.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.hotelbooking.booking.api.dto.BookingResponse;
import ru.hotelbooking.booking.api.dto.CreateBookingRequest;
import ru.hotelbooking.booking.domain.BookingStatus;
import ru.hotelbooking.booking.service.BookingService;
import ru.hotelbooking.booking.service.BookingService.BookingCreation;
import ru.hotelbooking.common.security.AuthenticatedUser;
import ru.hotelbooking.common.security.Authorities;
import ru.hotelbooking.common.security.CurrentUser;
import ru.hotelbooking.common.web.Locations;
import ru.hotelbooking.common.web.PageResponse;

@Tag(name = "Bookings")
@RestController
@RequiredArgsConstructor
@PreAuthorize(Authorities.USER)
public class BookingController {

    private final BookingService bookingService;

    @Operation(
            summary = "Create a booking (explicit room or autoSelect)",
            description = "201 CONFIRMED; 200 with the existing booking when the requestId was already used; "
                    + "422 when that requestId was used with other parameters; 409 when the room is taken; "
                    + "503 when Hotel Service is unavailable (the booking is cancelled).")
    @PostMapping("/booking")
    public ResponseEntity<BookingResponse> create(
            @CurrentUser AuthenticatedUser user, @RequestBody @Valid CreateBookingRequest request) {
        BookingCreation creation = bookingService.create(user, request);
        if (!creation.created()) {
            return ResponseEntity.ok(creation.booking());
        }
        return ResponseEntity.created(Locations.ofCreated(creation.booking().id()))
                .body(creation.booking());
    }

    @Operation(summary = "Booking history of the current user")
    @GetMapping("/bookings")
    public PageResponse<BookingResponse> history(
            @CurrentUser AuthenticatedUser user,
            @Parameter(description = "Only bookings in this status") @RequestParam(required = false)
                    BookingStatus status,
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return bookingService.history(user, status, pageable);
    }

    @Operation(summary = "Get a booking with its status history (owner or ADMIN)")
    @GetMapping("/booking/{id}")
    public BookingResponse get(@CurrentUser AuthenticatedUser user, @PathVariable long id) {
        return bookingService.get(user, id);
    }

    @Operation(summary = "Cancel a booking and release the room (owner or ADMIN); idempotent")
    @DeleteMapping("/booking/{id}")
    public ResponseEntity<Void> cancel(@CurrentUser AuthenticatedUser user, @PathVariable long id) {
        bookingService.cancel(user, id);
        return ResponseEntity.noContent().build();
    }
}
