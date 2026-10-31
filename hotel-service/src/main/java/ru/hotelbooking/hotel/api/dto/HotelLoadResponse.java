package ru.hotelbooking.hotel.api.dto;

/**
 * Насколько равномерно брони распределены по номерам отеля.
 *
 * @param loadSpread {@code timesBookedMax - timesBookedMin}; чем ближе к нулю, тем равномернее загрузка
 * @param idleRooms работающие номера, которые ни разу не бронировали
 */
public record HotelLoadResponse(
        Long hotelId,
        String name,
        String city,
        long roomsTotal,
        long roomsOperational,
        long timesBookedTotal,
        long timesBookedMin,
        long timesBookedMax,
        double timesBookedAvg,
        long loadSpread,
        long idleRooms) {}
