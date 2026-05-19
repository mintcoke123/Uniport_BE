package com.uniport.repository;

import com.uniport.entity.CompetitionApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompetitionApplicationRepository extends JpaRepository<CompetitionApplication, Long> {
    Optional<CompetitionApplication> findByCompetition_IdAndUser_Id(Long competitionId, Long userId);

    boolean existsByCompetition_IdAndUser_IdAndStatus(Long competitionId, Long userId, String status);

    List<CompetitionApplication> findByCompetition_IdAndStatus(Long competitionId, String status);

    List<CompetitionApplication> findByUser_IdOrderByAppliedAtDesc(Long userId);

    void deleteByUser_Id(Long userId);
}
