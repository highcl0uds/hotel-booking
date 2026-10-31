package ru.hotelbooking.hotel.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hotelbooking.common.error.ConflictException;
import ru.hotelbooking.common.error.NotFoundException;
import ru.hotelbooking.common.web.PageResponse;
import ru.hotelbooking.hotel.api.dto.HotelRequest;
import ru.hotelbooking.hotel.api.dto.HotelResponse;
import ru.hotelbooking.hotel.api.dto.HotelSearchCriteria;
import ru.hotelbooking.hotel.domain.Hotel;
import ru.hotelbooking.hotel.domain.ReservationStatus;
import ru.hotelbooking.hotel.domain.Room;
import ru.hotelbooking.hotel.mapper.HotelMapper;
import ru.hotelbooking.hotel.repository.HotelRepository;
import ru.hotelbooking.hotel.repository.HotelSpecifications;
import ru.hotelbooking.hotel.repository.RoomRepository;
import ru.hotelbooking.hotel.repository.RoomReservationRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotelServiceImpl implements HotelService {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final RoomReservationRepository reservationRepository;
    private final HotelMapper hotelMapper;
    private final Clock clock;

    @Override
    @Transactional
    public HotelResponse create(HotelRequest request) {
        Hotel hotel = hotelRepository.save(hotelMapper.toEntity(request));
        log.info("Hotel created: id={}, name={}", hotel.getId(), hotel.getName());
        return hotelMapper.toResponse(hotel);
    }

    @Override
    @Transactional(readOnly = true)
    public HotelResponse get(long id) {
        return hotelMapper.toResponse(findHotel(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<HotelResponse> search(HotelSearchCriteria criteria, Pageable pageable) {
        var spec = HotelSpecifications.matching(criteria.city(), criteria.name(), criteria.minStars());
        return PageResponse.of(hotelRepository.findAll(spec, pageable), hotelMapper::toResponse);
    }

    @Override
    @Transactional
    public HotelResponse update(long id, HotelRequest request) {
        Hotel hotel = findHotel(id);
        hotelMapper.update(hotel, request);
        log.info("Hotel updated: id={}", id);
        return hotelMapper.toResponse(hotel);
    }

    @Override
    @Transactional
    public void delete(long id) {
        Hotel hotel = findHotel(id);
        // те же блокировки строк, что и в confirm-availability: параллельное удержание либо завершится раньше и будет
        // замечено проверкой ниже, либо дождётся удаления и обнаружит, что номера уже нет
        List<Room> rooms = roomRepository.lockByHotelId(id);
        if (reservationRepository.existsByRoomHotelIdAndStatusAndEndDateAfter(
                id, ReservationStatus.HELD, LocalDate.now(clock))) {
            throw new ConflictException(
                    HotelErrorCodes.HOTEL_HAS_ACTIVE_RESERVATIONS,
                    "Hotel %d has rooms with upcoming reservations".formatted(id));
        }
        // историю резервов номеров удаляет ON DELETE CASCADE
        roomRepository.deleteAll(rooms);
        hotelRepository.delete(hotel);
        log.info("Hotel deleted: id={}", id);
    }

    private Hotel findHotel(long id) {
        return hotelRepository.findById(id).orElseThrow(() -> NotFoundException.of("hotel", id));
    }
}
