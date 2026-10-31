package ru.hotelbooking.booking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.ResultActions;
import ru.hotelbooking.booking.api.dto.AuthRequest;
import ru.hotelbooking.booking.api.dto.RegisterRequest;
import ru.hotelbooking.booking.support.BookingFixtures;
import ru.hotelbooking.booking.support.IntegrationTest;

@DisplayName("Регистрация и вход")
class AuthApiTest extends IntegrationTest {

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("Регистрация: 201, роль USER, токен живёт один час и принимается сервисом")
    void registersUserWithOneHourToken() throws Exception {
        JsonNode response = body(register(new RegisterRequest("carol", "carol123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.role").value("USER")));

        Jwt jwt = jwtDecoder.decode(response.get("accessToken").asText());
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        mockMvc.perform(get("/bookings")
                        .header(
                                AUTHORIZATION,
                                "Bearer " + response.get("accessToken").asText()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Занятое имя пользователя: 409 USERNAME_TAKEN")
    void duplicateUsernameIsRejected() throws Exception {
        fixtures.user("carol");

        register(new RegisterRequest("carol", "carol123"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    @DisplayName("Слишком короткий пароль и недопустимое имя: 400 со списком ошибок")
    void invalidRegistrationIsRejected() throws Exception {
        register(new RegisterRequest("a b", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.length()").value(2));
    }

    @Test
    @DisplayName("Пароль длиннее 72 байт (40 кириллических символов): 400 на поле password, а не 500 от BCrypt")
    void passwordOverBcryptLimitIsRejected() throws Exception {
        register(new RegisterRequest("carol", "п".repeat(40)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("password"))
                .andExpect(jsonPath("$.violations[0].message").value("must not exceed 72 bytes in UTF-8"));
    }

    @Test
    @DisplayName("Стандартные сообщения валидации на английском, как и собственные")
    void validationMessagesAreInEnglish() throws Exception {
        register(new RegisterRequest("carol", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].message").value("must not be blank"));
    }

    @Test
    @DisplayName("Вход с верным паролем: 200 и токен с ролью пользователя")
    void authenticatesWithValidCredentials() throws Exception {
        fixtures.admin("root");

        authenticate(new AuthRequest("root", BookingFixtures.PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    @DisplayName("Неверный пароль или неизвестный пользователь: одинаковый 401 INVALID_CREDENTIALS")
    void rejectsInvalidCredentials() throws Exception {
        fixtures.user("carol");

        authenticate(new AuthRequest("carol", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        authenticate(new AuthRequest("nobody", "whatever"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("Просроченный или поддельный токен: 401 INVALID_TOKEN")
    void rejectsExpiredAndForgedTokens() throws Exception {
        mockMvc.perform(get("/bookings").header(AUTHORIZATION, tokens.expired(1, "carol")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mockMvc.perform(get("/bookings").header(AUTHORIZATION, tokens.forged(1, "carol")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    private ResultActions register(RegisterRequest request) throws Exception {
        return mockMvc.perform(
                post("/user/register").contentType(MediaType.APPLICATION_JSON).content(json(request)));
    }

    private ResultActions authenticate(AuthRequest request) throws Exception {
        return mockMvc.perform(
                post("/user/auth").contentType(MediaType.APPLICATION_JSON).content(json(request)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
