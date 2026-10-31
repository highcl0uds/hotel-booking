package ru.hotelbooking.hotel.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import ru.hotelbooking.hotel.api.dto.SlotRequest;
import ru.hotelbooking.hotel.domain.Hotel;
import ru.hotelbooking.hotel.domain.Room;
import ru.hotelbooking.hotel.support.IntegrationTest;

@DisplayName("Шаг саги в Hotel Service: confirm-availability / release")
class RoomAvailabilityApiTest extends IntegrationTest {

    private Hotel hotel;
    private Room room;

    @BeforeEach
    void createRoom() {
        hotel = fixtures.hotel("Grand", "Moscow");
        room = fixtures.room(hotel, "101");
    }

    @Test
    @DisplayName("Подтверждение доступности блокирует слот и увеличивает times_booked")
    void holdsFreeSlot() throws Exception {
        SlotRequest request = slot("hold-1-1", 1L, inDays(10), inDays(12));

        confirm(room, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.bookingId").value(1))
                .andExpect(jsonPath("$.hotelId").value(hotel.getId()));

        assertThat(fixtures.reload(room).getTimesBooked()).isEqualTo(1);
    }

    @Test
    @DisplayName("Повторная доставка того же requestId не создаёт дубликат и не меняет times_booked")
    void repeatedHoldIsIdempotent() throws Exception {
        SlotRequest request = slot("hold-1-1", 1L, inDays(10), inDays(12));
        String first = confirm(room, request).andReturn().getResponse().getContentAsString();

        confirm(room, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(objectMapper.readTree(first).get("id").asLong()));

        assertThat(fixtures.reload(room).getTimesBooked()).isEqualTo(1);
        assertThat(heldReservations()).isEqualTo(1);
    }

    @Test
    @DisplayName("Пересекающиеся даты другой брони: 409 ROOM_ALREADY_BOOKED")
    void overlappingHoldIsRejected() throws Exception {
        confirm(room, slot("hold-1-1", 1L, inDays(10), inDays(13))).andExpect(status().isOk());

        confirm(room, slot("hold-2-1", 2L, inDays(12), inDays(15)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_ALREADY_BOOKED"))
                .andExpect(jsonPath("$.context.bookingId").value(2));
    }

    @Test
    @DisplayName("Бронь встык (выезд в день заезда) разрешена")
    void backToBackHoldIsAllowed() throws Exception {
        confirm(room, slot("hold-1-1", 1L, inDays(10), inDays(13))).andExpect(status().isOk());

        confirm(room, slot("hold-2-1", 2L, inDays(13), inDays(15))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Номер на ремонте: 409 ROOM_OUT_OF_SERVICE")
    void outOfServiceRoomIsRejected() throws Exception {
        Room broken = fixtures.roomOutOfService(hotel, "102");

        confirm(broken, slot("hold-1-2", 1L, inDays(1), inDays(2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_OUT_OF_SERVICE"));
    }

    @Test
    @DisplayName("Компенсация снимает блокировку и уменьшает times_booked; повтор release — без эффекта")
    void releaseIsIdempotent() throws Exception {
        SlotRequest request = slot("hold-1-1", 1L, inDays(10), inDays(12));
        confirm(room, request).andExpect(status().isOk());

        release(room, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
        release(room, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));

        assertThat(fixtures.reload(room).getTimesBooked()).isZero();
        assertThat(heldReservations()).isZero();
        confirm(room, slot("hold-2-1", 2L, inDays(10), inDays(12))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Release до hold (тайм-аут у Booking) оставляет «надгробие»: опоздавший hold отклоняется")
    void lateHoldAfterReleaseIsRejected() throws Exception {
        SlotRequest request = slot("hold-1-1", 1L, inDays(10), inDays(12));

        release(room, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
        confirm(room, request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_RELEASED"));

        assertThat(fixtures.reload(room).getTimesBooked()).isZero();
    }

    @Test
    @DisplayName("requestId, использованный для другого номера: 409 REQUEST_ID_CONFLICT")
    void requestIdOfAnotherRoomIsRejected() throws Exception {
        Room other = fixtures.room(hotel, "102");
        SlotRequest request = slot("hold-1-1", 1L, inDays(10), inDays(12));
        confirm(room, request).andExpect(status().isOk());

        confirm(other, request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_ID_CONFLICT"));
    }

    @Test
    @DisplayName("Пользовательский токен не допускается к внутренним эндпойнтам: 403")
    void userTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/api/rooms/{id}/confirm-availability", room.getId())
                        .header(AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(slot("hold-1-1", 1L, inDays(1), inDays(2)))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Несуществующий номер: 404 ROOM_NOT_FOUND")
    void unknownRoomIsNotFound() throws Exception {
        mockMvc.perform(post("/api/rooms/{id}/confirm-availability", 999)
                        .header(AUTHORIZATION, internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(slot("hold-1-999", 1L, inDays(1), inDays(2)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    private ResultActions confirm(Room target, SlotRequest request) throws Exception {
        return mockMvc.perform(post("/api/rooms/{id}/confirm-availability", target.getId())
                .header(AUTHORIZATION, internalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)));
    }

    private ResultActions release(Room target, SlotRequest request) throws Exception {
        return mockMvc.perform(post("/api/rooms/{id}/release", target.getId())
                .header(AUTHORIZATION, internalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)));
    }

    private static SlotRequest slot(String requestId, Long bookingId, LocalDate start, LocalDate end) {
        return new SlotRequest(requestId, bookingId, start, end);
    }

    private Integer heldReservations() {
        return jdbcTemplate.queryForObject(
                "select count(*) from room_reservations where status = 'HELD'", Integer.class);
    }
}
