package com.uniport.repository;

import com.uniport.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByRoomIdOrderByCreatedAtAsc(Long roomId);

    java.util.Optional<ChatMessage> findByRoomIdAndVoteId(Long roomId, Long voteId);

    java.util.Optional<ChatMessage> findTopByRoomIdOrderByCreatedAtDesc(Long roomId);
}
