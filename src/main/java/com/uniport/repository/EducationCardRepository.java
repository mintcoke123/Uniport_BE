package com.uniport.repository;

import com.uniport.entity.EducationCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EducationCardRepository extends JpaRepository<EducationCardEntity, Long> {
    List<EducationCardEntity> findByTrackAndSectorAndDayNumberOrderBySourceIdxAsc(String track, String sector, Integer dayNumber);
}
