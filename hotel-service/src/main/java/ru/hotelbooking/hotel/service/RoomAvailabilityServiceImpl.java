package ru.hotelbooking.hotel.service;

import java.time.Clock;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hotelbooking.common.error.ConflictException;
import ru.hotelbooking.common.error.NotFoundException;
import ru.hotelbooking.hotel.api.dto.ReservationResponse;
import ru.hotelbooking.hotel.domain.Room;
import ru.hotelbooking.hotel.domain.RoomReservation;
import ru.hotelbooking.hotel.domain.SlotCommand;
import ru.hotelbooking.hotel.mapper.ReservationMapper;
import ru.hotelbooking.hotel.repository.RoomRepository;
import ru.hotelbooking.hotel.repository.RoomReservationRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomAvailabilityServiceImpl implements RoomAvailabilityService {

    private final RoomRepository roomRepository;
    private final RoomReservationRepository reservationRepository;
    private final ReservationMapper reservationMapper;
    private final Clock clock;

    @Override
    @Transactional
    public ReservationResponse confirmAvailability(long roomId, SlotCommand command) {
        // Блокировка строки берётся первой: параллельные запросы к этому номеру (включая дубликаты этого запроса)
        // обрабатываются по очереди, поэтому между проверками ниже не возникнет гонки.
        Room room = lockRoom(roomId);

        var existing = reservationRepository.findByRequestId(command.requestId());
        if (existing.isPresent()) {
            return replayHold(existing.get(), roomId, command);
        }
        if (!room.isAvailable()) {
            throw conflict(HotelErrorCodes.ROOM_OUT_OF_SERVICE, "Room %d is out of service".formatted(roomId), command);
        }
        if (reservationRepository.hasHeldOverlap(roomId, command.startDate(), command.endDate())) {
            log.info(
                    "Hold rejected: room={} is already booked for [{}, {}), bookingId={}",
                    roomId,
                    command.startDate(),
                    command.endDate(),
                    command.bookingId());
            throw conflict(
                    HotelErrorCodes.ROOM_ALREADY_BOOKED,
                    "Room %d is already booked for the requested dates".formatted(roomId),
                    command);
        }

        RoomReservation reservation = reservationRepository.save(RoomReservation.hold(room, command));
        room.registerBooking();
        log.info(
                "Slot HELD: room={}, period=[{}, {}), bookingId={}, requestId={}, timesBooked={}",
                roomId,
                command.startDate(),
                command.endDate(),
                command.bookingId(),
                command.requestId(),
                room.getTimesBooked());
        return reservationMapper.toResponse(reservation);
    }

    @Override
    @Transactional
    public ReservationResponse release(long roomId, SlotCommand command) {
        Room room = lockRoom(roomId);

        var existing = reservationRepository.findByRequestId(command.requestId());
        if (existing.isEmpty()) {
            RoomReservation tombstone =
                    reservationRepository.save(RoomReservation.tombstone(room, command, clock.instant()));
            log.info(
                    "Release before hold: room={}, bookingId={}, requestId={}; tombstone stored, a late hold will be"
                            + " rejected",
                    roomId,
                    command.bookingId(),
                    command.requestId());
            return reservationMapper.toResponse(tombstone);
        }

        RoomReservation reservation = existing.get();
        ensureSameRoom(reservation, roomId, command);
        if (reservation.isHeld()) {
            reservation.release(clock.instant());
            room.unregisterBooking();
            log.info(
                    "Slot RELEASED: room={}, bookingId={}, requestId={}, timesBooked={}",
                    roomId,
                    command.bookingId(),
                    command.requestId(),
                    room.getTimesBooked());
        } else {
            log.info(
                    "Duplicate release ignored: room={}, bookingId={}, requestId={}",
                    roomId,
                    command.bookingId(),
                    command.requestId());
        }
        return reservationMapper.toResponse(reservation);
    }

    private ReservationResponse replayHold(RoomReservation reservation, long roomId, SlotCommand command) {
        ensureSameRoom(reservation, roomId, command);
        if (!reservation.isHeld()) {
            log.info(
                    "Late hold rejected: requestId={} was already released (bookingId={})",
                    command.requestId(),
                    command.bookingId());
            throw conflict(
                    HotelErrorCodes.RESERVATION_RELEASED,
                    "Request %s was already compensated".formatted(command.requestId()),
                    command);
        }
        log.info(
                "Duplicate hold: requestId={} already processed, returning reservation {} (bookingId={})",
                command.requestId(),
                reservation.getId(),
                command.bookingId());
        return reservationMapper.toResponse(reservation);
    }

    private static void ensureSameRoom(RoomReservation reservation, long roomId, SlotCommand command) {
        if (!reservation.belongsTo(roomId)) {
            throw conflict(
                    HotelErrorCodes.REQUEST_ID_CONFLICT,
                    "Request %s belongs to another room".formatted(command.requestId()),
                    command);
        }
    }

    private Room lockRoom(long roomId) {
        return roomRepository.findByIdForUpdate(roomId).orElseThrow(() -> NotFoundException.of("room", roomId));
    }

    private static ConflictException conflict(String code, String message, SlotCommand command) {
        return new ConflictException(
                code, message, Map.of("bookingId", command.bookingId(), "requestId", command.requestId()));
    }
}
