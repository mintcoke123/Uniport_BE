package com.uniport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
        name = "education_quizzes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_education_quiz_track_sector_day_source_number", columnNames = {"track", "sector", "day_number", "source_mode", "quiz_number"})
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class EducationQuizEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_mode", nullable = false, length = 20)
    private String sourceMode;

    @Column(nullable = false, length = 40)
    private String track;

    @Column(length = 120)
    private String sector;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;

    @Column(name = "quiz_number", nullable = false)
    private Integer quizNumber;

    @Column(length = 40)
    private String quizType;

    @Column(length = 2000)
    private String question;

    @Column(columnDefinition = "TEXT")
    private String optionsJson;

    @Column(nullable = false)
    private Integer answerIndex;

    @Column(length = 200)
    private String topic;

    @Column(length = 120)
    private String area;

    @Column(length = 2000)
    private String intent;
}
