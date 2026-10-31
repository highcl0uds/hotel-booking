package ru.hotelbooking.hotel.api.dto;

import ru.hotelbooking.hotel.domain.RoomType;

/**
 * Статистика загрузки номера.
 *
 * @param timesBooked число успешных бронирований (входные данные алгоритма рекомендаций)
 * @param upcomingReservations резервы HELD, которые ещё не закончились
 */
public record RoomStatsResponse(
        Long roomId,
        Long hotelId,
        String number,
        RoomType type,
        boolean available,
        long timesBooked,
        long upcomingReservations) {}
