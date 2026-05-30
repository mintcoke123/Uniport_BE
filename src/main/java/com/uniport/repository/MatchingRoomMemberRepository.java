package com.uniport.repository;

import com.uniport.entity.MatchingRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchingRoomMemberRepository extends JpaRepository<MatchingRoomMember, Long> {

    boolean existsByMatchingRoomIdAndUserId(Long matchingRoomId, Long userId);

    Optional<MatchingRoomMember> findByMatchingRoomIdAndUserId(Long matchingRoomId, Long userId);

    /** 내가 참가 중인 방 목록 (matchingRoom JOIN FETCH로 LazyInitializationException 방지). */
    @Query("SELECT m FROM MatchingRoomMember m JOIN FETCH m.matchingRoom WHERE m.user.id = :userId ORDER BY m.joinedAt DESC")
    List<MatchingRoomMember> findByUserIdOrderByJoinedAtDesc(@Param("userId") Long userId);

    /** 내가 참가 중인 활성 방 목록. 종료된 방은 새 매칭 가능 여부에 포함하지 않는다. */
    @Query("""
            SELECT m
            FROM MatchingRoomMember m
            JOIN FETCH m.matchingRoom
            WHERE m.user.id = :userId
              AND LOWER(m.matchingRoom.status) <> 'ended'
            ORDER BY m.joinedAt DESC
            """)
    List<MatchingRoomMember> findActiveByUserIdOrderByJoinedAtDesc(@Param("userId") Long userId);

    @Query("SELECT m FROM MatchingRoomMember m JOIN FETCH m.user WHERE m.matchingRoom.id = :roomId ORDER BY m.joinedAt ASC")
    List<MatchingRoomMember> findByMatchingRoomIdWithUser(@Param("roomId") Long roomId);

    List<MatchingRoomMember> findByMatchingRoomIdOrderByJoinedAtAsc(Long matchingRoomId);

    void deleteByMatchingRoomIdAndUserId(Long matchingRoomId, Long userId);

    void deleteByMatchingRoom_Id(Long matchingRoomId);

    long countByMatchingRoomId(Long matchingRoomId);

    /** 해당 사용자가 status가 "started"인 방에 참가 중인지 (모의투자 시작 여부). */
    boolean existsByUserIdAndMatchingRoom_Status(Long userId, String status);

    boolean existsByUserIdAndMatchingRoom_CompetitionIdAndMatchingRoom_Status(
            Long userId,
            Long competitionId,
            String status
    );

    boolean existsByUserIdAndMatchingRoom_CompetitionId(Long userId, Long competitionId);

    /** 유저 삭제 시 FK 제약을 위해 해당 유저의 매칭방 참가 기록 삭제 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM MatchingRoomMember m WHERE m.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
