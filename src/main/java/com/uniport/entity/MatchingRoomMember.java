package com.uniport.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 매칭방 참가자. (방, 사용자) 쌍은 유일 — 같은 멤버가 같은 방에 중복 참여 불가.
 */
@Entity
@Table(name = "matching_room_members", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"matching_room_id", "user_id"})
})
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class MatchingRoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matching_room_id", nullable = false)
    private MatchingRoom matchingRoom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    public static MatchingRoomMember of(MatchingRoom room, User user) {
        return MatchingRoomMember.builder()
                .matchingRoom(room)
                .user(user)
                .joinedAt(Instant.now())
                .build();
    }
}
