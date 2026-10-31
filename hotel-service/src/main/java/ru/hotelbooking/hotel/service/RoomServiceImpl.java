package ru.hotelbooking.hotel.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hotelbooking.common.error.ConflictException;
import ru.hotelbooking.common.error.NotFoundException;
import ru.hotelbooking.common.web.PageResponse;
import ru.hotelbooking.hotel.api.dto.RoomRequest;
import ru.hotelbooking.hotel.api.dto.RoomResponse;
import ru.hotelbooking.hotel.api.dto.RoomSearchCriteria;
import ru.hotelbooking.hotel.domain.Hotel;
import ru.hotelbooking.hotel.domain.ReservationStatus;
import ru.hotelbooking.hotel.domain.Room;
import ru.hotelbooking.hotel.mapper.RoomMapper;
import ru.hotelbooking.hotel.repository.HotelRepository;
import ru.hotelbooking.hotel.repository.RoomRepository;
import ru.hotelbooking.hotel.repository.RoomReservationRepository;
import ru.hotelbooking.hotel.repository.RoomSpecifications;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    /** Порядок рекомендаций по ТЗ: сначала наименее бронируемые, при равенстве — по id. */
    static final Sort RECOMMENDATION_ORDER = Sort.by(Sort.Order.asc("timesBooked"), Sort.Order.asc("id"));

    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;
    private final RoomReservationRepository reservationRepository;
    private final RoomMapper roomMapper;
    private final Clock clock;

    @Override
    @Transactional
    public RoomResponse create(RoomRequest request) {
        Hotel hotel = findHotel(request.hotelId());
        if (roomRepository.existsByHotelIdAndNumber(hotel.getId(), request.number())) {
            throw numberTaken(request);
        }
        Room room = new Room();
        roomMapper.update(room, request);
        room.setHotel(hotel);
        Room saved = roomRepository.save(room);
        log.info("Room created: id={}, hotelId={}, number={}", saved.getId(), hotel.getId(), saved.getNumber());
        return roomMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public RoomResponse get(long id) {
        return roomMapper.toResponse(findRoom(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RoomResponse> findBookable(RoomSearchCriteria criteria, Pageable pageable) {
        var spec = RoomSpecifications.bookable(roomMapper.toFilter(criteria));
        return PageResponse.of(roomRepository.findAll(spec, pageable), roomMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> recommend(RoomSearchCriteria criteria) {
        var spec = RoomSpecifications.bookable(roomMapper.toFilter(criteria));
        return roomRepository.findAll(spec, RECOMMENDATION_ORDER).stream()
                .map(roomMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public RoomResponse update(long id, RoomRequest request) {
        Room room = lockRoom(id);
        Hotel hotel = findHotel(request.hotelId());
        if (roomRepository.existsByHotelIdAndNumberAndIdNot(hotel.getId(), request.number(), id)) {
            throw numberTaken(request);
        }
        if (!hotel.getId().equals(room.getHotel().getId()) && hasUpcomingReservations(id)) {
            // подтверждённые брони ссылаются на отель номера; после переноса номера они указывали бы на чужой отель
            throw new ConflictException(
                    HotelErrorCodes.ROOM_HAS_ACTIVE_RESERVATIONS,
                    "Room %d has upcoming reservations and cannot be moved to another hotel".formatted(id));
        }
        roomMapper.update(room, request);
        room.setHotel(hotel);
        log.info("Room updated: id={}, available={}", id, room.isAvailable());
        return roomMapper.toResponse(room);
    }

    @Override
    @Transactional
    public void delete(long id) {
        Room room = lockRoom(id);
        if (hasUpcomingReservations(id)) {
            throw new ConflictException(
                    HotelErrorCodes.ROOM_HAS_ACTIVE_RESERVATIONS,
                    "Room %d has upcoming reservations; mark it unavailable instead".formatted(id));
        }
        roomRepository.delete(room);
        log.info("Room deleted: id={}", id);
    }

    private boolean hasUpcomingReservations(long roomId) {
        return reservationRepository.existsByRoomIdAndStatusAndEndDateAfter(
                roomId, ReservationStatus.HELD, LocalDate.now(clock));
    }

    /**
     * Та же блокировка строки, что и у confirm-availability: параллельное удержание слота либо завершится раньше и будет
     * видно проверке резервов, либо дождётся конца изменения номера.
     */
    private Room lockRoom(long id) {
        return roomRepository.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("room", id));
    }

    private Room findRoom(long id) {
        return roomRepository.findById(id).orElseThrow(() -> NotFoundException.of("room", id));
    }

    private Hotel findHotel(long id) {
        return hotelRepository.findById(id).orElseThrow(() -> NotFoundException.of("hotel", id));
    }

    private static ConflictException numberTaken(RoomRequest request) {
        return new ConflictException(
                HotelErrorCodes.ROOM_NUMBER_TAKEN,
                "Room %s already exists in hotel %d".formatted(request.number(), request.hotelId()));
    }
}
