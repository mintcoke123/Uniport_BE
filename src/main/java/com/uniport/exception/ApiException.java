package com.uniport.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * API 鍮꾩쫰?덉뒪/?대씪?댁뼵???ㅻ쪟??而ㅼ뒪? ?덉쇅.
 * HTTP ?곹깭 肄붾뱶? ?좏깮???먮윭 肄붾뱶瑜??댁븘 ?쇨????ㅻ쪟 ?묐떟???ъ슜?⑸땲??
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
        this.errorCode = ApiErrorCodeResolver.defaultForStatus(status);
    }

    public ApiException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = ApiErrorCodeResolver.resolve(status, errorCode);
    }
}
