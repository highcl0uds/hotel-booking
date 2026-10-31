package ru.hotelbooking.hotel.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import ru.hotelbooking.hotel.api.dto.OccupancyReport;
import ru.hotelbooking.hotel.api.dto.OccupancyReport.DailyOccupancy;
import ru.hotelbooking.hotel.api.dto.OccupancyReport.RoomOccupancy;
import ru.hotelbooking.hotel.api.dto.OccupancyReport.TimesBookedSummary;
import ru.hotelbooking.hotel.domain.Room;
import ru.hotelbooking.hotel.domain.RoomReservation;

/**
 * Чистое вычисление {@link OccupancyReport}: резервы обрезаются по периоду {@code [from, to)} и считаются в
 * номеро-ночах. Только простой подсчёт, без статистического моделирования.
 */
@Component
public class OccupancyCalculator {

    public OccupancyReport calculate(
            long hotelId, LocalDate from, LocalDate to, List<Room> rooms, Collection<RoomReservation> reservations) {
        long days = ChronoUnit.DAYS.between(from, to);
        Map<Long, List<RoomReservation>> byRoom = reservations.stream()
                .collect(Collectors.groupingBy(r -> r.getRoom().getId()));

        List<RoomOccupancy> roomOccupancies = new ArrayList<>();
        List<Long> idleRoomIds = new ArrayList<>();
        long bookedRoomNights = 0;
        for (Room room : rooms) {
            long nights = byRoom.getOrDefault(room.getId(), List.of()).stream()
                    .mapToLong(r -> nightsWithin(r, from, to))
                    .sum();
            bookedRoomNights += nights;
            if (room.isAvailable() && nights == 0) {
                idleRoomIds.add(room.getId());
            }
            roomOccupancies.add(new RoomOccupancy(
                    room.getId(),
                    room.getNumber(),
                    room.isAvailable(),
                    room.getTimesBooked(),
                    nights,
                    percent(nights, days)));
        }

        long roomNights = rooms.size() * days;
        int roomsOperational = (int) rooms.stream().filter(Room::isAvailable).count();
        return new OccupancyReport(
                hotelId,
                from,
                to,
                days,
                rooms.size(),
                roomsOperational,
                roomNights,
                bookedRoomNights,
                percent(bookedRoomNights, roomNights),
                idleRoomIds,
                timesBookedSummary(rooms),
                roomOccupancies,
                daily(from, to, rooms.size(), reservations));
    }

    private static List<DailyOccupancy> daily(
            LocalDate from, LocalDate to, int roomsTotal, Collection<RoomReservation> reservations) {
        List<DailyOccupancy> result = new ArrayList<>();
        for (LocalDate day = from; day.isBefore(to); day = day.plusDays(1)) {
            LocalDate current = day;
            int occupied = (int) reservations.stream()
                    .filter(r -> !current.isBefore(r.getStartDate()) && current.isBefore(r.getEndDate()))
                    .count();
            result.add(new DailyOccupancy(day, occupied, percent(occupied, roomsTotal)));
        }
        return result;
    }

    private static TimesBookedSummary timesBookedSummary(List<Room> rooms) {
        if (rooms.isEmpty()) {
            return new TimesBookedSummary(0, 0, 0.0, 0);
        }
        LongSummaryStatistics stats =
                rooms.stream().mapToLong(Room::getTimesBooked).summaryStatistics();
        return new TimesBookedSummary(
                stats.getMin(), stats.getMax(), round(stats.getAverage()), stats.getMax() - stats.getMin());
    }

    /** Ночи резерва, попадающие в {@code [from, to)}. */
    static long nightsWithin(RoomReservation reservation, LocalDate from, LocalDate to) {
        LocalDate start = reservation.getStartDate().isAfter(from) ? reservation.getStartDate() : from;
        LocalDate end = reservation.getEndDate().isBefore(to) ? reservation.getEndDate() : to;
        return Math.max(0, ChronoUnit.DAYS.between(start, end));
    }

    private static double percent(long part, long total) {
        return total == 0 ? 0.0 : round(part * 100.0 / total);
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
