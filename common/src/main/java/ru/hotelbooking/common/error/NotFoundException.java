package ru.hotelbooking.common.error;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }

    public static NotFoundException of(String entity, Object id) {
        return new NotFoundException(
                entity.toUpperCase() + "_NOT_FOUND", "%s with id %s not found".formatted(capitalize(entity), id));
    }

    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
