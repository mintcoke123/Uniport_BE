package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 紐낆꽭 짠1 ?? ?ㅽ뙣 ???묐떟. success: false, message.
 * requestId??5xx/誘몄쿂由??덉쇅 ??濡쒓렇 ?곴??⑹쑝濡??좏깮 ?ы븿.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponseDTO {

    private boolean success = false;
    private String message;
    private String errorCode;
    /** ?붿껌 異붿쟻??ID (5xx쨌誘몄쿂由??덉쇅 ??濡쒓렇? ?묐떟???ы븿) */
    private String requestId;

    /** requestId ?놁씠 ?ъ슜 ??(湲곗〈 ?명솚) */
    public ErrorResponseDTO(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.errorCode = null;
        this.requestId = null;
    }

    public ErrorResponseDTO(boolean success, String message, String errorCode) {
        this.success = success;
        this.message = message;
        this.errorCode = errorCode;
        this.requestId = null;
    }
}
