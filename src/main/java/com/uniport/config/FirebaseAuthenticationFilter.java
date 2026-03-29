package com.uniport.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.dto.ErrorResponseDTO;
import com.uniport.exception.ApiException;
import com.uniport.service.FirebaseAuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final FirebaseAuthenticationService firebaseAuthenticationService;
    private final ObjectMapper objectMapper;

    public FirebaseAuthenticationFilter(FirebaseAuthenticationService firebaseAuthenticationService,
                                        ObjectMapper objectMapper) {
        this.firebaseAuthenticationService = firebaseAuthenticationService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean protectedRequest = request.getRequestURI() != null
                && (request.getRequestURI().startsWith("/investment-survey/")
                || request.getRequestURI().startsWith("/surveys/")
                || request.getRequestURI().startsWith("/learning/")
                || request.getRequestURI().startsWith("/api/custom-etfs/")
                || request.getRequestURI().startsWith("/api/etf-analysis-reports/")
                || request.getRequestURI().startsWith("/api/mypage")
                || request.getRequestURI().startsWith("/api/points/")
                || request.getRequestURI().startsWith("/api/shop/redemptions/")
                || request.getRequestURI().startsWith("/api/friends/")
                || (request.getRequestURI().startsWith("/api/community/")
                && !"GET".equalsIgnoreCase(request.getMethod())));

        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorization.startsWith(BEARER_PREFIX)) {
            if (protectedRequest) {
                writeUnauthorized(response, "Authorization header must use Bearer scheme");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        String idToken = authorization.substring(BEARER_PREFIX.length()).trim();
        if (idToken.isBlank()) {
            writeUnauthorized(response, "Firebase ID token is required");
            return;
        }

        try {
            FirebaseAuthenticatedUser principal = firebaseAuthenticationService.authenticate(idToken);
            String role = principal.getUser().getRole() != null ? principal.getUser().getRole().toUpperCase() : "USER";
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (IllegalArgumentException ex) {
            writeUnauthorized(response, ex.getMessage());
        } catch (ApiException ex) {
            writeError(response, ex.getStatus().value(), ex.getMessage());
        } catch (Exception ex) {
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
        } finally {
            if (!response.isCommitted()) {
                SecurityContextHolder.clearContext();
            }
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, message);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.resetBuffer();
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ErrorResponseDTO(false, message));
    }
}
