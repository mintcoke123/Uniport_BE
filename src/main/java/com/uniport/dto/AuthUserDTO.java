package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 명세 §1: 로그인/회원가입 응답의 user 객체.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthUserDTO {

    private String id;           // 명세: string
    private String email;
    private String nickname;
    private BigDecimal totalAssets;
    private BigDecimal investmentAmount;
    private BigDecimal profitLoss;
    private BigDecimal profitLossRate;
    private String teamId;      // null = 팀 미소속
    private String role;        // "user" | "admin" (명세 §1, §10)
}
