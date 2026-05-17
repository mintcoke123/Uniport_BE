package com.uniport.repository;

import com.uniport.entity.MatchingRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MatchingRoomRepository extends JpaRepository<MatchingRoom, Long> {

    List<MatchingRoom> findAllByOrderByCreatedAtDesc();

    Optional<MatchingRoom> findByInviteCode(String inviteCode);

    List<MatchingRoom> findByStatusAndEndedAtLessThanEqualOrderByEndedAtAsc(String status, Instant endedAt);

    List<MatchingRoom> findByStatusAndEndedAtIsNullAndCreatedAtLessThanEqualOrderByCreatedAtAsc(String status, Instant createdAt);

    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO matching_rooms (
                id, name, capacity, member_count, status, visibility, created_at, ended_at
            ) VALUES (
                :id, :name, :capacity, :memberCount, :status, :visibility, :createdAt, :endedAt
            )
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    void insertRecoveredRoom(@Param("id") Long id,
                             @Param("name") String name,
                             @Param("capacity") int capacity,
                             @Param("memberCount") int memberCount,
                             @Param("status") String status,
                             @Param("visibility") String visibility,
                             @Param("createdAt") Instant createdAt,
                             @Param("endedAt") Instant endedAt);
}
