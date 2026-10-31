package ru.hotelbooking.booking.client;

import org.springframework.http.HttpStatusCode;

/**
 * Hotel Service отклонил запрос с 404 или 409: слот не удержан, компенсировать нечего. Не повторяется.
 */
public class SlotRejectedException extends HotelCallException {

    private final HttpStatusCode status;
    private final String code;

    public SlotRejectedException(HttpStatusCode status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    /** Код ошибки из {@code ErrorDto} Hotel Service, например {@code ROOM_ALREADY_BOOKED}. */
    public String getCode() {
        return code;
    }

    public boolean isNotFound() {
        return status.value() == 404;
    }
}
