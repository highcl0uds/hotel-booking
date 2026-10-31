package ru.hotelbooking.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.hotelbooking.common.security.JwtProperties;
import ru.hotelbooking.common.test.TestTokens;
import ru.hotelbooking.common.web.CorrelationHeaders;

/**
 * Gateway перед обоими сервисами; их заменяет один сервер WireMock, зарегистрированный в балансировщике нагрузки под их
 * service id.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability(metrics = false) // Spring Boot по умолчанию отключает передачу трассировки в тестах
@ActiveProfiles("test")
@DisplayName("Gateway: маршрутизация, проверка JWT, трассировка")
class GatewayRoutingTest {

    private static final WireMockServer SERVICES = new WireMockServer(options().dynamicPort());

    static {
        SERVICES.start();
    }

    @Autowired
    private WebTestClient client;

    @Autowired
    private JwtProperties jwtProperties;

    private TestTokens tokens;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.booking-service[0].uri", SERVICES::baseUrl);
        registry.add("spring.cloud.discovery.client.simple.instances.hotel-service[0].uri", SERVICES::baseUrl);
    }

    @AfterAll
    static void stopServices() {
        SERVICES.stop();
    }

    @BeforeEach
    void setUp() {
        SERVICES.resetAll();
        SERVICES.stubFor(get(anyUrl()).willReturn(json("{\"content\":[]}")));
        SERVICES.stubFor(post(anyUrl()).willReturn(json("{\"accessToken\":\"x\"}")));
        tokens = new TestTokens(jwtProperties);
    }

    @Test
    @DisplayName("Запрос с валидным токеном проксируется в Hotel Service вместе с заголовком Authorization")
    void routesHotelRequestsWithToken() {
        String token = tokens.user(2, "alice");

        client.get()
                .uri("/api/hotels?city=Kazan")
                .header(AUTHORIZATION, token)
                .exchange()
                .expectStatus()
                .isOk();

        LoggedRequest forwarded =
                SERVICES.findAll(getRequestedFor(urlPathEqualTo("/api/hotels"))).getFirst();
        assertThat(forwarded.getHeader(AUTHORIZATION)).isEqualTo(token);
        assertThat(forwarded.queryParameter("city").firstValue()).isEqualTo("Kazan");
    }

    @Test
    @DisplayName("/api/bookings/** уходит в Booking Service без префикса: /api/bookings/booking → /booking")
    void stripsBookingPrefix() {
        client.post()
                .uri("/api/bookings/booking")
                .header(AUTHORIZATION, tokens.user(2, "alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isOk();

        assertThat(SERVICES.findAll(postRequestedFor(urlPathEqualTo("/booking"))))
                .hasSize(1);
    }

    @Test
    @DisplayName("Регистрация и вход доступны без токена")
    void authEndpointsArePublic() {
        client.post()
                .uri("/api/bookings/user/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"alice\",\"password\":\"alice123\"}")
                .exchange()
                .expectStatus()
                .isOk();

        assertThat(SERVICES.findAll(postRequestedFor(urlPathEqualTo("/user/auth"))))
                .hasSize(1);
    }

    @Test
    @DisplayName("Без токена: 401 ErrorDto от Gateway, запрос до сервисов не доходит")
    void rejectsAnonymousRequests() {
        client.get()
                .uri("/api/hotels")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectHeader()
                .exists("WWW-Authenticate")
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("UNAUTHORIZED")
                .jsonPath("$.path")
                .isEqualTo("/api/hotels");

        assertThat(SERVICES.findAll(anyRequestedFor(anyUrl()))).isEmpty();
    }

    @Test
    @DisplayName("Поддельный или просроченный токен: 401 INVALID_TOKEN")
    void rejectsInvalidTokens() {
        client.get()
                .uri("/api/hotels")
                .header(AUTHORIZATION, tokens.forged(1, "admin"))
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INVALID_TOKEN");
        client.get()
                .uri("/api/hotels")
                .header(AUTHORIZATION, tokens.expired(1, "admin"))
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    @DisplayName("Внутренние эндпойнты саги не публикуются: 404 даже со служебным токеном")
    void internalEndpointsAreNotPublished() {
        client.post()
                .uri("/api/rooms/5/confirm-availability")
                .header(AUTHORIZATION, tokens.internal())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("NOT_FOUND");
        client.post().uri("/api/rooms/5/release").exchange().expectStatus().isNotFound();
        client.post()
                .uri("/api/rooms/5/release/")
                .header(AUTHORIZATION, tokens.internal())
                .exchange()
                .expectStatus()
                .isNotFound();

        assertThat(SERVICES.findAll(anyRequestedFor(anyUrl()))).isEmpty();
    }

    @Test
    @DisplayName("H2 Console Booking Service через Gateway не публикуется: 404 даже с валидным токеном")
    void h2ConsoleIsNotPublished() {
        String token = tokens.user(2, "alice");

        client.get()
                .uri("/api/bookings/h2-console/")
                .header(AUTHORIZATION, token)
                .exchange()
                .expectStatus()
                .isNotFound();
        client.post()
                .uri("/api/bookings/h2-console/login.do")
                .header(AUTHORIZATION, token)
                .exchange()
                .expectStatus()
                .isNotFound();

        assertThat(SERVICES.findAll(anyRequestedFor(anyUrl()))).isEmpty();
    }

    @Test
    @DisplayName("Неизвестный путь: 404 в формате ErrorDto")
    void unknownPathIsErrorDto() {
        client.get()
                .uri("/unknown")
                .header(AUTHORIZATION, tokens.user(2, "alice"))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("NOT_FOUND")
                .jsonPath("$.path")
                .isEqualTo("/unknown");
    }

    @Test
    @DisplayName("Сервис оборвал соединение: 502 BAD_GATEWAY в формате ErrorDto")
    void brokenConnectionIsBadGateway() {
        SERVICES.stubFor(
                get(urlPathEqualTo("/api/hotels")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        client.get()
                .uri("/api/hotels")
                .header(AUTHORIZATION, tokens.user(2, "alice"))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.BAD_GATEWAY)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("BAD_GATEWAY");
    }

    @Test
    @DisplayName("Сервис не ответил за response-timeout: 504 GATEWAY_TIMEOUT в формате ErrorDto")
    void slowServiceIsGatewayTimeout() {
        SERVICES.stubFor(
                get(urlPathEqualTo("/api/hotels")).willReturn(json("{}").withFixedDelay(2_000)));

        client.get()
                .uri("/api/hotels")
                .header(AUTHORIZATION, tokens.user(2, "alice"))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("GATEWAY_TIMEOUT");
    }

    @Test
    @DisplayName("URL, отклонённый firewall (';'), — 400 MALFORMED_REQUEST в формате ErrorDto")
    void rejectedUrlIsBadRequest() {
        client.get()
                .uri("/api/hotels;jsessionid=1")
                .header(AUTHORIZATION, tokens.user(2, "alice"))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("MALFORMED_REQUEST");

        assertThat(SERVICES.findAll(anyRequestedFor(anyUrl()))).isEmpty();
    }

    @Test
    @DisplayName("Заголовок X-Booking-Id от клиента не доходит до сервисов: его ставит только Booking Service")
    void dropsClientCorrelationHeader() {
        client.get()
                .uri("/api/hotels")
                .header(AUTHORIZATION, tokens.user(2, "alice"))
                .header(CorrelationHeaders.BOOKING_ID, "42")
                .exchange()
                .expectStatus()
                .isOk();

        LoggedRequest forwarded =
                SERVICES.findAll(getRequestedFor(urlPathEqualTo("/api/hotels"))).getFirst();
        assertThat(forwarded.containsHeader(CorrelationHeaders.BOOKING_ID)).isFalse();
    }

    @Test
    @DisplayName("Actuator Gateway закрыт: маршруты нельзя ни читать, ни менять даже с токеном ADMIN")
    void gatewayActuatorIsClosed() {
        String admin = tokens.admin(1, "admin");

        client.get()
                .uri("/actuator/gateway/routes")
                .header(AUTHORIZATION, admin)
                .exchange()
                .expectStatus()
                .isForbidden();
        client.post()
                .uri("/actuator/gateway/refresh")
                .header(AUTHORIZATION, admin)
                .exchange()
                .expectStatus()
                .isForbidden();
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("Трассировка: X-Trace-Id в ответе совпадает с trace id, переданным сервису в traceparent")
    void propagatesTraceId() {
        String traceId = client.get()
                .uri("/api/rooms")
                .header(AUTHORIZATION, tokens.user(2, "alice"))
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(String.class)
                .getResponseHeaders()
                .getFirst(CorrelationHeaders.TRACE_ID);

        LoggedRequest forwarded =
                SERVICES.findAll(getRequestedFor(urlPathEqualTo("/api/rooms"))).getFirst();
        assertThat(traceId).isNotBlank().isNotEqualTo("-");
        assertThat(forwarded.getHeader("traceparent"))
                .as("headers received by the service: %s", forwarded.getHeaders())
                .contains(traceId);
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }
}
