package com.uniport.exception;

import com.uniport.dto.ErrorResponseDTO;
import com.uniport.service.KisApiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 전역 예외 처리기. 명세 호환: 실패 시 { success: false, message }. H2 콘솔은 제외(예외 재throw).
 * KIS 미설정 시 503 + { code, message, configured: false }.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException ex) {
        if (KisApiService.ERROR_CODE_KIS_NOT_CONFIGURED.equals(ex.getErrorCode())) {
            Map<String, Object> body = Map.of(
                    "code", KisApiService.ERROR_CODE_KIS_NOT_CONFIGURED,
                    "message", "KIS API가 설정되지 않았습니다.",
                    "configured", false
            );
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
        ErrorResponseDTO specBody = new ErrorResponseDTO(false, ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(specBody);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception ex, HttpServletRequest request) {
        String uri = request != null ? request.getRequestURI() : "";

        // H2 콘솔은 예외 처리에서 제외 (콘솔이 자체 응답 처리)
        if (uri != null && uri.startsWith("/h2-console")) {
            throw new RuntimeException(ex);
        }

        log.error("Unhandled exception for {}: {}", uri, ex.getMessage(), ex);

        ErrorResponseDTO specBody = new ErrorResponseDTO(false, "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(specBody);
    }
}
