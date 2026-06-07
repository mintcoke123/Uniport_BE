package com.uniport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
        name = "onboarding_survey_options",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_onboarding_survey_option_question_order",
                        columnNames = {"question_id", "option_order"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class OnboardingSurveyOptionEntity extends AuditableEntity {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private OnboardingSurveyQuestionEntity question;

    @Column(name = "option_order", nullable = false)
    private Integer optionOrder;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(length = 200)
    private String sublabel;

    @Column(nullable = false)
    private Boolean active;
}
