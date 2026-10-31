package ru.hotelbooking.gateway.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.hotelbooking.common.error.ErrorDto;

/**
 * Пишет ошибки самого Gateway в том же формате {@link ErrorDto}, что и сервисы.
 */
@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ErrorDto body = ErrorDto.of(
                status,
                code,
                message,
                exchange.getRequest().getPath().value(),
                TraceIds.current(exchange).orElse(null));
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.fromCallable(() -> toBuffer(response, body)));
    }

    private DataBuffer toBuffer(ServerHttpResponse response, ErrorDto body) throws JsonProcessingException {
        return response.bufferFactory().wrap(objectMapper.writeValueAsBytes(body));
    }
}
