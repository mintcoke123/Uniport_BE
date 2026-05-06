package com.uniport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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

    @Column(nullable = false, columnDefinition = "TEXT")
    private String currentDayByCourseJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String completedDaysByCourseJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String submittedStepIdsJson;

    @Column(columnDefinition = "TEXT")
    private String educationCurrentDayJson;

    @Column(columnDefinition = "TEXT")
    private String educationCompletedDaysJson;

    @Column(columnDefinition = "TEXT")
    private String educationQuizAnswersJson;
}
