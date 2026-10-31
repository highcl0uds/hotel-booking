package ru.hotelbooking.booking.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import ru.hotelbooking.booking.config.HotelClientConfig;
import ru.hotelbooking.common.security.JwtProperties;
import ru.hotelbooking.common.test.TestTokens;

/**
 * Базовый класс интеграционных тестов Booking Service: полный Spring-контекст с H2 в памяти и Hotel Service, заменённым
 * WireMock (разрешается через балансировщик нагрузки, как настоящий). Каждый тест начинается с пустых таблиц, без
 * заглушек и с закрытым circuit breaker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(BookingFixtures.class)
public abstract class IntegrationTest {

    protected static final LocalDate TODAY = LocalDate.now();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected BookingFixtures fixtures;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    protected TestTokens tokens;

    @DynamicPropertySource
    static void hotelServiceStub(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.hotel-service[0].uri", HotelServiceStub::baseUrl);
    }

    @BeforeEach
    void resetState() {
        JdbcTestUtils.deleteFromTables(jdbcTemplate, "booking_status_history", "bookings", "users");
        HotelServiceStub.reset();
        hotelCircuitBreaker().reset();
        tokens = new TestTokens(jwtProperties);
    }

    /** Circuit breaker клиента Hotel Service, например чтобы сымитировать истечение времени открытого состояния. */
    protected CircuitBreaker hotelCircuitBreaker() {
        return circuitBreakerRegistry.circuitBreaker(HotelClientConfig.HOTEL_SERVICE);
    }

    protected String json(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    protected static LocalDate inDays(int days) {
        return TODAY.plusDays(days);
    }
}
