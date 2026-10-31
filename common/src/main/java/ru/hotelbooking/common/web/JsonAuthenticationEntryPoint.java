package ru.hotelbooking.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import ru.hotelbooking.common.error.ErrorCodes;

public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final ErrorResponseWriter writer;

    public JsonAuthenticationEntryPoint(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        bearerEntryPoint.commence(request, response, ex);
        if (ex instanceof InvalidBearerTokenException) {
            writer.write(
                    request, response, HttpStatus.UNAUTHORIZED, ErrorCodes.INVALID_TOKEN, "Invalid or expired token");
        } else {
            writer.write(
                    request, response, HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHORIZED, "Authentication required");
        }
    }
}
