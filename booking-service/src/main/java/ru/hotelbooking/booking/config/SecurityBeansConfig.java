package ru.hotelbooking.booking.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.hotelbooking.common.security.JwtProperties;
import ru.hotelbooking.common.security.JwtTokenFactory;

/**
 * Booking Service выпускает токены для всей системы; проверка токенов настроена в модуле {@code common}.
 */
@Configuration
public class SecurityBeansConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtTokenFactory jwtTokenFactory(JwtProperties properties, Clock clock) {
        return new JwtTokenFactory(properties, clock);
    }
}
