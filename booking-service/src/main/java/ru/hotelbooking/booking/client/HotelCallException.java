package ru.hotelbooking.booking.client;

/**
 * Неудачный вызов Hotel Service.
 */
public abstract class HotelCallException extends RuntimeException {

    protected HotelCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
