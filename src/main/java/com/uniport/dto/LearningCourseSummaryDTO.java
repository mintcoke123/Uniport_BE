package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "학습 코스 요약 정보")
public class LearningCourseSummaryDTO {

    @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(example = "입문 30일 코스", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(example = "투자의 기초를 탄탄하게 다지는 첫걸음", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;

    @Schema(example = "https://example.com/course-beginner.png", nullable = true)
    private String thumbnailUrl;

    @Schema(example = "2", nullable = true)
    private Integer currentDay;

    @Schema(example = "30", nullable = true)
    private Integer totalDays;

    @Schema(example = "Day 02 / 30", nullable = true)
    private String progressLabel;

    @Schema(example = "IN_PROGRESS", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

    @Schema(example = "현재 이수중", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusLabel;

    @Schema(example = "퀴즈 풀기", requiredMode = Schema.RequiredMode.REQUIRED)
    private String actionLabel;

    @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean isLocked;
}
