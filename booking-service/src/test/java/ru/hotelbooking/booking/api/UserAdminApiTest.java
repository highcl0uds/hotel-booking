package ru.hotelbooking.booking.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import ru.hotelbooking.booking.api.dto.AuthRequest;
import ru.hotelbooking.booking.api.dto.CreateUserRequest;
import ru.hotelbooking.booking.api.dto.UpdateUserRequest;
import ru.hotelbooking.booking.domain.Role;
import ru.hotelbooking.booking.domain.User;
import ru.hotelbooking.booking.support.IntegrationTest;

@DisplayName("Администрирование пользователей")
class UserAdminApiTest extends IntegrationTest {

    private User admin;
    private String adminToken;

    @BeforeEach
    void createAdmin() {
        admin = fixtures.admin("root");
        adminToken = tokens.admin(admin.getId(), "root");
    }

    @Test
    @DisplayName("ADMIN создаёт пользователя с ролью ADMIN: 201 и Location")
    void adminCreatesUser() throws Exception {
        mockMvc.perform(post("/user")
                        .header(AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateUserRequest("manager", "manager123", Role.ADMIN))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/user/\\d+$")))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("PATCH меняет только переданные поля; новый пароль сразу действует")
    void adminUpdatesUserPartially() throws Exception {
        User alice = fixtures.user("alice");

        mockMvc.perform(patch("/user/{id}", alice.getId())
                        .header(AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateUserRequest(null, "new-password", Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mockMvc.perform(post("/user/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AuthRequest("alice", "new-password"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Список пользователей с пагинацией")
    void adminListsUsers() throws Exception {
        fixtures.user("alice");
        fixtures.user("bob");

        mockMvc.perform(get("/user").header(AUTHORIZATION, adminToken).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @DisplayName("Пользователь с предстоящей бронью не удаляется: 409 USER_HAS_ACTIVE_BOOKINGS")
    void userWithUpcomingBookingIsNotDeleted() throws Exception {
        User alice = fixtures.user("alice");
        fixtures.confirmed(alice, 1, inDays(5), inDays(7));

        mockMvc.perform(delete("/user/{id}", alice.getId()).header(AUTHORIZATION, adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_HAS_ACTIVE_BOOKINGS"));
    }

    @Test
    @DisplayName("Пользователь с недоставленным освобождением слота не удаляется: 409, иначе слот завис бы навсегда")
    void userWithPendingCompensationIsNotDeleted() throws Exception {
        User alice = fixtures.user("alice");
        fixtures.cancelledAwaitingRelease(alice, 1, inDays(5), inDays(7));

        mockMvc.perform(delete("/user/{id}", alice.getId()).header(AUTHORIZATION, adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_HAS_ACTIVE_BOOKINGS"));
    }

    @Test
    @DisplayName("Пользователь без активных броней удаляется: 204, затем 404")
    void userIsDeleted() throws Exception {
        User alice = fixtures.user("alice");
        fixtures.confirmed(alice, 1, inDays(-5), inDays(-3));

        mockMvc.perform(delete("/user/{id}", alice.getId()).header(AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/user/{id}", alice.getId()).header(AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("Администратор не может удалить сам себя: 409")
    void adminCannotDeleteSelf() throws Exception {
        mockMvc.perform(delete("/user/{id}", admin.getId()).header(AUTHORIZATION, adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DELETE_SELF"));
    }

    @Test
    @DisplayName("USER не имеет доступа к администрированию: 403")
    void userCannotManageUsers() throws Exception {
        User alice = fixtures.user("alice");

        mockMvc.perform(get("/user").header(AUTHORIZATION, tokens.user(alice.getId(), "alice")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
