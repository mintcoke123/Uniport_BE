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
        name = "education_overviews",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_education_overview_track_sector_day", columnNames = {"track", "sector", "day_number"})
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class EducationOverviewEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String track;

    @Column(length = 120)
    private String sector;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;

    @Column(length = 40)
    private String levelLabel;

    @Column(length = 40)
    private String dayLabel;

    @Column(length = 200)
    private String title;

    @Column(length = 1000)
    private String summary1;

    @Column(length = 1000)
    private String summary2;

    @Column(columnDefinition = "TEXT")
    private String keyPointsJson;

    @Column(length = 200)
    private String ctaLabel;
}
