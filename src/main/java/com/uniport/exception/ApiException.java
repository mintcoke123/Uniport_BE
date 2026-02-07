package com.uniport.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * API 비즈니스/클라이언트 오류용 커스텀 예외.
 * HTTP 상태 코드와 선택적 에러 코드를 담아 일관된 오류 응답에 사용합니다.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
        this.errorCode = null;
    }

    public ApiException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
