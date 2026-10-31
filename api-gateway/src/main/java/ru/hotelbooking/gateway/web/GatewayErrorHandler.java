package ru.hotelbooking.gateway.web;

import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.hotelbooking.common.error.ErrorCodes;

/**
 * Ошибки самого Gateway в формате {@code ErrorDto} сервисов вместо стандартного тела Spring Boot: 404 для неизвестных
 * путей, 503, если у сервиса нет зарегистрированных или доступных экземпляров, 502 при обрыве соединения, 504, если
 * сервис не ответил за response timeout.
 */
@Slf4j
@Component
@Order(-2) // раньше DefaultErrorWebExceptionHandler из Spring Boot (-1)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private final ErrorResponseWriter errorWriter;

    public GatewayErrorHandler(ErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        HttpStatus status = statusOf(ex);
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            log.error(
                    "Unexpected error on {} {}",
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath(),
                    ex);
        } else if (status.is5xxServerError()) {
            log.warn(
                    "{} {} failed with {}: {}",
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath(),
                    status.value(),
                    ex.toString());
        }
        return errorWriter.write(exchange, status, ErrorCodes.forStatus(status), messageOf(status));
    }

    static HttpStatus statusOf(Throwable ex) {
        if (ex instanceof ResponseStatusException responseStatus) {
            HttpStatus status =
                    HttpStatus.resolve(responseStatus.getStatusCode().value());
            return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        }
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(ex);
        if (cause instanceof ConnectException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (cause instanceof TimeoutException) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        if (cause instanceof IOException) {
            return HttpStatus.BAD_GATEWAY;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String messageOf(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Not found";
            case BAD_GATEWAY -> "The service returned an invalid response";
            case SERVICE_UNAVAILABLE -> "The service is temporarily unavailable, please retry later";
            case GATEWAY_TIMEOUT -> "The service did not respond in time";
            case INTERNAL_SERVER_ERROR -> "Internal server error";
            default -> status.getReasonPhrase();
        };
    }
}
