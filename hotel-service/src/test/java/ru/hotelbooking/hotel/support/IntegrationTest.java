package ru.hotelbooking.hotel.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import ru.hotelbooking.common.security.JwtProperties;
import ru.hotelbooking.common.test.TestTokens;

/**
 * Базовый класс интеграционных тестов Hotel Service: полный Spring-контекст, H2 в памяти, Eureka отключена, настоящие
 * JWT, подписанные тестовым секретом. Каждый тест начинается с пустых таблиц.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(HotelFixtures.class)
public abstract class IntegrationTest {

    protected static final LocalDate TODAY = LocalDate.now();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected HotelFixtures fixtures;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtProperties jwtProperties;

    protected TestTokens tokens;
    protected String userToken;
    protected String adminToken;
    protected String internalToken;

    @BeforeEach
    void resetState() {
        JdbcTestUtils.deleteFromTables(jdbcTemplate, "room_reservations", "rooms", "hotels");
        tokens = new TestTokens(jwtProperties);
        userToken = tokens.user(2, "alice");
        adminToken = tokens.admin(1, "admin");
        internalToken = tokens.internal();
    }

    protected String json(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    protected static LocalDate inDays(int days) {
        return TODAY.plusDays(days);
    }
}
