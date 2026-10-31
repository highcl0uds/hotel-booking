package ru.hotelbooking.booking.domain;

public enum CancellationReason {
    /** Пользователь (или администратор) отменил подтверждённую бронь. */
    CANCELLED_BY_USER,
    /** Hotel Service ответил 409: номер занят на пересекающиеся даты или выведен из эксплуатации. */
    ROOM_UNAVAILABLE,
    /** Hotel Service не знает такого номера. */
    ROOM_NOT_FOUND,
    /** Автоподбор не нашёл свободного номера. */
    NO_ROOMS_AVAILABLE,
    /** Сбой Hotel Service: тайм-ауты, 5xx, ошибки соединения или открытый circuit breaker. */
    HOTEL_UNAVAILABLE,
    /**
     * Сага не завершилась вовремя (например, Booking Service перезапустился); бронь отменила фоновая задача
     * восстановления.
     */
    SAGA_TIMEOUT,
    /** Слот удержан, но бронь не удалось подтвердить локально. */
    CONFIRMATION_FAILED
}
