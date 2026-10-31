package ru.hotelbooking.booking.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import ru.hotelbooking.booking.api.dto.CreateBookingRequest;
import ru.hotelbooking.booking.domain.Booking;
import ru.hotelbooking.booking.domain.BookingStatus;
import ru.hotelbooking.booking.domain.CancellationReason;
import ru.hotelbooking.booking.domain.User;
import ru.hotelbooking.booking.service.BookingService;
import ru.hotelbooking.booking.service.BookingService.BookingCreation;
import ru.hotelbooking.booking.support.HotelServiceStub;
import ru.hotelbooking.booking.support.IntegrationTest;
import ru.hotelbooking.common.security.AuthenticatedUser;
import ru.hotelbooking.common.security.Roles;
import ru.hotelbooking.common.test.Concurrently;

@DisplayName("Сага бронирования: PENDING → CONFIRMED / CANCELLED с компенсацией")
class BookingSagaTest extends IntegrationTest {

    private static final long ROOM = 7;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SagaRecoveryJob recoveryJob;

    private User alice;
    private String aliceToken;

    @BeforeEach
    void createUser() {
        alice = fixtures.user("alice");
        aliceToken = tokens.user(alice.getId(), "alice");
    }

    @Nested
    @DisplayName("Успешные сценарии")
    class Success {

        @Test
        @DisplayName("Hotel подтверждает доступность: 201, статус CONFIRMED, история PENDING → CONFIRMED")
        void confirmsBooking() throws Exception {
            HotelServiceStub.holdSucceeds(ROOM);

            JsonNode booking = body(book(explicitRoom(ROOM))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.roomId").value(ROOM))
                    .andExpect(jsonPath("$.hotelId").value(1)));

            long id = booking.get("id").asLong();
            assertThat(HotelServiceStub.holdRequests(ROOM, holdRequestId(id))).isEqualTo(1);
            assertThat(statusHistory(id)).containsExactly("PENDING", "CONFIRMED");
        }

        @Test
        @DisplayName("Вызов Hotel идёт со служебным токеном и заголовком X-Booking-Id")
        void callsHotelWithServiceTokenAndCorrelationId() throws Exception {
            HotelServiceStub.holdSucceeds(ROOM);

            long id = body(book(explicitRoom(ROOM))).get("id").asLong();

            var request = HotelServiceStub.holdRequests(ROOM).getFirst();
            assertThat(request.getHeader("X-Booking-Id")).isEqualTo(String.valueOf(id));
            assertThat(request.getHeader(AUTHORIZATION)).startsWith("Bearer ");
        }

        @Test
        @DisplayName("Автоподбор: занятый кандидат пропускается, бронируется следующий по рекомендации")
        void autoSelectFallsBackToNextCandidate() throws Exception {
            HotelServiceStub.recommends(3, 5);
            HotelServiceStub.holdRejected(3, "ROOM_ALREADY_BOOKED");
            HotelServiceStub.holdSucceeds(5);

            book(autoSelect())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.roomId").value(5))
                    .andExpect(jsonPath("$.autoSelect").value(true));
        }

        @Test
        @DisplayName("Обрыв соединения на первой попытке: повтор с тем же requestId, бронь подтверждена")
        void retriesTransientFailureWithSameRequestId() throws Exception {
            HotelServiceStub.holdFailsOnceThenSucceeds(ROOM);

            long id = body(book(explicitRoom(ROOM)).andExpect(status().isCreated()))
                    .get("id")
                    .asLong();

            assertThat(HotelServiceStub.holdRequests(ROOM)).hasSize(2);
            assertThat(HotelServiceStub.holdRequests(ROOM, holdRequestId(id))).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Отказ Hotel: бронь отменяется без компенсации")
    class Rejection {

        @Test
        @DisplayName("Номер занят (409): ответ 409 с кодом Hotel, бронь CANCELLED, release не вызывается")
        void roomTakenCancelsBooking() throws Exception {
            HotelServiceStub.holdRejected(ROOM, "ROOM_ALREADY_BOOKED");

            JsonNode error = body(book(explicitRoom(ROOM))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ROOM_ALREADY_BOOKED")));

            long id = error.at("/context/bookingId").asLong();
            assertBooking(id, BookingStatus.CANCELLED, CancellationReason.ROOM_UNAVAILABLE, false);
            assertThat(HotelServiceStub.holdRequests(ROOM)).hasSize(1);
            assertThat(HotelServiceStub.releaseRequests(ROOM, holdRequestId(id)))
                    .isZero();
        }

        @Test
        @DisplayName("Автоподбор: все кандидаты заняты параллельными запросами — 409 NO_ROOMS_AVAILABLE, без release")
        void allCandidatesTakenConcurrently() throws Exception {
            HotelServiceStub.recommends(3, 5);
            HotelServiceStub.holdRejected(3, "ROOM_ALREADY_BOOKED");
            HotelServiceStub.holdRejected(5, "ROOM_ALREADY_BOOKED");

            long id = body(book(autoSelect())
                            .andExpect(status().isConflict())
                            .andExpect(jsonPath("$.code").value("NO_ROOMS_AVAILABLE")))
                    .at("/context/bookingId")
                    .asLong();

            assertBooking(id, BookingStatus.CANCELLED, CancellationReason.NO_ROOMS_AVAILABLE, false);
            assertThat(HotelServiceStub.holdRequests(3)).hasSize(1);
            assertThat(HotelServiceStub.holdRequests(5)).hasSize(1);
            assertThat(HotelServiceStub.releaseRequests(5, "hold-%d-5".formatted(id)))
                    .isZero();
        }

        @Test
        @DisplayName("Автоподбор без свободных номеров: 409 NO_ROOMS_AVAILABLE")
        void noFreeRooms() throws Exception {
            HotelServiceStub.recommends();

            book(autoSelect())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("NO_ROOMS_AVAILABLE"));
        }
    }

    @Nested
    @DisplayName("Сбой Hotel: повторы, тайм-аут, компенсация")
    class Failure {

        @Test
        @DisplayName("500 на всех попытках: 3 попытки, release того же requestId, бронь CANCELLED, ответ 503")
        void serverErrorTriggersCompensation() throws Exception {
            HotelServiceStub.holdFails(ROOM, 500);
            HotelServiceStub.releaseSucceeds(ROOM);

            long id = body(book(explicitRoom(ROOM))
                            .andExpect(status().isServiceUnavailable())
                            .andExpect(jsonPath("$.code").value("HOTEL_UNAVAILABLE")))
                    .at("/context/bookingId")
                    .asLong();

            assertThat(HotelServiceStub.holdRequests(ROOM, holdRequestId(id))).isEqualTo(3);
            assertThat(HotelServiceStub.releaseRequests(ROOM, holdRequestId(id)))
                    .isEqualTo(1);
            assertBooking(id, BookingStatus.CANCELLED, CancellationReason.HOTEL_UNAVAILABLE, false);
            assertThat(statusHistory(id)).containsExactly("PENDING", "CANCELLED");
        }

        @Test
        @DisplayName("Тайм-аут ответа: попытки ограничены, слот компенсируется, бронь CANCELLED")
        void timeoutTriggersCompensation() throws Exception {
            HotelServiceStub.holdRespondsAfter(ROOM, 1_000);
            HotelServiceStub.releaseSucceeds(ROOM);

            long id = body(book(explicitRoom(ROOM)).andExpect(status().isServiceUnavailable()))
                    .at("/context/bookingId")
                    .asLong();

            assertThat(HotelServiceStub.holdRequests(ROOM, holdRequestId(id))).isEqualTo(3);
            assertThat(HotelServiceStub.releaseRequests(ROOM, holdRequestId(id)))
                    .isEqualTo(1);
            assertBooking(id, BookingStatus.CANCELLED, CancellationReason.HOTEL_UNAVAILABLE, false);
        }

        @Test
        @DisplayName("Release тоже не прошёл: компенсация откладывается и доставляется фоновой задачей")
        void deferredCompensationIsRetriedByRecoveryJob() throws Exception {
            HotelServiceStub.holdFails(ROOM, 503);
            HotelServiceStub.releaseFails(ROOM);
            long id = body(book(explicitRoom(ROOM)).andExpect(status().isServiceUnavailable()))
                    .at("/context/bookingId")
                    .asLong();
            assertBooking(id, BookingStatus.CANCELLED, CancellationReason.HOTEL_UNAVAILABLE, true);

            // Hotel Service снова доступен, а время открытого состояния circuit breaker истекло: пробные вызовы
            // пропускаются
            HotelServiceStub.releaseSucceeds(ROOM);
            hotelCircuitBreaker().transitionToHalfOpenState();
            recoveryJob.recover();

            assertBooking(id, BookingStatus.CANCELLED, CancellationReason.HOTEL_UNAVAILABLE, false);
            assertThat(HotelServiceStub.releaseRequests(ROOM, holdRequestId(id)))
                    .isEqualTo(4);
        }

        @Test
        @DisplayName("Release отвечает 404 (номера нет): освобождать нечего, компенсация не повторяется")
        void releaseOfUnknownRoomIsNotRetriedForever() throws Exception {
            HotelServiceStub.holdFails(ROOM, 503);
            HotelServiceStub.releaseRoomNotFound(ROOM);
            long id = body(book(explicitRoom(ROOM)).andExpect(status().isServiceUnavailable()))
                    .at("/context/bookingId")
                    .asLong();

            recoveryJob.recover();

            assertBooking(id, BookingStatus.CANCELLED, CancellationReason.HOTEL_UNAVAILABLE, false);
            assertThat(HotelServiceStub.releaseRequests(ROOM, holdRequestId(id)))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Circuit breaker открыт: Hotel больше не вызывается, ответ 503 сразу")
        void openCircuitBreakerFailsFast() throws Exception {
            HotelServiceStub.holdFails(ROOM, 500);
            HotelServiceStub.releaseFails(ROOM);
            book(explicitRoom(ROOM)).andExpect(status().isServiceUnavailable());
            int callsBefore = HotelServiceStub.totalRequests();

            book(explicitRoom(ROOM))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("HOTEL_UNAVAILABLE"));

            assertThat(HotelServiceStub.totalRequests()).isEqualTo(callsBefore);
        }

        @Test
        @DisplayName("Недоступен подбор номеров: бронь CANCELLED без компенсации (ничего не блокировалось)")
        void recommendationFailureCancelsWithoutCompensation() throws Exception {
            HotelServiceStub.recommendFails();

            long id = body(book(autoSelect()).andExpect(status().isServiceUnavailable()))
                    .at("/context/bookingId")
                    .asLong();

            assertBooking(id, BookingStatus.CANCELLED, CancellationReason.HOTEL_UNAVAILABLE, false);
        }
    }

    @Nested
    @DisplayName("Идемпотентность и восстановление")
    class Idempotency {

        @Test
        @DisplayName("Повторная доставка запроса с тем же requestId: 200 с той же бронью, Hotel вызван один раз")
        void repeatedRequestReturnsSameBooking() throws Exception {
            HotelServiceStub.holdSucceeds(ROOM);
            CreateBookingRequest request = explicitRoom(ROOM);
            long id = body(book(request).andExpect(status().isCreated()))
                    .get("id")
                    .asLong();

            book(request).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));

            assertThat(HotelServiceStub.holdRequests(ROOM)).hasSize(1);
            assertThat(countBookings()).isEqualTo(1);
        }

        @Test
        @DisplayName("Тот же requestId с другими датами: 422 IDEMPOTENCY_KEY_REUSED, новой брони нет")
        void requestIdReusedForAnotherRequestIsRejected() throws Exception {
            HotelServiceStub.holdSucceeds(ROOM);
            CreateBookingRequest original = explicitRoom(ROOM);
            book(original).andExpect(status().isCreated());
            var otherDates = new CreateBookingRequest(original.requestId(), false, ROOM, null, inDays(20), inDays(22));

            book(otherDates)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

            assertThat(countBookings()).isEqualTo(1);
            assertThat(HotelServiceStub.holdRequests(ROOM)).hasSize(1);
        }

        @Test
        @DisplayName("Параллельные дубликаты одного запроса: одна бронь, один вызов Hotel")
        void concurrentDuplicatesCreateOneBooking() throws Exception {
            HotelServiceStub.holdRespondsAfter(ROOM, 150);
            CreateBookingRequest request = explicitRoom(ROOM);
            AuthenticatedUser user = new AuthenticatedUser(alice.getId(), "alice", Set.of(Roles.USER));

            List<BookingCreation> results = Concurrently.run(5, () -> bookingService.create(user, request));

            assertThat(results)
                    .extracting(r -> r.booking().id())
                    .containsOnly(results.getFirst().booking().id());
            assertThat(results).filteredOn(BookingCreation::created).hasSize(1);
            assertThat(countBookings()).isEqualTo(1);
            assertThat(HotelServiceStub.holdRequests(ROOM)).hasSize(1);
        }

        @Test
        @DisplayName("Бронь зависла в PENDING (сбой посреди саги): фоновая задача отменяет её и снимает блокировку")
        void stalePendingBookingIsCompensated() {
            HotelServiceStub.releaseSucceeds(ROOM);
            Booking stuck = fixtures.pending(alice, ROOM, inDays(3), inDays(5));
            fixtures.makeOlder(stuck, Duration.ofMinutes(5));

            recoveryJob.recover();

            assertBooking(stuck.getId(), BookingStatus.CANCELLED, CancellationReason.SAGA_TIMEOUT, false);
            assertThat(HotelServiceStub.releaseRequests(ROOM, holdRequestId(stuck.getId())))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Свежая PENDING-бронь (сага ещё идёт) фоновой задачей не трогается")
        void freshPendingBookingIsLeftAlone() {
            Booking inProgress = fixtures.pending(alice, ROOM, inDays(3), inDays(5));

            recoveryJob.recover();

            assertThat(fixtures.reload(inProgress).getStatus()).isEqualTo(BookingStatus.PENDING);
            assertThat(HotelServiceStub.totalRequests()).isZero();
        }
    }

    @Nested
    @DisplayName("Валидация запроса")
    class Validation {

        @Test
        @DisplayName("Дата выезда не позже даты заезда: 400 на поле endDate")
        void endDateMustFollowStartDate() throws Exception {
            var request = new CreateBookingRequest(UUID.randomUUID(), false, ROOM, null, inDays(5), inDays(5));

            book(request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.violations[0].field").value("endDate"));
        }

        @Test
        @DisplayName("Заезд в прошлом: 400")
        void startDateInPastIsRejected() throws Exception {
            var request = new CreateBookingRequest(UUID.randomUUID(), false, ROOM, null, inDays(-1), inDays(2));

            book(request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.violations[0].field").value("startDate"));
        }

        @Test
        @DisplayName("Больше 30 ночей: 400")
        void tooLongStayIsRejected() throws Exception {
            var request = new CreateBookingRequest(UUID.randomUUID(), false, ROOM, null, inDays(1), inDays(40));

            book(request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("Без autoSelect не указан roomId: 400")
        void roomIsRequiredWithoutAutoSelect() throws Exception {
            var request = new CreateBookingRequest(UUID.randomUUID(), false, null, null, inDays(1), inDays(2));

            book(request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.violations[0].field").value("roomSpecified"));
        }

        @Test
        @DisplayName("При autoSelect поле roomId игнорируется, даже некорректное")
        void roomIdIsIgnoredWithAutoSelect() throws Exception {
            HotelServiceStub.recommends(5);
            HotelServiceStub.holdSucceeds(5);
            var request = new CreateBookingRequest(UUID.randomUUID(), true, -1L, 1L, inDays(10), inDays(12));

            book(request)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.roomId").value(5));
        }

        @Test
        @DisplayName("Значение не того типа в JSON: 400 на этом поле, без имён Java-классов в ответе")
        void wrongValueTypeNamesTheField() throws Exception {
            String body = """
                    {"requestId": "not-a-uuid", "autoSelect": true, "startDate": "2030-01-10", "endDate": "2030-01-12"}
                    """;

            mockMvc.perform(post("/booking")
                            .header(AUTHORIZATION, aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations[0].field").value("requestId"))
                    .andExpect(jsonPath("$.violations[0].message").value("must be a UUID"))
                    .andExpect(content().string(not(containsString("java."))));
        }

        @Test
        @DisplayName("Запрос без тела: 400 MALFORMED_REQUEST без сигнатуры метода контроллера в ответе")
        void missingBodyIsMalformed() throws Exception {
            mockMvc.perform(post("/booking").header(AUTHORIZATION, aliceToken).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                    .andExpect(jsonPath("$.message").value("Request body is missing or is not valid JSON"));
        }

        @Test
        @DisplayName("Без requestId: 400; Hotel не вызывается")
        void requestIdIsRequired() throws Exception {
            var request = new CreateBookingRequest(null, false, ROOM, null, inDays(1), inDays(2));

            book(request).andExpect(status().isBadRequest());
            assertThat(HotelServiceStub.totalRequests()).isZero();
        }
    }

    private ResultActions book(CreateBookingRequest request) throws Exception {
        return mockMvc.perform(post("/booking")
                .header(AUTHORIZATION, aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static CreateBookingRequest explicitRoom(long roomId) {
        return new CreateBookingRequest(UUID.randomUUID(), false, roomId, null, inDays(10), inDays(12));
    }

    private static CreateBookingRequest autoSelect() {
        return new CreateBookingRequest(UUID.randomUUID(), true, null, 1L, inDays(10), inDays(12));
    }

    private static String holdRequestId(long bookingId) {
        return "hold-%d-%d".formatted(bookingId, ROOM);
    }

    private void assertBooking(long id, BookingStatus status, CancellationReason reason, boolean compensationPending) {
        var row = jdbcTemplate.queryForMap(
                "select status, cancel_reason, compensation_pending from bookings where id = ?", id);
        assertThat(row.get("STATUS")).isEqualTo(status.name());
        assertThat(row.get("CANCEL_REASON")).isEqualTo(reason.name());
        assertThat(row.get("COMPENSATION_PENDING")).isEqualTo(compensationPending);
    }

    private List<String> statusHistory(long bookingId) {
        return jdbcTemplate.queryForList(
                "select to_status from booking_status_history where booking_id = ? order by id",
                String.class,
                bookingId);
    }

    private Integer countBookings() {
        return jdbcTemplate.queryForObject("select count(*) from bookings", Integer.class);
    }
}
