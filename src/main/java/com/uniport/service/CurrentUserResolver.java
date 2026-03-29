package com.uniport.service;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {

    private final AuthService authService;

    public CurrentUserResolver(AuthService authService) {
        this.authService = authService;
    }

    public User resolveNullable(FirebaseAuthenticatedUser principal, String authorization) {
        if (principal != null && principal.getUser() != null) {
            return principal.getUser();
        }
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        return authService.getUserFromTokenOrNull(authorization);
    }

    public User resolveRequired(FirebaseAuthenticatedUser principal, String authorization) {
        User user = resolveNullable(principal, authorization);
        if (user == null) {
            throw new ApiException("Authentication is required", HttpStatus.UNAUTHORIZED);
        }
        return user;
    }
}
