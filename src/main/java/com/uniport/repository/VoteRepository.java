package com.uniport.repository;

import com.uniport.entity.Vote;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    List<Vote> findByRoomIdOrderByCreatedAtDesc(Long roomId);

    List<Vote> findByRoomIdAndStatusOrderByCreatedAtDesc(Long roomId, String status);

    /** 스케줄러/만료 처리: status로 목록 조회 (derived query) */
    List<Vote> findByStatus(String status);

    /** 중복 체결 방지: 락 걸고 조회 (실행 직전 호출) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vote v WHERE v.id = :id")
    Optional<Vote> findByIdForUpdate(@Param("id") Long id);
}
