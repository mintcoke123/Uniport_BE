package com.uniport.repository;

import com.uniport.entity.VoteParticipant;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteParticipantRepository extends JpaRepository<VoteParticipant, Long> {

    List<VoteParticipant> findByVote_IdOrderById(Long voteId);

    Optional<VoteParticipant> findByVote_IdAndUserId(Long voteId, Long userId);

    boolean existsByVote_IdAndUserId(Long voteId, Long userId);
}
