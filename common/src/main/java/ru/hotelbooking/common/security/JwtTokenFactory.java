package ru.hotelbooking.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

public class JwtTokenFactory {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenFactory(JwtProperties properties, Clock clock) {
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(properties.secretKey()));
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issueUserToken(long userId, String username, Collection<String> roles) {
        return issue(username, userId, roles, properties.accessTokenTtl());
    }

    public IssuedToken issueServiceToken(String serviceName) {
        return issue(serviceName, null, List.of(Roles.INTERNAL), properties.serviceTokenTtl());
    }

    private IssuedToken issue(String subject, Long userId, Collection<String> roles, Duration ttl) {
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(ttl);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(TokenClaims.ROLES, List.copyOf(roles));
        if (userId != null) {
            claims.claim(TokenClaims.USER_ID, userId);
        }
        JwsHeader header = JwsHeader.with(JwtSupport.ALGORITHM).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims.build()))
                .getTokenValue();
        return new IssuedToken(value, expiresAt, ttl);
    }

    public record IssuedToken(String value, Instant expiresAt, Duration ttl) {}
}
