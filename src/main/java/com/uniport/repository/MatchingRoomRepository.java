package com.uniport.repository;

import com.uniport.entity.MatchingRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MatchingRoomRepository extends JpaRepository<MatchingRoom, Long> {

    List<MatchingRoom> findAllByOrderByCreatedAtDesc();

    Optional<MatchingRoom> findByInviteCode(String inviteCode);

    List<MatchingRoom> findByStatusAndEndedAtLessThanEqualOrderByEndedAtAsc(String status, Instant endedAt);

    List<MatchingRoom> findByStatusAndEndedAtIsNullAndCreatedAtLessThanEqualOrderByCreatedAtAsc(String status, Instant createdAt);
}
