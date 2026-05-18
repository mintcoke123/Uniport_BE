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

import java.time.Instant;

/**
 * 매칭방 엔티티. 방 만들기 시 저장, 목록 조회 시 반환.
 */
@Entity
@Table(name = "matching_rooms")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class MatchingRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private int capacity = 3;

    @Column(nullable = false)
    @Builder.Default
    private int memberCount = 0;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "waiting";  // waiting, started

    /** PUBLIC(목록 노출, roomId join 가능) | PRIVATE(목록 비노출, 초대코드로만 입장) */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String visibility = "PUBLIC";

    @Column(name = "match_type", length = 20)
    private String matchType;

    @Column(name = "market_type", length = 20)
    private String marketType;

    /** 참가 중인 대회 ID. null이면 일반 모의투자 방. */
    @Column(name = "competition_id")
    private Long competitionId;

    /** 6~8자리 Base62 랜덤. UNIQUE. 비공개 방 입장용. */
    @Column(name = "invite_code", unique = true, length = 8)
    private String inviteCode;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    public static MatchingRoom create(String name) {
        return create(name, 3);
    }

    /** capacity 1=개인, 3=팀 등 */
    public static MatchingRoom create(String name, int capacity) {
        int cap = capacity <= 0 ? 3 : (capacity > 10 ? 10 : capacity);
        return MatchingRoom.builder()
                .name(name != null && !name.isBlank() ? name : "새 매칭방")
                .capacity(cap)
                .memberCount(0)
                .status("waiting")
                .visibility("PUBLIC")
                .createdAt(Instant.now())
                .build();
    }
}
