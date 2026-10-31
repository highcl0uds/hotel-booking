package ru.hotelbooking.hotel.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.hotelbooking.hotel.api.dto.OccupancyReport;
import ru.hotelbooking.hotel.domain.Room;
import ru.hotelbooking.hotel.domain.RoomReservation;
import ru.hotelbooking.hotel.domain.SlotCommand;

class OccupancyCalculatorTest {

    private static final LocalDate FROM = LocalDate.of(2030, 3, 1);
    private static final LocalDate TO = LocalDate.of(2030, 3, 11);

    private final OccupancyCalculator calculator = new OccupancyCalculator();

    @Test
    @DisplayName("Брони обрезаются по границам периода; считаются только ночи внутри него")
    void clipsReservationsToPeriod() {
        Room room = room(1L, true, 2);
        RoomReservation startsBefore = reservation(room, FROM.minusDays(3), FROM.plusDays(2));
        RoomReservation endsAfter = reservation(room, TO.minusDays(1), TO.plusDays(5));

        OccupancyReport report = calculator.calculate(7L, FROM, TO, List.of(room), List.of(startsBefore, endsAfter));

        assertThat(report.days()).isEqualTo(10);
        assertThat(report.bookedRoomNights()).isEqualTo(3);
        assertThat(report.occupancyPercent()).isEqualTo(30.0);
        assertThat(report.daily()).hasSize(10);
        assertThat(report.daily().get(0).occupiedRooms()).isEqualTo(1);
        assertThat(report.daily().get(2).occupiedRooms()).isZero();
    }

    @Test
    @DisplayName("Простаивающими считаются только работающие номера без ночей в периоде")
    void idleRoomsAreOperationalRoomsWithoutNights() {
        Room busy = room(1L, true, 5);
        Room idle = room(2L, true, 1);
        Room maintenance = room(3L, false, 0);

        OccupancyReport report = calculator.calculate(
                7L, FROM, TO, List.of(busy, idle, maintenance), List.of(reservation(busy, FROM, FROM.plusDays(1))));

        assertThat(report.idleRoomIds()).containsExactly(2L);
        assertThat(report.roomsOperational()).isEqualTo(2);
        assertThat(report.timesBooked().spread()).isEqualTo(5);
        assertThat(report.timesBooked().avg()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Отель без номеров: нулевые показатели без деления на ноль")
    void emptyHotel() {
        OccupancyReport report = calculator.calculate(7L, FROM, TO, List.of(), List.of());

        assertThat(report.occupancyPercent()).isZero();
        assertThat(report.timesBooked().max()).isZero();
    }

    private static Room room(long id, boolean available, int timesBooked) {
        Room room = new Room();
        ReflectionTestUtils.setField(room, "id", id);
        room.setNumber(String.valueOf(100 + id));
        room.setAvailable(available);
        for (int i = 0; i < timesBooked; i++) {
            room.registerBooking();
        }
        return room;
    }

    private static RoomReservation reservation(Room room, LocalDate start, LocalDate end) {
        return RoomReservation.hold(room, new SlotCommand("r-" + start + room.getId(), 1L, start, end));
    }
}
