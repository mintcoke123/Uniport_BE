package com.uniport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "learning_user_states")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class LearningUserStateEntity extends AuditableEntity {

    @Id
    private Long userId;

    @Column(nullable = false)
    private Integer level;

    @Column(nullable = false)
    private Integer point;

    private Long activeCourseId;

    @Column(nullable = false)
    private Integer streakDays;

    private LocalDate lastCompletedDate;

    private LocalDate roadmapLastCompletedDate;

    @Lob
    @Column(nullable = false)
    private String currentDayByCourseJson;

    @Lob
    @Column(nullable = false)
    private String completedDaysByCourseJson;

    @Lob
    @Column(nullable = false)
    private String submittedStepIdsJson;

    @Lob
    private String educationCurrentDayJson;

    @Lob
    private String educationCompletedDaysJson;

    @Lob
    private String educationQuizAnswersJson;
}
