package com.safeops.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI safeOpsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SafeOps AI Backend API")
                        .description("Enterprise backend for SafeOps AI industrial safety platform. "
                                + "Provides JWT-secured REST endpoints for safety analysis, sensor monitoring, "
                                + "permit management, and AI-powered compliance checking.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("SafeOps AI Team")
                                .email("team@safeops.ai")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .bearerFormat("JWT")
                                        .scheme("bearer")));
    }
}
