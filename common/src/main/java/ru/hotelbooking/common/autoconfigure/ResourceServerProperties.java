package ru.hotelbooking.common.autoconfigure;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Пути конкретного сервиса, открытые без аутентификации, в дополнение к {@link #INFRASTRUCTURE_PATHS}.
 * Всё остальное требует валидного JWT; проверки ролей объявляются на методах контроллеров через {@code @PreAuthorize}.
 */
@ConfigurationProperties("app.security")
public record ResourceServerProperties(@DefaultValue List<String> publicPaths) {

    /** Health-проверки и документация API открыты всегда. */
    public static final List<String> INFRASTRUCTURE_PATHS =
            List.of("/actuator/health/**", "/actuator/info", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html");
}
