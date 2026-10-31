package ru.hotelbooking.hotel.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hotelbooking.common.security.Authorities;
import ru.hotelbooking.common.web.Locations;
import ru.hotelbooking.common.web.PageResponse;
import ru.hotelbooking.hotel.api.dto.RoomRequest;
import ru.hotelbooking.hotel.api.dto.RoomResponse;
import ru.hotelbooking.hotel.api.dto.RoomSearchCriteria;
import ru.hotelbooking.hotel.service.RoomService;

@Tag(name = "Rooms")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @Operation(
            summary = "List bookable rooms",
            description = "Operational rooms; with startDate/endDate only those free for [startDate, endDate).")
    @GetMapping
    @PreAuthorize(Authorities.USER)
    public PageResponse<RoomResponse> findBookable(
            @ParameterObject @Valid RoomSearchCriteria criteria,
            @ParameterObject @PageableDefault(sort = "id", size = 50) Pageable pageable) {
        return roomService.findBookable(criteria, pageable);
    }

    @Operation(
            summary = "Recommended rooms",
            description = "The same free rooms ordered by times_booked ascending, then by id (even load distribution).")
    @GetMapping("/recommend")
    @PreAuthorize(Authorities.USER_OR_INTERNAL)
    public List<RoomResponse> recommend(@ParameterObject @Valid RoomSearchCriteria criteria) {
        return roomService.recommend(criteria);
    }

    @Operation(summary = "Get a room")
    @GetMapping("/{id}")
    @PreAuthorize(Authorities.USER)
    public RoomResponse get(@PathVariable long id) {
        return roomService.get(id);
    }

    @Operation(summary = "Add a room to a hotel (ADMIN)")
    @PostMapping
    @PreAuthorize(Authorities.ADMIN)
    public ResponseEntity<RoomResponse> create(@RequestBody @Valid RoomRequest request) {
        RoomResponse created = roomService.create(request);
        return ResponseEntity.created(Locations.ofCreated(created.id())).body(created);
    }

    @Operation(summary = "Update a room, e.g. withdraw it from service (ADMIN)")
    @PutMapping("/{id}")
    @PreAuthorize(Authorities.ADMIN)
    public RoomResponse update(@PathVariable long id, @RequestBody @Valid RoomRequest request) {
        return roomService.update(id, request);
    }

    @Operation(summary = "Delete a room (ADMIN); refused while it has upcoming reservations")
    @DeleteMapping("/{id}")
    @PreAuthorize(Authorities.ADMIN)
    public ResponseEntity<Void> delete(@PathVariable long id) {
        roomService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
