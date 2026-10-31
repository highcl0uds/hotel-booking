package ru.hotelbooking.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("hotel-booking") String issuer,
        @DefaultValue("1h") Duration accessTokenTtl,
        @DefaultValue("1m") Duration serviceTokenTtl) {

    private static final int MIN_SECRET_BYTES = 32;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "app.security.jwt.secret must be at least %d bytes long for HS256".formatted(MIN_SECRET_BYTES));
        }
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }
}
