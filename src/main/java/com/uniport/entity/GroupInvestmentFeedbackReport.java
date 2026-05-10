package com.uniport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "group_investment_feedback_reports",
        uniqueConstraints = @UniqueConstraint(name = "uk_group_feedback_report_session", columnNames = "session_id")
)
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class GroupInvestmentFeedbackReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "initial_capital", nullable = false, precision = 19, scale = 4)
    private BigDecimal initialCapital;

    @Column(name = "final_equity", nullable = false, precision = 19, scale = 4)
    private BigDecimal finalEquity;

    @Column(name = "profit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal profitAmount;

    @Column(name = "return_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal returnRate;

    @Lob
    @Column(name = "best_trade_json")
    private String bestTradeJson;

    @Lob
    @Column(name = "worst_trade_json")
    private String worstTradeJson;

    @Column(name = "ai_comment", nullable = false, length = 500)
    private String aiComment;

    @Column(name = "ai_source", nullable = false, length = 20)
    private String aiSource;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "published_message_id")
    private Long publishedMessageId;

    @Column(name = "point_settlement_status", nullable = false, length = 20)
    @Builder.Default
    private String pointSettlementStatus = "PENDING";

    @Column(name = "point_settled_at")
    private Instant pointSettledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (generatedAt == null) {
            generatedAt = now;
        }
        if (pointSettlementStatus == null || pointSettlementStatus.isBlank()) {
            pointSettlementStatus = "PENDING";
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
