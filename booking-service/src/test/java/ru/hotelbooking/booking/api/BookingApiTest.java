package ru.hotelbooking.booking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hotelbooking.booking.domain.Booking;
import ru.hotelbooking.booking.domain.BookingStatus;
import ru.hotelbooking.booking.domain.CancellationReason;
import ru.hotelbooking.booking.domain.User;
import ru.hotelbooking.booking.support.HotelServiceStub;
import ru.hotelbooking.booking.support.IntegrationTest;

@DisplayName("Брони: история, доступ только к своим, отмена")
class BookingApiTest extends IntegrationTest {

    private User alice;
    private User bob;
    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void createUsers() {
        alice = fixtures.user("alice");
        bob = fixtures.user("bob");
        aliceToken = tokens.user(alice.getId(), "alice");
        bobToken = tokens.user(bob.getId(), "bob");
    }

    @Nested
    @DisplayName("История бронирований")
    class History {

        @Test
        @DisplayName("Пользователь видит только свои брони, новые первыми")
        void returnsOnlyOwnBookingsNewestFirst() throws Exception {
            Booking older = fixtures.confirmed(alice, 1, inDays(5), inDays(7));
            Booking newer = fixtures.cancelled(alice, 2, inDays(8), inDays(9));
            fixtures.confirmed(bob, 3, inDays(5), inDays(7));

            mockMvc.perform(get("/bookings").header(AUTHORIZATION, aliceToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[*].id")
                            .value(contains(
                                    newer.getId().intValue(), older.getId().intValue())));
        }

        @Test
        @DisplayName("Фильтр по статусу и пагинация")
        void filtersByStatusWithPaging() throws Exception {
            fixtures.confirmed(alice, 1, inDays(5), inDays(7));
            fixtures.confirmed(alice, 2, inDays(5), inDays(7));
            fixtures.cancelled(alice, 3, inDays(5), inDays(7));

            mockMvc.perform(get("/bookings")
                            .header(AUTHORIZATION, aliceToken)
                            .param("status", "CONFIRMED")
                            .param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(2));
        }

        @Test
        @DisplayName("Неизвестный статус в фильтре: 400 на поле status со списком допустимых значений")
        void unknownStatusFilterIsRejected() throws Exception {
            mockMvc.perform(get("/bookings").header(AUTHORIZATION, aliceToken).param("status", "FOO"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations[0].field").value("status"))
                    .andExpect(jsonPath("$.violations[0].message")
                            .value("must be one of [PENDING, CONFIRMED, CANCELLED]"));
        }

        @Test
        @DisplayName("Без токена: 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/bookings"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Nested
    @DisplayName("Просмотр брони")
    class GetBooking {

        @Test
        @DisplayName("Владелец получает бронь вместе с историей переходов статусов")
        void ownerSeesBookingWithHistory() throws Exception {
            Booking booking = fixtures.confirmed(alice, 1, inDays(5), inDays(7));

            mockMvc.perform(get("/booking/{id}", booking.getId()).header(AUTHORIZATION, aliceToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.history[*].to").value(contains("PENDING", "CONFIRMED")));
        }

        @Test
        @DisplayName("Чужая бронь: 403 ACCESS_DENIED")
        void foreignBookingIsForbidden() throws Exception {
            Booking booking = fixtures.confirmed(alice, 1, inDays(5), inDays(7));

            mockMvc.perform(get("/booking/{id}", booking.getId()).header(AUTHORIZATION, bobToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("Администратор видит любую бронь")
        void adminSeesAnyBooking() throws Exception {
            User admin = fixtures.admin("root");
            Booking booking = fixtures.confirmed(alice, 1, inDays(5), inDays(7));

            mockMvc.perform(get("/booking/{id}", booking.getId())
                            .header(AUTHORIZATION, tokens.admin(admin.getId(), "root")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Несуществующая бронь: 404 BOOKING_NOT_FOUND")
        void unknownBookingIsNotFound() throws Exception {
            mockMvc.perform(get("/booking/{id}", 999).header(AUTHORIZATION, aliceToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("Отмена брони")
    class Cancel {

        @Test
        @DisplayName("Отмена подтверждённой брони: 204, слот освобождён в Hotel, повторная отмена ничего не делает")
        void cancelsAndReleasesSlotIdempotently() throws Exception {
            Booking booking = fixtures.confirmed(alice, 4, inDays(5), inDays(7));
            HotelServiceStub.releaseSucceeds(4);
            String requestId = "hold-%d-4".formatted(booking.getId());

            mockMvc.perform(delete("/booking/{id}", booking.getId()).header(AUTHORIZATION, aliceToken))
                    .andExpect(status().isNoContent());
            mockMvc.perform(delete("/booking/{id}", booking.getId()).header(AUTHORIZATION, aliceToken))
                    .andExpect(status().isNoContent());

            Booking cancelled = fixtures.reload(booking);
            assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(cancelled.getCancelReason()).isEqualTo(CancellationReason.CANCELLED_BY_USER);
            assertThat(cancelled.isCompensationPending()).isFalse();
            assertThat(HotelServiceStub.releaseRequests(4, requestId)).isEqualTo(1);
        }

        @Test
        @DisplayName("Hotel недоступен при отмене: бронь отменена, освобождение слота отложено")
        void cancellationSurvivesHotelOutage() throws Exception {
            Booking booking = fixtures.confirmed(alice, 4, inDays(5), inDays(7));
            HotelServiceStub.releaseFails(4);

            mockMvc.perform(delete("/booking/{id}", booking.getId()).header(AUTHORIZATION, aliceToken))
                    .andExpect(status().isNoContent());

            Booking cancelled = fixtures.reload(booking);
            assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(cancelled.isCompensationPending()).isTrue();
        }

        @Test
        @DisplayName("Чужую бронь отменить нельзя: 403")
        void foreignBookingCannotBeCancelled() throws Exception {
            Booking booking = fixtures.confirmed(alice, 4, inDays(5), inDays(7));

            mockMvc.perform(delete("/booking/{id}", booking.getId()).header(AUTHORIZATION, bobToken))
                    .andExpect(status().isForbidden());
            assertThat(fixtures.reload(booking).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        @DisplayName("Завершённое проживание отменить нельзя: 409 BOOKING_COMPLETED")
        void completedStayCannotBeCancelled() throws Exception {
            Booking booking = fixtures.confirmed(alice, 4, inDays(-5), inDays(-2));

            mockMvc.perform(delete("/booking/{id}", booking.getId()).header(AUTHORIZATION, aliceToken))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("BOOKING_COMPLETED"));
        }

        @Test
        @DisplayName("Бронь ещё обрабатывается сагой: 409 BOOKING_IN_PROGRESS")
        void pendingBookingCannotBeCancelled() throws Exception {
            Booking booking = fixtures.pending(alice, 4, inDays(5), inDays(7));

            mockMvc.perform(delete("/booking/{id}", booking.getId()).header(AUTHORIZATION, aliceToken))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("BOOKING_IN_PROGRESS"));
        }
    }
}
