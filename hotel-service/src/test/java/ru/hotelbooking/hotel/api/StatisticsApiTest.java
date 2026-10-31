package ru.hotelbooking.hotel.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hotelbooking.hotel.domain.Hotel;
import ru.hotelbooking.hotel.domain.Room;
import ru.hotelbooking.hotel.support.IntegrationTest;

@DisplayName("Статистика загруженности (ADMIN)")
class StatisticsApiTest extends IntegrationTest {

    @Test
    @DisplayName("Счётчики номеров отсортированы по times_booked по убыванию, с предстоящими бронями")
    void roomStatsAreSortedByTimesBooked() throws Exception {
        Hotel hotel = fixtures.hotel("Grand", "Moscow");
        Room quiet = fixtures.room(hotel, "101", 1);
        Room popular = fixtures.room(hotel, "102", 6);
        fixtures.hold(quiet, inDays(3), inDays(5));

        mockMvc.perform(get("/api/rooms/stats").header(AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].roomId")
                        .value(contains(
                                popular.getId().intValue(), quiet.getId().intValue())))
                .andExpect(jsonPath("$.content[1].timesBooked").value(2))
                .andExpect(jsonPath("$.content[1].upcomingReservations").value(1));
    }

    @Test
    @DisplayName("Сводка по отелям: min/max/avg times_booked, разброс и простаивающие номера")
    void hotelLoadSummary() throws Exception {
        Hotel hotel = fixtures.hotel("Grand", "Moscow");
        fixtures.room(hotel, "101", 0);
        fixtures.room(hotel, "102", 2);
        fixtures.room(hotel, "103", 4);
        fixtures.roomOutOfService(hotel, "104");

        mockMvc.perform(get("/api/hotels/stats").header(AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].roomsTotal").value(4))
                .andExpect(jsonPath("$[0].roomsOperational").value(3))
                .andExpect(jsonPath("$[0].timesBookedTotal").value(6))
                .andExpect(jsonPath("$[0].timesBookedMax").value(4))
                .andExpect(jsonPath("$[0].loadSpread").value(4))
                .andExpect(jsonPath("$[0].idleRooms").value(1));
    }

    @Test
    @DisplayName("Отчёт о загрузке за период: room-nights, проценты, дни и простаивающие номера")
    void occupancyReport() throws Exception {
        Hotel hotel = fixtures.hotel("Grand", "Moscow");
        Room booked = fixtures.room(hotel, "101");
        Room idle = fixtures.room(hotel, "102");
        // 2 из 3 ночей попадают в 4-дневный период
        fixtures.hold(booked, inDays(3), inDays(6));

        mockMvc.perform(get("/api/hotels/{id}/occupancy", hotel.getId())
                        .header(AUTHORIZATION, adminToken)
                        .param("from", inDays(0).toString())
                        .param("to", inDays(5).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(5))
                .andExpect(jsonPath("$.roomNights").value(10))
                .andExpect(jsonPath("$.bookedRoomNights").value(2))
                .andExpect(jsonPath("$.occupancyPercent").value(20.0))
                .andExpect(jsonPath("$.idleRoomIds").value(contains(idle.getId().intValue())))
                .andExpect(jsonPath("$.daily", hasSize(5)))
                .andExpect(jsonPath("$.daily[3].occupiedRooms").value(1));
    }

    @Test
    @DisplayName("Статистика доступна только администратору: USER получает 403")
    void statisticsAreAdminOnly() throws Exception {
        mockMvc.perform(get("/api/hotels/stats").header(AUTHORIZATION, userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Период отчёта задан наоборот: 400 с ошибкой на поле to")
    void invalidPeriodIsRejected() throws Exception {
        Hotel hotel = fixtures.hotel("Grand", "Moscow");

        mockMvc.perform(get("/api/hotels/{id}/occupancy", hotel.getId())
                        .header(AUTHORIZATION, adminToken)
                        .param("from", inDays(5).toString())
                        .param("to", inDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("to"));
    }
}
