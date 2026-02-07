package com.uniport.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 그룹(방) 채팅 메시지. DB 저장 후 나중에 들어온 사용자도 조회 가능.
 */
@Entity
@Table(name = "chat_messages", indexes = @Index(name = "idx_chat_room_created", columnList = "room_id, created_at"))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_nickname", nullable = false, length = 100)
    private String userNickname;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static ChatMessage of(Long roomId, Long userId, String userNickname, String message) {
        return ChatMessage.builder()
                .roomId(roomId)
                .userId(userId)
                .userNickname(userNickname != null ? userNickname : "")
                .message(message != null ? message : "")
                .createdAt(Instant.now())
                .build();
    }
}
