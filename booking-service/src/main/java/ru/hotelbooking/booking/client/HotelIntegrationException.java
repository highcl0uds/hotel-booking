package ru.hotelbooking.booking.client;

/**
 * Неожиданный ответ 4xx (например, 400/401/403): ошибка контракта или конфигурации. Не повторяется.
 */
public class HotelIntegrationException extends HotelCallException {

    public HotelIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
