package com.uniport.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CORS 설정. 개발 환경에서 http://localhost:3000 (Vite 등) → http://localhost:8080 요청 허용.
 * 프로덕션에서는 app.cors.allowed-origins 로 허용 오리진을 제한하는 것을 권장.
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}")
    private String allowedOriginsConfig;

    private final Environment environment;

    public CorsConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = Arrays.stream(allowedOriginsConfig.split("\\s*,\\s*"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
        boolean isProd = Arrays.stream(environment.getActiveProfiles()).anyMatch("prod"::equals);
        if (origins.isEmpty() && !isProd) {
            origins = List.of("http://localhost:3000", "http://127.0.0.1:3000");
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
