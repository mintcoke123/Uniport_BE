package com.uniport.repository;

import com.uniport.entity.LearningCourseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningCourseRepository extends JpaRepository<LearningCourseEntity, Long> {
    List<LearningCourseEntity> findByCategoryOrderByIdAsc(String category);
}
