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

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static MatchingRoom create(String name) {
        return MatchingRoom.builder()
                .name(name != null && !name.isBlank() ? name : "새 매칭방")
                .capacity(3)
                .memberCount(0)
                .status("waiting")
                .createdAt(Instant.now())
                .build();
    }
}
