package com.uniport.repository;

import com.uniport.entity.Competition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompetitionRepository extends JpaRepository<Competition, Long> {

    List<Competition> findByStatusOrderByStartDateAsc(String status);

    Optional<Competition> findFirstByStatusOrderByStartDateAsc(String status);
}
