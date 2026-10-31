package ru.hotelbooking.hotel.repository;

/**
 * Проекция результата {@link RoomReservationRepository#countByRoomEndingAfter}.
 */
public interface RoomReservationCount {

    Long getRoomId();

    Long getReservations();
}
