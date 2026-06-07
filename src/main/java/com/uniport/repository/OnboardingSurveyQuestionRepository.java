package com.uniport.repository;

import com.uniport.entity.OnboardingSurveyQuestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OnboardingSurveyQuestionRepository extends JpaRepository<OnboardingSurveyQuestionEntity, Long> {

    List<OnboardingSurveyQuestionEntity> findByActiveTrueOrderByQuestionOrderAsc();
}
