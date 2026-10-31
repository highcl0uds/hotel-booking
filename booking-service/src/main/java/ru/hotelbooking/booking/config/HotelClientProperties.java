package ru.hotelbooking.booking.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Параметры подключения клиента Hotel Service.
 *
 * @param baseUrl логический адрес, который разрешается через Eureka с помощью Spring Cloud LoadBalancer
 * @param connectTimeout время на установку соединения
 * @param readTimeout время ожидания ответа на одну попытку
 * @param serviceName subject служебного токена, отправляемого в Hotel Service
 */
@ConfigurationProperties("app.hotel-client")
public record HotelClientProperties(
        @DefaultValue("http://hotel-service") String baseUrl,
        @DefaultValue("1s") Duration connectTimeout,
        @DefaultValue("2s") Duration readTimeout,
        @DefaultValue("booking-service") String serviceName) {}
