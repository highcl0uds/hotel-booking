package ru.hotelbooking.hotel.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hotelbooking.common.error.NotFoundException;
import ru.hotelbooking.common.web.PageResponse;
import ru.hotelbooking.hotel.api.dto.HotelLoadResponse;
import ru.hotelbooking.hotel.api.dto.OccupancyQuery;
import ru.hotelbooking.hotel.api.dto.OccupancyReport;
import ru.hotelbooking.hotel.api.dto.RoomStatsResponse;
import ru.hotelbooking.hotel.domain.ReservationStatus;
import ru.hotelbooking.hotel.domain.Room;
import ru.hotelbooking.hotel.mapper.HotelMapper;
import ru.hotelbooking.hotel.mapper.RoomMapper;
import ru.hotelbooking.hotel.repository.HotelRepository;
import ru.hotelbooking.hotel.repository.RoomRepository;
import ru.hotelbooking.hotel.repository.RoomReservationCount;
import ru.hotelbooking.hotel.repository.RoomReservationRepository;
import ru.hotelbooking.hotel.repository.RoomSpecifications;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsServiceImpl implements StatisticsService {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final RoomReservationRepository reservationRepository;
    private final OccupancyCalculator occupancyCalculator;
    private final HotelMapper hotelMapper;
    private final RoomMapper roomMapper;
    private final Clock clock;

    @Override
    public PageResponse<RoomStatsResponse> roomStats(Long hotelId, Pageable pageable) {
        Specification<Room> spec = hotelId != null ? RoomSpecifications.inHotel(hotelId) : Specification.allOf();
        Page<Room> rooms = roomRepository.findAll(spec, pageable);
        Map<Long, Long> upcoming = upcomingReservations(rooms.map(Room::getId).getContent());
        return PageResponse.of(
                rooms, room -> roomMapper.toStatsResponse(room, upcoming.getOrDefault(room.getId(), 0L)));
    }

    @Override
    public List<HotelLoadResponse> hotelLoad() {
        return hotelRepository.summarizeLoad().stream()
                .map(hotelMapper::toLoadResponse)
                .toList();
    }

    @Override
    public OccupancyReport occupancy(long hotelId, OccupancyQuery query) {
        if (!hotelRepository.existsById(hotelId)) {
            throw NotFoundException.of("hotel", hotelId);
        }
        List<Room> rooms = roomRepository.findByHotelIdOrderById(hotelId);
        var reservations =
                reservationRepository.findOverlappingInHotel(hotelId, ReservationStatus.HELD, query.from(), query.to());
        return occupancyCalculator.calculate(hotelId, query.from(), query.to(), rooms, reservations);
    }

    private Map<Long, Long> upcomingReservations(List<Long> roomIds) {
        if (roomIds.isEmpty()) {
            return Map.of();
        }
        return reservationRepository
                .countByRoomEndingAfter(roomIds, ReservationStatus.HELD, LocalDate.now(clock))
                .stream()
                .collect(Collectors.toMap(RoomReservationCount::getRoomId, RoomReservationCount::getReservations));
    }
}
