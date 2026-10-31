package ru.hotelbooking.common.security;

import java.util.List;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;

public record AuthenticatedUser(Long id, String username, Set<String> roles) {

    public static AuthenticatedUser from(Jwt jwt) {
        Number uid = jwt.getClaim(TokenClaims.USER_ID);
        List<String> roles = jwt.getClaimAsStringList(TokenClaims.ROLES);
        return new AuthenticatedUser(
                uid != null ? uid.longValue() : null, jwt.getSubject(), roles != null ? Set.copyOf(roles) : Set.of());
    }

    public boolean isAdmin() {
        return roles.contains(Roles.ADMIN);
    }
}
