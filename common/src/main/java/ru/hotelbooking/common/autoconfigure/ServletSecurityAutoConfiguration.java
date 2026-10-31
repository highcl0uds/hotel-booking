package ru.hotelbooking.common.autoconfigure;

import jakarta.servlet.DispatcherType;
import java.util.stream.Stream;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import ru.hotelbooking.common.error.ErrorCodes;
import ru.hotelbooking.common.security.JwtProperties;
import ru.hotelbooking.common.security.JwtSupport;
import ru.hotelbooking.common.security.Roles;
import ru.hotelbooking.common.web.ErrorResponseWriter;
import ru.hotelbooking.common.web.JsonAccessDeniedHandler;
import ru.hotelbooking.common.web.JsonAuthenticationEntryPoint;

/**
 * Каждый servlet-сервис — OAuth2 resource server, который сам проверяет JWT (независимо от Gateway).
 * Цепочка фильтров требует только аутентификации; роли проверяются через {@code @PreAuthorize} на методах.
 */
@AutoConfiguration(
        before = {
            SecurityAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class
        })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({SecurityFilterChain.class, JwtDecoder.class})
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(ResourceServerProperties.class)
public class ServletSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        return JwtSupport.decoder(properties);
    }

    /** ADMIN может всё, что может USER. INTERNAL намеренно вне иерархии. */
    @Bean
    public static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(Roles.ADMIN)
                .implies(Roles.USER)
                .build();
    }

    @Bean
    public JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint(ErrorResponseWriter writer) {
        return new JsonAuthenticationEntryPoint(writer);
    }

    @Bean
    public JsonAccessDeniedHandler jsonAccessDeniedHandler(ErrorResponseWriter writer) {
        return new JsonAccessDeniedHandler(writer);
    }

    /**
     * URL, отклонённые firewall ({@code ;}, {@code //}, закодированные разделители, ...), получают здесь ответ 400.
     * Стандартный обработчик отправляет ошибку на {@code /error}, где она превратилась бы в вводящий в заблуждение 401.
     */
    @Bean
    @ConditionalOnMissingBean
    public RequestRejectedHandler requestRejectedHandler(ErrorResponseWriter writer) {
        return (request, response, ex) -> writer.write(
                request,
                response,
                HttpStatus.BAD_REQUEST,
                ErrorCodes.MALFORMED_REQUEST,
                "The request URL is not allowed");
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain resourceServerFilterChain(
            HttpSecurity http,
            ResourceServerProperties properties,
            JsonAuthenticationEntryPoint entryPoint,
            JsonAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        RequestMatcher[] publicPaths = Stream.concat(
                        ResourceServerProperties.INFRASTRUCTURE_PATHS.stream(), properties.publicPaths().stream())
                .map(PathPatternRequestMatcher.withDefaults()::matcher)
                .toArray(RequestMatcher[]::new);

        return http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // H2 Console профиля demo отображается во фреймах того же origin
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                // ошибка, возникшая вне Spring MVC, отображается через /error со своим статусом, а не превращается в
                // 401
                .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers(publicPaths)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                                jwt -> jwt.jwtAuthenticationConverter(JwtSupport.authenticationConverter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
                .build();
    }
}
