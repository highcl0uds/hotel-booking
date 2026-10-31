package ru.hotelbooking.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import ru.hotelbooking.common.error.ErrorCodes;

public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final BearerTokenAccessDeniedHandler bearerHandler = new BearerTokenAccessDeniedHandler();
    private final ErrorResponseWriter writer;

    public JsonAccessDeniedHandler(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        bearerHandler.handle(request, response, ex);
        writer.write(request, response, HttpStatus.FORBIDDEN, ErrorCodes.ACCESS_DENIED, "Access denied");
    }
}
