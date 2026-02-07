package com.uniport.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(CorsConfigurationSource corsConfigurationSource) {
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(c -> c.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/favicon.ico").permitAll()
                        .requestMatchers("/", "/api/auth/**", "/api/me/**", "/api/market/**", "/api/stocks/**", "/api/trades", "/api/competitions/**", "/api/ranking/**", "/api/groups/**", "/api/matching-rooms/**", "/api/admin/**", "/api/config/**", "/auth/**", "/stock/**", "/market/**", "/trade/**", "/h2-console/**", "/error").permitAll()
                        .requestMatchers("/groups/*/chat").permitAll()
                        .anyRequest().authenticated())
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .build();
    }
}
