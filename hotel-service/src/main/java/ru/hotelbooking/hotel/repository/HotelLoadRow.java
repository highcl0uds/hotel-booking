package ru.hotelbooking.hotel.repository;

/**
 * Строка результата {@link HotelRepository#summarizeLoad()}.
 */
public record HotelLoadRow(
        Long hotelId,
        String name,
        String city,
        Long roomsTotal,
        Long roomsOperational,
        Long timesBookedTotal,
        Long timesBookedMin,
        Long timesBookedMax,
        Double timesBookedAvg,
        Long idleRooms) {}
