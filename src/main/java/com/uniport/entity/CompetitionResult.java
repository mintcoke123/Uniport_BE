package com.uniport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "competition_results", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"competition_id", "matching_room_id"})
})
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class CompetitionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "competition_id", nullable = false)
    private Competition competition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matching_room_id", nullable = false)
    private MatchingRoom matchingRoom;

    @Column(nullable = false)
    private Integer rank;

    @Column(name = "team_name", nullable = false, length = 200)
    private String teamName;

    @Column(name = "total_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalValue;

    @Column(name = "investment_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal investmentAmount;

    @Column(name = "profit_loss", nullable = false, precision = 19, scale = 4)
    private BigDecimal profitLoss;

    @Column(name = "profit_loss_percentage", nullable = false, precision = 12, scale = 4)
    private BigDecimal profitLossPercentage;

    @Column(name = "reward_point", nullable = false)
    private Integer rewardPoint;

    @Column(name = "settled_at", nullable = false)
    private Instant settledAt;
}
