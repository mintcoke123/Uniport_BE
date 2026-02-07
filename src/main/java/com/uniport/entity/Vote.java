package com.uniport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 그룹(방) 투표. 매수/매도 계획 공유 시 생성.
 */
@Entity
@Table(name = "votes")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "proposer_id", nullable = false)
    private Long proposerId;

    @Column(name = "proposer_name", nullable = false, length = 100)
    private String proposerName;

    @Column(nullable = false, length = 10)
    private String type;  // 매수, 매도

    @Column(name = "stock_name", nullable = false, length = 100)
    private String stockName;

    @Column(name = "stock_code", length = 20)
    private String stockCode;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "proposed_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal proposedPrice;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "total_members", nullable = false)
    private int totalMembers;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ongoing";  // ongoing, passed, rejected, expired

    @OneToMany(mappedBy = "vote", fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<VoteParticipant> participants = new ArrayList<>();
}
