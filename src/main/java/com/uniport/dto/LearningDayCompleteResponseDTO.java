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
@Schema(description = "Day 완료 응답")
public class LearningDayCompleteResponseDTO {

    @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long courseId;

    @Schema(example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer day;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean completed;

    @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer streakDays;

    @Schema(example = "50", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer earnedPoint;

    @Schema(example = "120", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer earnedExp;
}
