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
@Schema(description = "학습 홈의 현재 코스 정보")
public class LearningHomeCourseDTO {

    @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(example = "30일 로드맵", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer progressPercent;

    @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer currentDay;

    @Schema(example = "30", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer totalDays;

    @Schema(example = "Day 02 / 30", requiredMode = Schema.RequiredMode.REQUIRED)
    private String progressLabel;
}
