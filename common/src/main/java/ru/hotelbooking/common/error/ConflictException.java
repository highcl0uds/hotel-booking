package ru.hotelbooking.common.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }

    public ConflictException(String code, String message, Map<String, Object> context) {
        super(HttpStatus.CONFLICT, code, message, context, null);
    }
}
