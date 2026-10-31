package ru.hotelbooking.gateway.web;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;
import ru.hotelbooking.common.error.ErrorCodes;

/**
 * Пути, которые есть в сервисах, но не публикуются: запросы к ним заканчиваются здесь ответом 404 ещё до аутентификации
 * и маршрутизации, кто бы их ни отправил.
 * <ul>
 *   <li>межсервисные эндпойнты саги бронирования;
 *   <li>H2 Console Booking Service (профиль demo): маршрут {@code /api/bookings/**} отрезает свой префикс, поэтому без
 *       этого правила любой аутентифицированный пользователь попал бы в консоль и выполнял бы SQL от имени владельца
 *       базы.
 * </ul>
 */
@Slf4j
@Component
public class InternalEndpointsFilter implements WebFilter, Ordered {

    /**
     * Выполняется раньше цепочки Spring Security (порядок -100), поэтому даже анонимный клиент узнаёт только «не
     * найдено».
     */
    public static final int ORDER = -200;

    /**
     * {@code /**} покрывает и завершающий слеш, и более длинные пути; другие варианты написания ({@code //},
     * {@code /./}, {@code ;}, закодированные символы) отклоняет firewall Spring Security, так что обойти проверку
     * нельзя.
     */
    private static final List<PathPattern> UNPUBLISHED_PATHS = List.of(
            PathPatternParser.defaultInstance.parse("/api/rooms/{id}/confirm-availability/**"),
            PathPatternParser.defaultInstance.parse("/api/rooms/{id}/release/**"),
            PathPatternParser.defaultInstance.parse("/api/bookings/h2-console/**"));

    private final ErrorResponseWriter errorWriter;

    public InternalEndpointsFilter(ErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var path = exchange.getRequest().getPath().pathWithinApplication();
        if (UNPUBLISHED_PATHS.stream().anyMatch(pattern -> pattern.matches(path))) {
            log.warn(
                    "Blocked external call to unpublished path {} {}",
                    exchange.getRequest().getMethod(),
                    path);
            return errorWriter.write(exchange, HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "Not found");
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
