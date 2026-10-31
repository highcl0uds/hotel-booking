package ru.hotelbooking.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorDto(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String traceId,
        List<FieldViolation> violations,
        Map<String, Object> context) {

    public static ErrorDto of(HttpStatusCode status, String code, String message, String path, String traceId) {
        return new ErrorDto(
                Instant.now(), status.value(), reasonPhrase(status), code, message, path, traceId, List.of(), Map.of());
    }

    public ErrorDto withViolations(List<FieldViolation> newViolations) {
        return new ErrorDto(
                timestamp, status, error, code, message, path, traceId, List.copyOf(newViolations), context);
    }

    public ErrorDto withContext(Map<String, Object> newContext) {
        return new ErrorDto(timestamp, status, error, code, message, path, traceId, violations, Map.copyOf(newContext));
    }

    private static String reasonPhrase(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : String.valueOf(status.value());
    }
}
