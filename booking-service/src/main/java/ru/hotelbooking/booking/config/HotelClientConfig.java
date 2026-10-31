package ru.hotelbooking.booking.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.net.http.HttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import ru.hotelbooking.booking.client.HotelClient;
import ru.hotelbooking.booking.client.ResilientHotelClient;
import ru.hotelbooking.booking.client.RestHotelClient;
import ru.hotelbooking.common.security.JwtTokenFactory;

@Slf4j
@Configuration
@EnableConfigurationProperties({HotelClientProperties.class, SagaProperties.class})
public class HotelClientConfig {

    /** Имя экземпляров Retry и CircuitBreaker, настроенных в {@code resilience4j.*}. */
    public static final String HOTEL_SERVICE = "hotel-service";

    /**
     * Обычный builder для всех остальных клиентов (например, инфраструктурных), чтобы через балансировщик нагрузки шёл
     * только клиент Hotel Service. Scope prototype: builder изменяемый, делить его между клиентами нельзя.
     */
    @Bean
    @Primary
    @Scope("prototype")
    public RestClient.Builder restClientBuilder(RestClientBuilderConfigurer configurer) {
        return configurer.configure(RestClient.builder());
    }

    /**
     * Builder, запросы которого разрешаются через Eureka (lb). Сохраняет инструментирование трассировки Spring Boot.
     */
    @Bean
    @LoadBalanced
    @Scope("prototype")
    public RestClient.Builder loadBalancedRestClientBuilder(RestClientBuilderConfigurer configurer) {
        return configurer.configure(RestClient.builder());
    }

    @Bean
    public HotelClient hotelClient(
            @LoadBalanced RestClient.Builder builder,
            HotelClientProperties properties,
            JwtTokenFactory tokenFactory,
            RetryRegistry retryRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient restClient = builder.baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                // свежий короткоживущий служебный токен на каждый запрос: нет общего изменяемого состояния, токен
                // никогда не просрочен
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders()
                            .setBearerAuth(tokenFactory
                                    .issueServiceToken(properties.serviceName())
                                    .value());
                    return execution.execute(request, body);
                })
                .build();

        return new ResilientHotelClient(
                new RestHotelClient(restClient),
                observedRetry(retryRegistry.retry(HOTEL_SERVICE)),
                observedCircuitBreaker(circuitBreakerRegistry.circuitBreaker(HOTEL_SERVICE)));
    }

    private static Retry observedRetry(Retry retry) {
        retry.getEventPublisher()
                .onRetry(event -> log.warn(
                        "Hotel Service call failed (attempt {}), retrying in {} ms: {}",
                        event.getNumberOfRetryAttempts(),
                        event.getWaitInterval().toMillis(),
                        event.getLastThrowable().getMessage()))
                .onError(event -> log.warn(
                        "Hotel Service call failed after {} attempts: {}",
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable().getMessage()));
        return retry;
    }

    private static CircuitBreaker observedCircuitBreaker(CircuitBreaker circuitBreaker) {
        circuitBreaker
                .getEventPublisher()
                .onStateTransition(event -> log.warn("Hotel Service circuit breaker: {}", event.getStateTransition()));
        return circuitBreaker;
    }
}
