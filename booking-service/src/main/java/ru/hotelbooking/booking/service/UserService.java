package ru.hotelbooking.booking.service;

import org.springframework.data.domain.Pageable;
import ru.hotelbooking.booking.api.dto.CreateUserRequest;
import ru.hotelbooking.booking.api.dto.UpdateUserRequest;
import ru.hotelbooking.booking.api.dto.UserResponse;
import ru.hotelbooking.common.security.AuthenticatedUser;
import ru.hotelbooking.common.web.PageResponse;

/**
 * Администрирование пользователей (ADMIN) и создание учётных записей.
 */
public interface UserService {

    UserResponse create(CreateUserRequest request);

    UserResponse get(long id);

    PageResponse<UserResponse> list(Pageable pageable);

    UserResponse update(long id, UpdateUserRequest request);

    /** Запрещено для собственной учётной записи вызывающего и для пользователей с предстоящими бронями. */
    void delete(long id, AuthenticatedUser actor);
}
