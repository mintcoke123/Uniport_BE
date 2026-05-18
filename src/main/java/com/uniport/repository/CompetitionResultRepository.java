package com.uniport.repository;

import com.uniport.entity.CompetitionResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompetitionResultRepository extends JpaRepository<CompetitionResult, Long> {
    boolean existsByCompetition_IdAndMatchingRoom_Id(Long competitionId, Long matchingRoomId);

    Optional<CompetitionResult> findByCompetition_IdAndMatchingRoom_Id(Long competitionId, Long matchingRoomId);

    List<CompetitionResult> findByCompetition_IdOrderByRankAsc(Long competitionId);

    List<CompetitionResult> findAllByOrderBySettledAtDesc();
}
