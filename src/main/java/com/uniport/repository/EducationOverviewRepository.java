package com.uniport.repository;

import com.uniport.entity.EducationOverviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EducationOverviewRepository extends JpaRepository<EducationOverviewEntity, Long> {
    List<EducationOverviewEntity> findAllByOrderByTrackAscSectorAscDayNumberAsc();

    Optional<EducationOverviewEntity> findByTrackAndSectorAndDayNumber(String track, String sector, Integer dayNumber);

    List<EducationOverviewEntity> findByTrackAndSectorOrderByDayNumberAsc(String track, String sector);
}
