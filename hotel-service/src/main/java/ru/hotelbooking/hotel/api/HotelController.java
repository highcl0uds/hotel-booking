package ru.hotelbooking.hotel.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import ru.hotelbooking.hotel.api.dto.HotelRequest;
import ru.hotelbooking.hotel.api.dto.HotelResponse;
import ru.hotelbooking.hotel.api.dto.HotelSearchCriteria;
import ru.hotelbooking.hotel.service.HotelService;

@Tag(name = "Hotels")
@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;

    @Operation(summary = "List hotels with filters, paging and sorting")
    @GetMapping
    @PreAuthorize(Authorities.USER)
    public PageResponse<HotelResponse> search(
            @ParameterObject @Valid HotelSearchCriteria criteria,
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return hotelService.search(criteria, pageable);
    }

    @Operation(summary = "Get a hotel")
    @GetMapping("/{id}")
    @PreAuthorize(Authorities.USER)
    public HotelResponse get(@PathVariable long id) {
        return hotelService.get(id);
    }

    @Operation(summary = "Add a hotel (ADMIN)")
    @PostMapping
    @PreAuthorize(Authorities.ADMIN)
    public ResponseEntity<HotelResponse> create(@RequestBody @Valid HotelRequest request) {
        HotelResponse created = hotelService.create(request);
        return ResponseEntity.created(Locations.ofCreated(created.id())).body(created);
    }

    @Operation(summary = "Update a hotel (ADMIN)")
    @PutMapping("/{id}")
    @PreAuthorize(Authorities.ADMIN)
    public HotelResponse update(@PathVariable long id, @RequestBody @Valid HotelRequest request) {
        return hotelService.update(id, request);
    }

    @Operation(summary = "Delete a hotel with its rooms (ADMIN); refused while rooms have upcoming reservations")
    @DeleteMapping("/{id}")
    @PreAuthorize(Authorities.ADMIN)
    public ResponseEntity<Void> delete(@PathVariable long id) {
        hotelService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
