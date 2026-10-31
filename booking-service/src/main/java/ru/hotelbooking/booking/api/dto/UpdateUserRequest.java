package ru.hotelbooking.booking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.hotelbooking.booking.domain.Role;
import ru.hotelbooking.common.validation.MaxUtf8Bytes;

/**
 * Частичное обновление: поля со значением {@code null} не меняются.
 */
public record UpdateUserRequest(
        @Schema(example = "alice.smith")
        @Pattern(regexp = UserConstraints.USERNAME_PATTERN, message = UserConstraints.USERNAME_MESSAGE)
        String username,

        @Schema(example = "new-password")
        @Size(min = UserConstraints.PASSWORD_MIN, max = UserConstraints.PASSWORD_MAX)
        @MaxUtf8Bytes(UserConstraints.PASSWORD_MAX_BYTES)
        String password,

        @Schema(example = "ADMIN") Role role) {}
