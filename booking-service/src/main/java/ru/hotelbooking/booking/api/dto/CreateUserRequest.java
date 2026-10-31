package ru.hotelbooking.booking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.hotelbooking.booking.domain.Role;
import ru.hotelbooking.common.validation.MaxUtf8Bytes;

public record CreateUserRequest(
        @Schema(example = "manager")
        @NotBlank
        @Pattern(regexp = UserConstraints.USERNAME_PATTERN, message = UserConstraints.USERNAME_MESSAGE)
        String username,

        @Schema(example = "manager123")
        @NotBlank
        @Size(min = UserConstraints.PASSWORD_MIN, max = UserConstraints.PASSWORD_MAX)
        @MaxUtf8Bytes(UserConstraints.PASSWORD_MAX_BYTES)
        String password,

        @Schema(example = "ADMIN") @NotNull Role role) {}
