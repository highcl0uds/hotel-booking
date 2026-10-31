package ru.hotelbooking.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.firewall.ServerExchangeRejectedHandler;
import ru.hotelbooking.common.error.ErrorCodes;
import ru.hotelbooking.common.security.JwtProperties;
import ru.hotelbooking.common.security.JwtSupport;
import ru.hotelbooking.gateway.web.ErrorResponseWriter;

/**
 * Первая линия защиты: каждый запрос, кроме регистрации, входа, документации и health-проверок, должен нести валидный
 * JWT. Токен передаётся дальше без изменений; каждый сервис проверяет его заново и сам проверяет роли.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/api/bookings/user/register",
        "/api/bookings/user/auth",
        "/api/bookings/v3/api-docs/**",
        "/api/hotels/v3/api-docs/**",
        "/v3/api-docs/**",
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/webjars/**",
        "/actuator/health/**",
        "/actuator/info"
    };

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ErrorResponseWriter errorWriter) {
        ServerAuthenticationEntryPoint entryPoint = (exchange, ex) -> {
            exchange.getResponse().getHeaders().set("WWW-Authenticate", "Bearer");
            return ex instanceof InvalidBearerTokenException
                    ? errorWriter.write(
                            exchange, HttpStatus.UNAUTHORIZED, ErrorCodes.INVALID_TOKEN, "Invalid or expired token")
                    : errorWriter.write(
                            exchange, HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHORIZED, "Authentication required");
        };
        ServerAccessDeniedHandler accessDeniedHandler = (exchange, ex) ->
                errorWriter.write(exchange, HttpStatus.FORBIDDEN, ErrorCodes.ACCESS_DENIED, "Access denied");

        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(PUBLIC_PATHS)
                        .permitAll()
                        // служебные эндпойнты самого Gateway, кроме health/info, закрыты всегда
                        .pathMatchers("/actuator/**")
                        .denyAll()
                        .anyExchange()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(
                                new ReactiveJwtAuthenticationConverterAdapter(JwtSupport.authenticationConverter())))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    /**
     * URL, отклонённые firewall ({@code //}, {@code /./}, {@code ;}, закодированные разделители): 400 в формате
     * ErrorDto.
     */
    @Bean
    public ServerExchangeRejectedHandler exchangeRejectedHandler(ErrorResponseWriter errorWriter) {
        return (exchange, ex) -> errorWriter.write(
                exchange, HttpStatus.BAD_REQUEST, ErrorCodes.MALFORMED_REQUEST, "The request URL is not allowed");
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(JwtProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(properties.secretKey())
                .macAlgorithm(JwtSupport.ALGORITHM)
                .build();
        decoder.setJwtValidator(JwtSupport.validator(properties));
        return decoder;
    }
}
