package ru.hotelbooking.booking.api.dto;

import java.time.Instant;
import ru.hotelbooking.booking.domain.Role;

public record UserResponse(Long id, String username, Role role, Instant createdAt) {}
