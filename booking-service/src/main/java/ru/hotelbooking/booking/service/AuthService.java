package ru.hotelbooking.booking.service;

import ru.hotelbooking.booking.api.dto.AuthRequest;
import ru.hotelbooking.booking.api.dto.RegisterRequest;
import ru.hotelbooking.booking.api.dto.TokenResponse;

public interface AuthService {

    /** Создаёт учётную запись с ролью USER и возвращает её access-токен. */
    TokenResponse register(RegisterRequest request);

    /** Проверяет учётные данные и возвращает новый access-токен (действует один час). */
    TokenResponse authenticate(AuthRequest request);
}
