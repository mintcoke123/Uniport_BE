package com.uniport.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Configuration
public class OpenApiConfig {

    private static final String FIREBASE_BEARER_AUTH = "firebaseBearerAuth";

    @Bean
    public OpenAPI uniportOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Uniport API")
                        .version("v1")
                        .description("Uniport backend API documentation"))
                .components(new Components()
                        .addSecuritySchemes(FIREBASE_BEARER_AUTH,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste the Firebase ID token. Swagger UI will send the Bearer prefix automatically.")));
    }

    @Bean
    public OperationCustomizer firebaseBearerOperationCustomizer() {
        return (operation, handlerMethod) -> {
            String path = resolvePath(handlerMethod);
            Set<String> httpMethods = resolveHttpMethods(handlerMethod);

            boolean requiresFirebaseAuth = httpMethods.stream()
                    .anyMatch(httpMethod -> isFirebaseProtected(path, httpMethod));

            if (!requiresFirebaseAuth) {
                return operation;
            }

            if (operation.getSecurity() == null) {
                operation.setSecurity(new ArrayList<>());
            }

            boolean alreadyPresent = operation.getSecurity().stream()
                    .anyMatch(requirement -> requirement.containsKey(FIREBASE_BEARER_AUTH));

            if (!alreadyPresent) {
                operation.addSecurityItem(new SecurityRequirement().addList(FIREBASE_BEARER_AUTH));
            }
            return operation;
        };
    }

    private boolean isFirebaseProtected(String path, String httpMethod) {
        if (!StringUtils.hasText(path)) {
            return false;
        }

        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        String normalizedMethod = httpMethod == null ? "" : httpMethod.toUpperCase(Locale.ROOT);

        return normalizedPath.startsWith("/api/onboarding/")
                || normalizedPath.startsWith("/api/chat/")
                || normalizedPath.startsWith("/api/matching-rooms/")
                || normalizedPath.startsWith("/api/custom-etfs/")
                || normalizedPath.startsWith("/api/etf-analysis-reports/")
                || normalizedPath.startsWith("/api/mypage")
                || normalizedPath.startsWith("/api/points/")
                || normalizedPath.startsWith("/api/shop/redemptions/")
                || normalizedPath.startsWith("/api/friends/")
                || normalizedPath.startsWith("/api/me/")
                || normalizedPath.startsWith("/api/users/")
                || normalizedPath.startsWith("/api/home/summary")
                || normalizedPath.startsWith("/api/home/matching-dashboard")
                || (normalizedPath.startsWith("/api/community/") && !"GET".equals(normalizedMethod))
                || (normalizedPath.startsWith("/api/etf-discovery/") && !"GET".equals(normalizedMethod));
    }

    private String resolvePath(HandlerMethod handlerMethod) {
        String classPath = extractFirstPath(
                AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequestMapping.class));
        String methodPath = extractMethodPath(handlerMethod.getMethod());
        return joinPaths(classPath, methodPath);
    }

    private Set<String> resolveHttpMethods(HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        Set<String> methods = new LinkedHashSet<>();

        if (AnnotatedElementUtils.hasAnnotation(method, GetMapping.class)) {
            methods.add("GET");
        }
        if (AnnotatedElementUtils.hasAnnotation(method, PostMapping.class)) {
            methods.add("POST");
        }
        if (AnnotatedElementUtils.hasAnnotation(method, PutMapping.class)) {
            methods.add("PUT");
        }
        if (AnnotatedElementUtils.hasAnnotation(method, PatchMapping.class)) {
            methods.add("PATCH");
        }
        if (AnnotatedElementUtils.hasAnnotation(method, DeleteMapping.class)) {
            methods.add("DELETE");
        }

        RequestMapping requestMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (requestMapping != null && requestMapping.method().length > 0) {
            for (var requestMethod : requestMapping.method()) {
                methods.add(requestMethod.name());
            }
        }

        if (methods.isEmpty()) {
            methods.add("GET");
        }
        return methods;
    }

    private String extractMethodPath(Method method) {
        GetMapping getMapping = AnnotatedElementUtils.findMergedAnnotation(method, GetMapping.class);
        if (getMapping != null) {
            return extractFirstPath(getMapping.value(), getMapping.path());
        }

        PostMapping postMapping = AnnotatedElementUtils.findMergedAnnotation(method, PostMapping.class);
        if (postMapping != null) {
            return extractFirstPath(postMapping.value(), postMapping.path());
        }

        PutMapping putMapping = AnnotatedElementUtils.findMergedAnnotation(method, PutMapping.class);
        if (putMapping != null) {
            return extractFirstPath(putMapping.value(), putMapping.path());
        }

        PatchMapping patchMapping = AnnotatedElementUtils.findMergedAnnotation(method, PatchMapping.class);
        if (patchMapping != null) {
            return extractFirstPath(patchMapping.value(), patchMapping.path());
        }

        DeleteMapping deleteMapping = AnnotatedElementUtils.findMergedAnnotation(method, DeleteMapping.class);
        if (deleteMapping != null) {
            return extractFirstPath(deleteMapping.value(), deleteMapping.path());
        }

        RequestMapping requestMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        return extractFirstPath(requestMapping);
    }

    private String extractFirstPath(RequestMapping requestMapping) {
        if (requestMapping == null) {
            return "";
        }
        return extractFirstPath(requestMapping.value(), requestMapping.path());
    }

    private String extractFirstPath(String[] values, String[] paths) {
        if (values != null && values.length > 0 && StringUtils.hasText(values[0])) {
            return values[0];
        }
        if (paths != null && paths.length > 0 && StringUtils.hasText(paths[0])) {
            return paths[0];
        }
        return "";
    }

    private String joinPaths(String classPath, String methodPath) {
        String left = StringUtils.hasText(classPath) ? classPath.trim() : "";
        String right = StringUtils.hasText(methodPath) ? methodPath.trim() : "";

        if (!StringUtils.hasText(left)) {
            return StringUtils.hasText(right) ? right : "/";
        }
        if (!StringUtils.hasText(right)) {
            return left;
        }

        boolean leftEndsWithSlash = left.endsWith("/");
        boolean rightStartsWithSlash = right.startsWith("/");

        if (leftEndsWithSlash && rightStartsWithSlash) {
            return left + right.substring(1);
        }
        if (!leftEndsWithSlash && !rightStartsWithSlash) {
            return left + "/" + right;
        }
        return left + right;
    }
}
