package ru.hotelbooking.hotel.api.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Загрузка отеля за {@code [from, to)}, посчитанная по резервам в статусе HELD.
 *
 * @param roomNights число номеров, умноженное на число дней периода
 * @param bookedRoomNights ночи, покрытые резервами в пределах периода
 * @param occupancyPercent {@code bookedRoomNights / roomNights * 100}
 * @param idleRoomIds работающие номера без единой забронированной ночи за период
 */
public record OccupancyReport(
        Long hotelId,
        LocalDate from,
        LocalDate to,
        long days,
        int roomsTotal,
        int roomsOperational,
        long roomNights,
        long bookedRoomNights,
        double occupancyPercent,
        List<Long> idleRoomIds,
        TimesBookedSummary timesBooked,
        List<RoomOccupancy> rooms,
        List<DailyOccupancy> daily) {

    public record RoomOccupancy(
            Long roomId,
            String number,
            boolean available,
            long timesBooked,
            long bookedNights,
            double occupancyPercent) {}

    public record DailyOccupancy(LocalDate date, int occupiedRooms, double occupancyPercent) {}

    /** Разброс счётчика бронирований; {@code spread = max - min}. */
    public record TimesBookedSummary(long min, long max, double avg, long spread) {}
}
