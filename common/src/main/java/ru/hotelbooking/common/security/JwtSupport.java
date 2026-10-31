package ru.hotelbooking.common.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

public final class JwtSupport {

    public static final MacAlgorithm ALGORITHM = MacAlgorithm.HS256;
    private static final String ROLE_PREFIX = "ROLE_";

    private JwtSupport() {}

    public static JwtAuthenticationConverter authenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenClaims.ROLES);
        authorities.setAuthorityPrefix(ROLE_PREFIX);

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    public static OAuth2TokenValidator<Jwt> validator(JwtProperties properties) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, sub -> sub != null && !sub.isBlank()));
    }

    public static JwtDecoder decoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.secretKey())
                .macAlgorithm(ALGORITHM)
                .build();
        decoder.setJwtValidator(validator(properties));
        return decoder;
    }
}
