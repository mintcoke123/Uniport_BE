package com.uniport.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI uniportOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Uniport API")
                        .version("v1")
                        .description("Uniport backend API documentation"))
                .components(new Components()
                        .addSecuritySchemes("firebaseBearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Firebase ID Token")));
    }
}
