package com.uniport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "onboarding_survey_questions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_onboarding_survey_question_order", columnNames = {"question_order"})
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class OnboardingSurveyQuestionEntity extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "question_order", nullable = false)
    private Integer questionOrder;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 300)
    private String subtitle;

    @Column(name = "min_selection", nullable = false)
    private Integer minSelection;

    @Column(name = "max_selection", nullable = false)
    private Integer maxSelection;

    @Column(nullable = false)
    private Boolean active;
}
