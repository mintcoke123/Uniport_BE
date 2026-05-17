package com.uniport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "festival_trading_sessions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class FestivalTradingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String participantName;

    @Column(nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false, length = 120)
    private String department;

    @Column(nullable = false, length = 20)
    private String studentId;

    @Column(nullable = false, length = 30)
    private String phoneNumber;

    @Column(nullable = false)
    private boolean privacyAgreed;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal startCash;

    @Column(precision = 19, scale = 4)
    private BigDecimal endCash;

    @Column(precision = 19, scale = 4)
    private BigDecimal endPortfolioValue;

    @Column(precision = 19, scale = 4)
    private BigDecimal endTotalValue;

    @Column(precision = 10, scale = 4)
    private BigDecimal returnRate;

    @Column(length = 120)
    private String mainStockName;

    @Column(length = 80)
    private String basePrize;

    @Column(length = 80)
    private String finalPrize;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime endedAt;

    @Column(nullable = false)
    private Integer tradeCount;

    @Column(nullable = false)
    private Integer unfilledOrderCount;

    @Lob
    @Column
    private String holdingsSnapshotJson;

    @Lob
    @Column
    private String tradeHistoryJson;
}
