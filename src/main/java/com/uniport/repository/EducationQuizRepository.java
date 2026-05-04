package com.uniport.repository;

import com.uniport.entity.EducationQuizEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EducationQuizRepository extends JpaRepository<EducationQuizEntity, Long> {
    List<EducationQuizEntity> findByTrackAndSectorAndDayNumberOrderByQuizNumberAsc(String track, String sector, Integer dayNumber);

    List<EducationQuizEntity> findByTrackAndSectorAndDayNumberAndSourceModeOrderByQuizNumberAsc(String track, String sector, Integer dayNumber, String sourceMode);
}
