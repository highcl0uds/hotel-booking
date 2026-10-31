package ru.hotelbooking.booking.api.dto;

/**
 * Правила валидации, общие для DTO пользователей.
 */
final class UserConstraints {

    static final String USERNAME_PATTERN = "^[A-Za-z0-9._-]{3,50}$";
    static final String USERNAME_MESSAGE = "must be 3-50 characters: letters, digits, '.', '_' or '-'";
    static final int PASSWORD_MIN = 6;
    static final int PASSWORD_MAX = 72;
    /** Предел BCrypt: более длинный пароль кодировщик отвергает исключением, а не ошибкой валидации. */
    static final int PASSWORD_MAX_BYTES = 72;

    private UserConstraints() {}
}
