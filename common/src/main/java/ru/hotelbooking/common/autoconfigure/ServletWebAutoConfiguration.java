package ru.hotelbooking.common.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import ru.hotelbooking.common.web.CorrelationMdcFilter;
import ru.hotelbooking.common.web.CurrentUserArgumentResolver;
import ru.hotelbooking.common.web.ErrorResponseWriter;
import ru.hotelbooking.common.web.GlobalExceptionHandler;

/**
 * Обработка ошибок, корреляция и получение текущего пользователя для servlet-сервисов.
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ServletWebAutoConfiguration implements WebMvcConfigurer {

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    public ErrorResponseWriter errorResponseWriter(ObjectMapper objectMapper) {
        return new ErrorResponseWriter(objectMapper);
    }

    @Bean
    public FilterRegistrationBean<CorrelationMdcFilter> correlationMdcFilter() {
        FilterRegistrationBean<CorrelationMdcFilter> registration =
                new FilterRegistrationBean<>(new CorrelationMdcFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }
}
