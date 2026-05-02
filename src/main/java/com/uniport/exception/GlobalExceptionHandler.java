package com.uniport.exception;

import com.uniport.dto.ErrorResponseDTO;
import com.uniport.service.KisApiService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.UUID;

/**
 * ?꾩뿭 ?덉쇅 泥섎━湲? 紐낆꽭 ?명솚: ?ㅽ뙣 ??{ success: false, message }. H2 肄섏넄? ?쒖쇅(?덉쇅 ?촷hrow).
 * KIS 誘몄꽕????503 + { code, message, configured: false }.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException ex, HttpServletRequest request) {
        String uri = request != null ? request.getRequestURI() : "";
        int status = ex.getStatus().value();
        String errorCode = ApiErrorCodeResolver.resolve(ex.getStatus(), ex.getErrorCode());
        String requestId = UUID.randomUUID().toString();

        if (status >= 500) {
            log.error("requestId={} status={} errorCode={} uri={} message={}",
                    requestId, status, errorCode, uri, ex.getMessage());
        } else {
            log.warn("requestId={} status={} errorCode={} uri={} message={}",
                    requestId, status, errorCode, uri, ex.getMessage());
        }

        if (KisApiService.ERROR_CODE_KIS_NOT_CONFIGURED.equals(errorCode)) {
            Map<String, Object> body = Map.of(
                    "code", KisApiService.ERROR_CODE_KIS_NOT_CONFIGURED,
                    "errorCode", KisApiService.ERROR_CODE_KIS_NOT_CONFIGURED,
                    "message", "KIS API媛 ?ㅼ젙?섏? ?딆븯?듬땲??",
                    "configured", false
            );
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }

        ErrorResponseDTO specBody = new ErrorResponseDTO(
                false,
                ex.getMessage(),
                errorCode,
                status >= 500 ? requestId : null
        );
        return ResponseEntity.status(ex.getStatus()).body(specBody);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception ex, HttpServletRequest request) {
        String uri = request != null ? request.getRequestURI() : "";
        String requestId = UUID.randomUUID().toString();

        // H2 肄섏넄? ?덉쇅 泥섎━?먯꽌 ?쒖쇅 (肄섏넄???먯껜 ?묐떟 泥섎━)
        if (uri != null && uri.startsWith("/h2-console")) {
            throw new RuntimeException(ex);
        }

        log.error("requestId={} uri={} exception={} message={}",
                requestId, uri, ex.getClass().getSimpleName(), ex.getMessage(), ex);

        String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        ErrorResponseDTO specBody = new ErrorResponseDTO(
                false,
                "Internal server error: " + message,
                ApiErrorCodeResolver.INTERNAL_SERVER_ERROR,
                requestId
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(specBody);
    }
}
