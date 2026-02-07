package com.uniport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 사용자 엔티티. 명세 §1: 로그인은 email, 응답에는 nickname·자산·teamId 등.
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인·회원가입 식별자 (명세 §1) */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    /** 표시명 (명세 §1) */
    @Column(nullable = false, length = 100)
    private String nickname;

    /** 총 자산 (명세 §1) */
    @Column(precision = 19, scale = 4)
    private BigDecimal totalAssets;

    /** 투자 원금 (명세 §1) */
    @Column(precision = 19, scale = 4)
    private BigDecimal investmentAmount;

    /** 평가 손익 (명세 §1) */
    @Column(precision = 19, scale = 4)
    private BigDecimal profitLoss;

    /** 수익률 % (명세 §1) */
    @Column(precision = 10, scale = 4)
    private BigDecimal profitLossRate;

    /** 팀 소속 ID (null = 팀 미소속, 명세 §1) */
    @Column(length = 50)
    private String teamId;

    /** 역할: user | admin (명세 §1, §10. Admin 페이지 접근용) */
    @Column(length = 20)
    private String role;

    /** 하위 호환: username (내부적으로 email과 동일하게 사용 가능) */
    @Column(unique = true, length = 255)
    private String username;
}
