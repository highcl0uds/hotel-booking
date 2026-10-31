package ru.hotelbooking.common.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> context;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of(), null);
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> context, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.context = Map.copyOf(context);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getContext() {
        return context;
    }
}
