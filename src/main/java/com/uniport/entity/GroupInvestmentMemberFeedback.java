package com.uniport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "group_investment_member_feedbacks")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class GroupInvestmentMemberFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private GroupInvestmentFeedbackReport report;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 100)
    private String nickname;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "representative_decision", nullable = false, length = 100)
    private String representativeDecision;

    @Column(nullable = false, length = 20)
    private String level;

    @Column(name = "contribution_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal contributionAmount;

    @Column(name = "contribution_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal contributionRate;

    @Column(name = "participated_decision_count", nullable = false)
    @Builder.Default
    private int participatedDecisionCount = 0;

    @Column(name = "total_decision_count", nullable = false)
    @Builder.Default
    private int totalDecisionCount = 0;

    @Column(name = "participation_rate", nullable = false, precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal participationRate = BigDecimal.ZERO;

    @Column(name = "settled_point", nullable = false)
    @Builder.Default
    private Integer settledPoint = 0;

    @Column(name = "settled_exp", nullable = false)
    @Builder.Default
    private Integer settledExp = 0;

    @Column(name = "point_transaction_id", length = 100)
    private String pointTransactionId;

    @Column(name = "point_settlement_status", nullable = false, length = 20)
    @Builder.Default
    private String pointSettlementStatus = "PENDING";

    @Column(name = "point_settled_at")
    private Instant pointSettledAt;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (participationRate == null) {
            participationRate = BigDecimal.ZERO;
        }
        if (settledPoint == null) {
            settledPoint = 0;
        }
        if (settledExp == null) {
            settledExp = 0;
        }
        if (pointSettlementStatus == null || pointSettlementStatus.isBlank()) {
            pointSettlementStatus = "PENDING";
        }
    }
}
