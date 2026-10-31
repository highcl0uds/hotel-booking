package ru.hotelbooking.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

class JwtTokenFactoryTest {

    private static final JwtProperties PROPERTIES = new JwtProperties(
            "unit-test-secret-unit-test-secret-0123456789",
            "hotel-booking",
            Duration.ofHours(1),
            Duration.ofMinutes(1));

    private final JwtDecoder decoder = JwtSupport.decoder(PROPERTIES);

    @Test
    @DisplayName("Токен пользователя содержит uid, роли и живёт один час")
    void userTokenContainsIdentityAndExpiresInOneHour() {
        // дано
        Instant now = Instant.now();
        JwtTokenFactory factory = new JwtTokenFactory(PROPERTIES, Clock.fixed(now, ZoneOffset.UTC));

        // когда
        JwtTokenFactory.IssuedToken token = factory.issueUserToken(7L, "alice", List.of(Roles.USER));
        Jwt jwt = decoder.decode(token.value());

        // тогда
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        assertThat(user.id()).isEqualTo(7L);
        assertThat(user.username()).isEqualTo("alice");
        assertThat(user.roles()).containsExactly(Roles.USER);
        assertThat(jwt.getExpiresAt()).isEqualTo(token.expiresAt());
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("Роли из токена превращаются в authorities с префиксом ROLE_")
    void rolesAreMappedToAuthorities() {
        // дано
        JwtTokenFactory factory = new JwtTokenFactory(PROPERTIES, Clock.systemUTC());
        Jwt jwt = decoder.decode(factory.issueServiceToken("booking-service").value());

        // когда
        AbstractAuthenticationToken authentication =
                JwtSupport.authenticationConverter().convert(jwt);

        // тогда
        assertThat(authentication.getName()).isEqualTo("booking-service");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_INTERNAL");
    }

    @Test
    @DisplayName("Просроченный токен отклоняется")
    void expiredTokenIsRejected() {
        // дано
        Clock twoHoursAgo = Clock.offset(Clock.systemUTC(), Duration.ofHours(-2));
        String token = new JwtTokenFactory(PROPERTIES, twoHoursAgo)
                .issueUserToken(1L, "bob", List.of(Roles.USER))
                .value();

        // когда / тогда
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    @DisplayName("Короткий секрет не принимается при старте")
    void shortSecretIsRejected() {
        assertThatThrownBy(() -> new JwtProperties("too-short", "iss", Duration.ofHours(1), Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
