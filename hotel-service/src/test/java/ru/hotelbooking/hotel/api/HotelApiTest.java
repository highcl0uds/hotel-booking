package ru.hotelbooking.hotel.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import ru.hotelbooking.hotel.api.dto.HotelRequest;
import ru.hotelbooking.hotel.domain.Hotel;
import ru.hotelbooking.hotel.domain.Room;
import ru.hotelbooking.hotel.support.IntegrationTest;

@DisplayName("Отели: CRUD, поиск, безопасность")
class HotelApiTest extends IntegrationTest {

    private static final HotelRequest NEW_HOTEL =
            new HotelRequest("Volga Riverside", "Kazan", "Kremlevskaya st., 1", 4);

    @Nested
    @DisplayName("Управление отелями")
    class Crud {

        @Test
        @DisplayName("ADMIN добавляет отель: 201 и Location")
        void adminCreatesHotel() throws Exception {
            mockMvc.perform(post("/api/hotels")
                            .header(AUTHORIZATION, adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(NEW_HOTEL)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", matchesPattern("http://.*/api/hotels/\\d+$")))
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.name").value("Volga Riverside"))
                    .andExpect(jsonPath("$.stars").value(4));
        }

        @Test
        @DisplayName("ADMIN изменяет отель")
        void adminUpdatesHotel() throws Exception {
            Hotel hotel = fixtures.hotel("Old name", "Kazan");

            mockMvc.perform(put("/api/hotels/{id}", hotel.getId())
                            .header(AUTHORIZATION, adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(NEW_HOTEL)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Volga Riverside"));
        }

        @Test
        @DisplayName("Нельзя удалить отель, в номерах которого есть предстоящие брони: 409")
        void hotelWithUpcomingReservationsIsNotDeleted() throws Exception {
            Hotel hotel = fixtures.hotel("Grand", "Moscow");
            Room room = fixtures.room(hotel, "101");
            fixtures.hold(room, inDays(5), inDays(7));

            mockMvc.perform(delete("/api/hotels/{id}", hotel.getId()).header(AUTHORIZATION, adminToken))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("HOTEL_HAS_ACTIVE_RESERVATIONS"));
        }

        @Test
        @DisplayName("Отель без броней удаляется вместе с номерами: 204")
        void hotelWithoutReservationsIsDeleted() throws Exception {
            Hotel hotel = fixtures.hotel("Grand", "Moscow");
            Room room = fixtures.room(hotel, "101");

            mockMvc.perform(delete("/api/hotels/{id}", hotel.getId()).header(AUTHORIZATION, adminToken))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get("/api/rooms/{id}", room.getId()).header(AUTHORIZATION, adminToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Поиск отелей")
    class Search {

        @Test
        @DisplayName("Фильтр по городу без учёта регистра, пагинация и сортировка")
        void filtersByCityWithPaging() throws Exception {
            fixtures.hotel("Neva Palace", "Saint Petersburg");
            fixtures.hotel("Grand Moscow", "Moscow");
            fixtures.hotel("Arbat House", "Moscow");

            mockMvc.perform(get("/api/hotels")
                            .header(AUTHORIZATION, userToken)
                            .param("city", "moscow")
                            .param("sort", "name,asc")
                            .param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].name").value("Arbat House"))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(2));
        }

        @Test
        @DisplayName("Сортировка по несуществующему полю: 400 INVALID_SORT_PROPERTY")
        void unknownSortPropertyIsRejected() throws Exception {
            mockMvc.perform(get("/api/hotels").header(AUTHORIZATION, userToken).param("sort", "password"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_SORT_PROPERTY"));
        }

        @Test
        @DisplayName("Несуществующий отель: 404 с кодом HOTEL_NOT_FOUND")
        void unknownHotelIsNotFound() throws Exception {
            mockMvc.perform(get("/api/hotels/{id}", 999).header(AUTHORIZATION, userToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("HOTEL_NOT_FOUND"))
                    .andExpect(jsonPath("$.path").value("/api/hotels/999"));
        }
    }

    @Nested
    @DisplayName("Безопасность и валидация")
    class SecurityAndValidation {

        @Test
        @DisplayName("Без токена: 401 UNAUTHORIZED в формате ErrorDto")
        void requestWithoutTokenIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/hotels"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().exists("WWW-Authenticate"))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Токен, подписанный чужим ключом: 401 INVALID_TOKEN")
        void forgedTokenIsRejected() throws Exception {
            mockMvc.perform(get("/api/hotels").header(AUTHORIZATION, tokens.forged(1, "admin")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }

        @Test
        @DisplayName("Просроченный токен: 401 INVALID_TOKEN")
        void expiredTokenIsRejected() throws Exception {
            mockMvc.perform(get("/api/hotels").header(AUTHORIZATION, tokens.expired(2, "alice")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }

        @Test
        @DisplayName("USER не может добавлять отели: 403 ACCESS_DENIED")
        void userCannotCreateHotel() throws Exception {
            mockMvc.perform(post("/api/hotels")
                            .header(AUTHORIZATION, userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(NEW_HOTEL)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("Служебный токен INTERNAL не даёт доступа к пользовательским операциям: 403")
        void internalTokenCannotListHotels() throws Exception {
            mockMvc.perform(get("/api/hotels").header(AUTHORIZATION, internalToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Невалидное тело запроса: 400 VALIDATION_FAILED со списком полей")
        void invalidRequestIsRejected() throws Exception {
            HotelRequest invalid = new HotelRequest("", "Kazan", "Street", 7);

            mockMvc.perform(post("/api/hotels")
                            .header(AUTHORIZATION, adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations", hasSize(2)));
        }

        @Test
        @DisplayName("URL, отклонённый firewall (';'): 400 MALFORMED_REQUEST, а не 401")
        void rejectedUrlIsBadRequest() throws Exception {
            mockMvc.perform(get("/api/hotels;jsessionid=1").header(AUTHORIZATION, userToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        }

        @Test
        @DisplayName("Некорректный JSON: 400 MALFORMED_REQUEST")
        void malformedJsonIsRejected() throws Exception {
            mockMvc.perform(post("/api/hotels")
                            .header(AUTHORIZATION, adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        }
    }
}
