package com.uniport.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.dto.ErrorResponseDTO;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.exception.ApiErrorCodeResolver;
import com.uniport.repository.UserRepository;
import com.uniport.service.FirebaseAuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<String> AUTH_BYPASS_PATHS = List.of(
            "/.well-known/assetlinks.json",
            "/.well-known/apple-app-site-association",
            "/apple-app-site-association"
    );

    private final FirebaseAuthenticationService firebaseAuthenticationService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FirebaseAuthenticationFilter(FirebaseAuthenticationService firebaseAuthenticationService,
                                        JwtUtil jwtUtil,
                                        UserRepository userRepository) {
        this.firebaseAuthenticationService = firebaseAuthenticationService;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return requestUri != null && AUTH_BYPASS_PATHS.contains(requestUri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String requestUri = request.getRequestURI();
        String method = request.getMethod();
        boolean protectedRequest = requestUri != null
                && (requestUri.startsWith("/api/investment-survey/")
                || requestUri.startsWith("/api/onboarding/")
                || requestUri.startsWith("/api/surveys/")
                || requestUri.startsWith("/api/learning/")
                || requestUri.startsWith("/api/chat/")
                || requestUri.startsWith("/api/matching-rooms/")
                || requestUri.startsWith("/investment-survey/")
                || requestUri.startsWith("/surveys/")
                || requestUri.startsWith("/learning/")
                || requestUri.startsWith("/api/custom-etfs/")
                || requestUri.startsWith("/api/etf-analysis-reports/")
                || (requestUri.startsWith("/api/etf-discovery/") && !"GET".equalsIgnoreCase(request.getMethod()))
                || requestUri.startsWith("/api/mypage")
                || requestUri.startsWith("/api/points/")
                || requestUri.startsWith("/api/shop/redemptions/")
                || requestUri.startsWith("/api/friends/")
                || (requestUri.startsWith("/api/community/")
                && !"GET".equalsIgnoreCase(request.getMethod())));

        if (authorization == null || authorization.isBlank()) {
            if (protectedRequest) {
                log.warn("[firebase-auth-filter] Missing Authorization header: method={}, uri={}", method, requestUri);
            }
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorization.startsWith(BEARER_PREFIX)) {
            if (protectedRequest) {
                log.warn("[firebase-auth-filter] Invalid Authorization scheme: method={}, uri={}, headerPrefix={}",
                        method, requestUri, summarizeAuthorizationHeader(authorization));
                writeUnauthorized(response, "Authorization header must use Bearer scheme");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        String idToken = authorization.substring(BEARER_PREFIX.length()).trim();
        if (idToken.isBlank()) {
            log.warn("[firebase-auth-filter] Blank Firebase token: method={}, uri={}", method, requestUri);
            writeUnauthorized(response, "Firebase ID token is required");
            return;
        }

        try {
            FirebaseAuthenticatedUser principal = authenticatePrincipal(idToken);
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
            log.warn("[firebase-auth-filter] Unauthorized token: method={}, uri={}, message={}",
                    method, requestUri, ex.getMessage());
            writeUnauthorized(response, ex.getMessage(), ApiErrorCodeResolver.AUTH_TOKEN_REQUIRED);
        } catch (ApiException ex) {
            log.error("[firebase-auth-filter] ApiException during authentication: method={}, uri={}, status={}, errorCode={}, message={}",
                    method, requestUri, ex.getStatus().value(), ex.getErrorCode(), ex.getMessage(), ex);
            writeError(
                    response,
                    ex.getStatus().value(),
                    ex.getMessage(),
                    ApiErrorCodeResolver.resolve(ex.getStatus(), ex.getErrorCode())
            );
        } catch (Exception ex) {
            log.error("[firebase-auth-filter] Unexpected authentication failure: method={}, uri={}, exception={}, message={}",
                    method, requestUri, ex.getClass().getName(), ex.getMessage(), ex);
            writeError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Internal server error",
                    ApiErrorCodeResolver.INTERNAL_SERVER_ERROR
            );
        } finally {
            if (!response.isCommitted()) {
                SecurityContextHolder.clearContext();
            }
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        writeUnauthorized(response, message, ApiErrorCodeResolver.AUTH_TOKEN_REQUIRED);
    }

    private FirebaseAuthenticatedUser authenticatePrincipal(String idToken) {
        try {
            return firebaseAuthenticationService.authenticate(idToken);
        } catch (Exception ignored) {
            Long userId = jwtUtil.getUserIdFromToken(idToken);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ApiException("User not found", org.springframework.http.HttpStatus.UNAUTHORIZED));
            return new FirebaseAuthenticatedUser(user, user.getFirebaseUid(), user.getEmail());
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message, String errorCode) throws IOException {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, message, errorCode);
    }

    private void writeError(HttpServletResponse response, int status, String message, String errorCode) throws IOException {
        response.resetBuffer();
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ErrorResponseDTO(false, message, errorCode));
    }

    private String summarizeAuthorizationHeader(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return "<blank>";
        }
        int end = Math.min(authorization.length(), 16);
        return authorization.substring(0, end);
    }
}
