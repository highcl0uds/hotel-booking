package ru.hotelbooking.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import ru.hotelbooking.common.error.ErrorDto;

public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(
            HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        ErrorDto body = ErrorDto.of(status, code, message, request.getRequestURI(), currentTraceId());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    public static String currentTraceId() {
        return MDC.get(CorrelationHeaders.MDC_TRACE_ID);
    }
}
