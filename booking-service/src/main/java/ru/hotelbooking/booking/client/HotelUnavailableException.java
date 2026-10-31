package ru.hotelbooking.booking.client;

/**
 * Hotel Service не ответил как положено (тайм-аут, 5xx, отказ в соединении, открытый circuit breaker). Исход вызова
 * неизвестен, слот может быть удержан: сага обязана выполнить компенсацию. Повторяется с паузами.
 */
public class HotelUnavailableException extends HotelCallException {

    public HotelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
