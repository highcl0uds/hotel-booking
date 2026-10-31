package ru.hotelbooking.common.autoconfigure;

import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import ru.hotelbooking.common.security.JwtProperties;

/**
 * Бины, не зависящие от стека: их используют и servlet-сервисы, и реактивный Gateway.
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class CommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
