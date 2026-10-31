package ru.hotelbooking.booking.api.dto;

import java.time.Instant;

/**
 * Выданный access-токен; передавайте его как {@code Authorization: Bearer <accessToken>}.
 *
 * @param expiresIn срок жизни в секундах (3600)
 */
public record TokenResponse(
        String accessToken, String tokenType, long expiresIn, Instant expiresAt, UserResponse user) {

    public static TokenResponse bearer(String accessToken, long expiresIn, Instant expiresAt, UserResponse user) {
        return new TokenResponse(accessToken, "Bearer", expiresIn, expiresAt, user);
    }
}
