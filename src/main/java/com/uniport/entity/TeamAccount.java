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
 * 팀(매칭방) 단위 투자 계정. 팀 잔액 보관.
 */
@Entity
@Table(name = "team_accounts")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class TeamAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 매칭방(팀) ID. groupId와 동일. */
    @Column(nullable = false, unique = true)
    private Long teamId;

    /** 보유 현금 (매도 시 증가, 매수 시 차감) */
    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal cashBalance = BigDecimal.ZERO;
}
