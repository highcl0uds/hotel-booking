package ru.hotelbooking.common.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String code, String message, Map<String, Object> context, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code, message, context, cause);
    }
}
