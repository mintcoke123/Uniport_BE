package com.uniport.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;
    private final FirebaseAuthenticationFilter firebaseAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    public SecurityConfig(CorsConfigurationSource corsConfigurationSource,
                          FirebaseAuthenticationFilter firebaseAuthenticationFilter,
                          RestAuthenticationEntryPoint restAuthenticationEntryPoint,
                          RestAccessDeniedHandler restAccessDeniedHandler) {
        this.corsConfigurationSource = corsConfigurationSource;
        this.firebaseAuthenticationFilter = firebaseAuthenticationFilter;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.restAccessDeniedHandler = restAccessDeniedHandler;
    }

    /** 기본 사용자 생성 방지. 인증은 JWT(Authorization 헤더)만 사용. "Using generated security password" 로그 미출력. */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new org.springframework.security.core.userdetails.UsernameNotFoundException("No default user; use JWT.");
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(c -> c.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/favicon.ico").permitAll()
                        .requestMatchers("/", "/api/auth/**", "/api/me/**", "/api/market/**", "/api/stocks/**", "/api/ohlcv", "/api/trades", "/api/competitions/**", "/api/ranking/**", "/api/groups/**", "/api/matching-rooms/**", "/api/admin/**", "/api/config/**", "/api/health", "/api/home/group-insights", "/api/etf-discovery/**", "/api/shop/items", "/auth/**", "/stock/**", "/market/**", "/trade/**", "/h2-console/**", "/error", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/community/**").permitAll()
                        .requestMatchers("/groups/*/chat", "/prices").permitAll()
                        .requestMatchers("/investment-survey/**", "/surveys/**", "/learning/**", "/api/custom-etfs/**", "/api/etf-analysis-reports/**", "/api/community/**", "/api/mypage", "/api/points/**", "/api/shop/redemptions/**", "/api/friends/**").authenticated()
                        .anyRequest().authenticated())
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .addFilterBefore(firebaseAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
