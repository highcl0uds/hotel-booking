package ru.hotelbooking.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public final class ErrorCodes {

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String INVALID_TOKEN = "INVALID_TOKEN";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";
    public static final String DATA_INTEGRITY_VIOLATION = "DATA_INTEGRITY_VIOLATION";
    public static final String INVALID_SORT_PROPERTY = "INVALID_SORT_PROPERTY";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorCodes() {}

    /** Код для ошибок, у которых нет ничего, кроме HTTP-статуса, например 405 или 503: имя статуса. */
    public static String forStatus(HttpStatusCode status) {
        if (status.value() == HttpStatus.BAD_REQUEST.value()) {
            return MALFORMED_REQUEST;
        }
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return NOT_FOUND;
        }
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null && resolved != HttpStatus.INTERNAL_SERVER_ERROR ? resolved.name() : INTERNAL_ERROR;
    }
}
