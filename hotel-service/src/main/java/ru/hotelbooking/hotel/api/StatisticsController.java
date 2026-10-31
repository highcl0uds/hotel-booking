package ru.hotelbooking.hotel.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.hotelbooking.common.security.Authorities;
import ru.hotelbooking.common.web.PageResponse;
import ru.hotelbooking.hotel.api.dto.HotelLoadResponse;
import ru.hotelbooking.hotel.api.dto.OccupancyQuery;
import ru.hotelbooking.hotel.api.dto.OccupancyReport;
import ru.hotelbooking.hotel.api.dto.RoomStatsResponse;
import ru.hotelbooking.hotel.service.StatisticsService;

@Tag(name = "Statistics (ADMIN)")
@RestController
@RequiredArgsConstructor
@PreAuthorize(Authorities.ADMIN)
public class StatisticsController {

    private final StatisticsService statisticsService;

    @Operation(summary = "Booking counters of rooms (times_booked) with upcoming reservations")
    @GetMapping("/api/rooms/stats")
    public PageResponse<RoomStatsResponse> roomStats(
            @Parameter(description = "Only rooms of this hotel") @RequestParam(required = false) Long hotelId,
            @ParameterObject @PageableDefault(sort = "timesBooked", direction = Sort.Direction.DESC, size = 50)
                    Pageable pageable) {
        return statisticsService.roomStats(hotelId, pageable);
    }

    @Operation(summary = "Load distribution per hotel: min/max/avg times_booked, spread, idle rooms")
    @GetMapping("/api/hotels/stats")
    public List<HotelLoadResponse> hotelLoad() {
        return statisticsService.hotelLoad();
    }

    @Operation(summary = "Occupancy of a hotel for a period: per room, per day, idle rooms")
    @GetMapping("/api/hotels/{id}/occupancy")
    public OccupancyReport occupancy(@PathVariable long id, @ParameterObject @Valid OccupancyQuery query) {
        return statisticsService.occupancy(id, query);
    }
}
