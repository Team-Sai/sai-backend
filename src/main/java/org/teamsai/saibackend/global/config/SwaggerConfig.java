package org.teamsai.saibackend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String JWT_SCHEME_NAME = "JWT";

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        SecurityRequirement securityRequirement =
                new SecurityRequirement().addList(JWT_SCHEME_NAME);

        return new OpenAPI()
                .info(apiInfo())
                .components(new Components()
                        .addSecuritySchemes(
                                JWT_SCHEME_NAME,
                                securityScheme
                        ))
                .addSecurityItem(securityRequirement);
    }

    private Info apiInfo() {
        return new Info()
                .title("사이원장 API")
                .description("사이원장 서비스 API 명세")
                .version("mvp");
    }
}