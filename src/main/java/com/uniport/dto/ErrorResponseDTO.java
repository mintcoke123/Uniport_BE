package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 명세 §1 등: 실패 시 응답. success: false, message.
 * requestId는 5xx/미처리 예외 시 로그 상관용으로 선택 포함.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponseDTO {

    private boolean success = false;
    private String message;
    /** 요청 추적용 ID (5xx·미처리 예외 시 로그와 응답에 포함) */
    private String requestId;

    /** requestId 없이 사용 시 (기존 호환) */
    public ErrorResponseDTO(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.requestId = null;
    }
}
