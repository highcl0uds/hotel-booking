package ru.hotelbooking.eureka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EurekaServerApplicationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Реестр Eureka поднимается и отдаёт список приложений")
    void registryIsAvailable() {
        ResponseEntity<String> response = restTemplate.getForEntity("/eureka/apps", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
