package ru.hotelbooking.hotel.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import ru.hotelbooking.hotel.api.dto.RoomRequest;
import ru.hotelbooking.hotel.domain.Hotel;
import ru.hotelbooking.hotel.domain.Room;
import ru.hotelbooking.hotel.domain.RoomType;
import ru.hotelbooking.hotel.support.IntegrationTest;

@DisplayName("Номера: CRUD, свободные номера, рекомендации")
class RoomApiTest extends IntegrationTest {

    @Nested
    @DisplayName("Управление номерами")
    class Crud {

        @Test
        @DisplayName("ADMIN добавляет номер в отель; times_booked начинается с нуля")
        void adminCreatesRoom() throws Exception {
            Hotel hotel = fixtures.hotel("Grand", "Moscow");
            RoomRequest request =
                    new RoomRequest(hotel.getId(), "101", RoomType.DELUXE, 3, new BigDecimal("9800.00"), null);

            mockMvc.perform(post("/api/rooms")
                            .header(AUTHORIZATION, adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.hotelId").value(hotel.getId()))
                    .andExpect(jsonPath("$.available").value(true))
                    .andExpect(jsonPath("$.timesBooked").value(0));
        }

        @Test
        @DisplayName("Номер с тем же номером в отеле: 409 ROOM_NUMBER_TAKEN")
        void duplicateRoomNumberIsRejected() throws Exception {
            Hotel hotel = fixtures.hotel("Grand", "Moscow");
            fixtures.room(hotel, "101");
            RoomRequest duplicate =
                    new RoomRequest(hotel.getId(), "101", RoomType.STANDARD, 2, new BigDecimal("5000"), true);

            mockMvc.perform(post("/api/rooms")
                            .header(AUTHORIZATION, adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(duplicate)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ROOM_NUMBER_TAKEN"));
        }

        @Test
        @DisplayName("Неизвестный тип номера: 400 на поле type со списком допустимых значений")
        void unknownRoomTypeIsRejected() throws Exception {
            Hotel hotel = fixtures.hotel("Grand", "Moscow");
            String body = """
                    {"hotelId": %d, "number": "101", "type": "PENTHOUSE", "capacity": 2, "pricePerNight": 5000}
                    """.formatted(hotel.getId());

            mockMvc.perform(post("/api/rooms")
                            .header(AUTHORIZATION, adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations[0].field").value("type"))
                    .andExpect(jsonPath("$.violations[0].message").value("must be one of [STANDARD, DELUXE, SUITE]"));
        }

        @Test
        @DisplayName("Номер в несуществующем отеле: 404")
        void roomInUnknownHotelIsRejected() throws Exception {
            RoomRequest request = new RoomRequest(999L, "101", RoomType.STANDARD, 2, new BigDecimal("5000"), true);

            mockMvc.perform(post("/api/rooms")
                            .header(AUTHORIZATION, adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("HOTEL_NOT_FOUND"));
        }

        @Test
        @DisplayName("Обновление не сбрасывает статистику times_booked")
        void updateKeepsStatistics() throws Exception {
            Hotel hotel = fixtures.hotel("Grand", "Moscow");
            Room room = fixtures.room(hotel, "101", 4);
            RoomRequest withdraw =
                    new RoomRequest(hotel.getId(), "101", RoomType.STANDARD, 2, new BigDecimal("5000"), false);

            mockMvc.perform(put("/api/rooms/{id}", room.getId())
                            .header(AUTHORIZATION, adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(withdraw)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.available").value(false))
                    .andExpect(jsonPath("$.timesBooked").value(4));
        }

        @Test
        @DisplayName("Номер с предстоящей бронью нельзя перенести в другой отель: 409 ROOM_HAS_ACTIVE_RESERVATIONS")
        void roomWithUpcomingReservationsStaysInItsHotel() throws Exception {
            Hotel hotel = fixtures.hotel("Grand", "Moscow");
            Hotel other = fixtures.hotel("Neva", "Saint Petersburg");
            Room room = fixtures.room(hotel, "101");
            fixtures.hold(room, inDays(5), inDays(7));
            RoomRequest move =
                    new RoomRequest(other.getId(), "101", RoomType.STANDARD, 2, new BigDecimal("5000"), true);

            mockMvc.perform(put("/api/rooms/{id}", room.getId())
                            .header(AUTHORIZATION, adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(move)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ROOM_HAS_ACTIVE_RESERVATIONS"));
        }
    }

    @Nested
    @DisplayName("Свободные номера")
    class BookableRooms {

        @Test
        @DisplayName("Номера на ремонте и занятые на пересекающиеся даты исключаются; бронь встык не мешает")
        void excludesOutOfServiceAndOverlappingRooms() throws Exception {
            Hotel hotel = fixtures.hotel("Grand", "Moscow");
            Room free = fixtures.room(hotel, "101");
            Room overlapping = fixtures.room(hotel, "102");
            Room backToBack = fixtures.room(hotel, "103");
            fixtures.roomOutOfService(hotel, "104");
            fixtures.hold(overlapping, inDays(11), inDays(13));
            fixtures.hold(backToBack, inDays(8), inDays(10));

            mockMvc.perform(get("/api/rooms")
                            .header(AUTHORIZATION, userToken)
                            .param("hotelId", hotel.getId().toString())
                            .param("startDate", inDays(10).toString())
                            .param("endDate", inDays(12).toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].id")
                            .value(containsInAnyOrder(
                                    free.getId().intValue(), backToBack.getId().intValue())));
        }

        @Test
        @DisplayName("Без дат возвращаются все работающие номера")
        void withoutPeriodReturnsOperationalRooms() throws Exception {
            Hotel hotel = fixtures.hotel("Grand", "Moscow");
            fixtures.room(hotel, "101");
            fixtures.roomOutOfService(hotel, "102");

            mockMvc.perform(get("/api/rooms").header(AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("Нечисловой параметр фильтра: 400 на этом поле, без имён Java-классов в ответе")
        void nonNumericFilterNamesTheField() throws Exception {
            mockMvc.perform(get("/api/rooms").header(AUTHORIZATION, userToken).param("minCapacity", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations[0].field").value("minCapacity"))
                    .andExpect(jsonPath("$.violations[0].message").value("must be a number"));
        }

        @Test
        @DisplayName("Дата выезда раньше даты заезда: 400 с ошибкой на поле endDate")
        void invalidPeriodIsRejected() throws Exception {
            mockMvc.perform(get("/api/rooms")
                            .header(AUTHORIZATION, userToken)
                            .param("startDate", inDays(5).toString())
                            .param("endDate", inDays(3).toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations[0].field").value("endDate"));
        }
    }

    @Nested
    @DisplayName("Рекомендации")
    class Recommendations {

        @Test
        @DisplayName("Сортировка по возрастанию times_booked, при равенстве — по id")
        void ordersByTimesBookedThenId() throws Exception {
            Hotel hotel = fixtures.hotel("Grand", "Moscow");
            Room busy = fixtures.room(hotel, "101", 5);
            Room tieFirst = fixtures.room(hotel, "102", 1);
            Room idle = fixtures.room(hotel, "103", 0);
            Room tieSecond = fixtures.room(hotel, "104", 1);
            fixtures.roomOutOfService(hotel, "105");

            mockMvc.perform(get("/api/rooms/recommend")
                            .header(AUTHORIZATION, userToken)
                            .param("hotelId", hotel.getId().toString())
                            .param("startDate", inDays(1).toString())
                            .param("endDate", inDays(3).toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id")
                            .value(contains(
                                    idle.getId().intValue(),
                                    tieFirst.getId().intValue(),
                                    tieSecond.getId().intValue(),
                                    busy.getId().intValue())));
        }

        @Test
        @DisplayName("Занятый на эти даты номер не рекомендуется, даже если у него наименьший times_booked")
        void skipsReservedRooms() throws Exception {
            Hotel hotel = fixtures.hotel("Grand", "Moscow");
            Room reserved = fixtures.room(hotel, "101", 0);
            Room other = fixtures.room(hotel, "102", 3);
            fixtures.hold(reserved, inDays(1), inDays(4));

            mockMvc.perform(get("/api/rooms/recommend")
                            .header(AUTHORIZATION, internalToken)
                            .param("startDate", inDays(2).toString())
                            .param("endDate", inDays(3).toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id").value(contains(other.getId().intValue())));
        }

        @Test
        @DisplayName("Без дат — все работающие номера в том же порядке, как GET /api/rooms без дат")
        void withoutPeriodRecommendsOperationalRooms() throws Exception {
            Hotel hotel = fixtures.hotel("Grand", "Moscow");
            Room busy = fixtures.room(hotel, "101", 2);
            Room idle = fixtures.room(hotel, "102", 0);
            fixtures.roomOutOfService(hotel, "103");

            mockMvc.perform(get("/api/rooms/recommend").header(AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id")
                            .value(contains(
                                    idle.getId().intValue(), busy.getId().intValue())));
        }
    }
}
