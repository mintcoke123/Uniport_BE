package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 명세 §1: 로그인/회원가입 응답. success, message, user.
 * 로그인 시 클라이언트 인증용 token도 포함 (명세: 토큰 또는 세션 쿠키).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponseDTO {

    private boolean success;
    private String message;
    private AuthUserDTO user;
    private String token;        // 로그인 시에만 설정, Authorization 헤더에 사용
}
