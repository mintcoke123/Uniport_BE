package com.uniport.repository;

import com.uniport.entity.OnboardingSurveyOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface OnboardingSurveyOptionRepository extends JpaRepository<OnboardingSurveyOptionEntity, Long> {

    @Query("""
            select option
            from OnboardingSurveyOptionEntity option
            where option.active = true
              and option.question.id in :questionIds
            order by option.question.id asc, option.optionOrder asc
            """)
    List<OnboardingSurveyOptionEntity> findActiveByQuestionIds(
            @Param("questionIds") Collection<Long> questionIds
    );
}
