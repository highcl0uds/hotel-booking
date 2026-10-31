package ru.hotelbooking.gateway.web;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.handler.TracingObservationHandler.TracingContext;
import java.util.Optional;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.web.server.ServerWebExchange;

/**
 * Читает id трассировки, которую Micrometer открыл для текущего обмена (exchange). Этот же id передаётся сервисам
 * (заголовок {@code traceparent}) и печатается в их логах.
 */
public final class TraceIds {

    private TraceIds() {}

    public static Optional<String> current(ServerWebExchange exchange) {
        return ServerRequestObservationContext.findCurrent(exchange.getAttributes())
                .map(context -> context.<TracingContext>get(TracingContext.class))
                .map(TracingContext::getSpan)
                .map(Span::context)
                .map(context -> context.traceId());
    }
}
