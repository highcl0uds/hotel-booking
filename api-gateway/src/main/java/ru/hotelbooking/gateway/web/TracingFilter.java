package ru.hotelbooking.gateway.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import ru.hotelbooking.common.web.CorrelationHeaders;

/**
 * Трассировка запросов на входе в систему: возвращает клиенту trace id в заголовке {@value CorrelationHeaders#TRACE_ID}
 * и пишет по одной строке access-лога на каждый запрос (включая отклонённые проверкой безопасности) с тем же trace id,
 * что сервисы печатают в своих логах.
 */
@Slf4j
@Component
public class TracingFilter implements WebFilter, Ordered {

    /** Раньше {@link InternalEndpointsFilter} и Spring Security, чтобы заголовок был в каждом ответе. */
    public static final int ORDER = -300;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startedAt = System.nanoTime();
        String traceId = TraceIds.current(exchange).orElse("-");
        exchange.getResponse().getHeaders().set(CorrelationHeaders.TRACE_ID, traceId);
        return chain.filter(exchange).doFinally(signal -> logAccess(exchange, traceId, startedAt));
    }

    private static void logAccess(ServerWebExchange exchange, String traceId, long startedAt) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        log.info(
                "{} {} -> {} in {} ms (route={}, traceId={})",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value(),
                exchange.getResponse().getStatusCode() != null
                        ? exchange.getResponse().getStatusCode().value()
                        : "-",
                (System.nanoTime() - startedAt) / 1_000_000,
                route != null ? route.getId() : "-",
                traceId);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
