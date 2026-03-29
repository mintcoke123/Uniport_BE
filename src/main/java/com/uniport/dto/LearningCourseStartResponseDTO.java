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
@Schema(description = "코스 시작 응답")
public class LearningCourseStartResponseDTO {

    @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long courseId;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean started;

    @Schema(example = "IN_PROGRESS", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

    @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer currentDay;

    @Schema(example = "30", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer totalDays;
}
