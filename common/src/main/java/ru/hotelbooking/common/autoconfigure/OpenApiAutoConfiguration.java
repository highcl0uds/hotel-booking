package ru.hotelbooking.common.autoconfigure;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import ru.hotelbooking.common.security.CurrentUser;

/**
 * Описание OpenAPI со схемой JWT bearer, чтобы Swagger UI мог вызывать защищённые эндпойнты.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({OpenAPI.class, SpringDocUtils.class})
public class OpenApiAutoConfiguration {

    private static final String BEARER_SCHEME = "bearer-jwt";

    static {
        // параметры @CurrentUser берутся из токена, они не входят в HTTP-контракт
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI openApi(
            @Value("${spring.application.name}") String applicationName,
            @Value("${app.openapi.description:}") String description) {
        return new OpenAPI()
                .info(new Info().title(applicationName).description(description).version("v1"))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
