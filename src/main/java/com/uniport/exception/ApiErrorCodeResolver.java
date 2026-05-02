package com.uniport.exception;

import org.springframework.http.HttpStatus;

public final class ApiErrorCodeResolver {

    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
    public static final String AUTH_TOKEN_REQUIRED = "AUTH_TOKEN_REQUIRED";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String FORBIDDEN_IN_NON_DEV_PROFILE = "FORBIDDEN_IN_NON_DEV_PROFILE";
    public static final String CHAT_ROOM_AUTH_REQUIRED = "CHAT_ROOM_AUTH_REQUIRED";
    public static final String CHAT_ROOM_ACCESS_DENIED = "CHAT_ROOM_ACCESS_DENIED";
    public static final String GROUP_VOTE_AUTH_REQUIRED = "GROUP_VOTE_AUTH_REQUIRED";

    private ApiErrorCodeResolver() {
    }

    public static String resolve(HttpStatus status, String errorCode) {
        if (errorCode != null && !errorCode.isBlank()) {
            return errorCode;
        }
        return defaultForStatus(status);
    }

    public static String defaultForStatus(HttpStatus status) {
        if (status == null) {
            return INTERNAL_SERVER_ERROR;
        }

        return switch (status) {
            case BAD_REQUEST -> "BAD_REQUEST";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            case UNPROCESSABLE_ENTITY -> "UNPROCESSABLE_ENTITY";
            case TOO_MANY_REQUESTS -> "TOO_MANY_REQUESTS";
            case SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE";
            case INTERNAL_SERVER_ERROR -> INTERNAL_SERVER_ERROR;
            default -> "HTTP_" + status.value();
        };
    }
}
