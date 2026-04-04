package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "일일 학습 콘텐츠 조회 응답")
public class LearningDayContentResponseDTO {

    @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long courseId;

    @Schema(example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer day;

    @Schema(example = "캔들스틱 차트의 이해", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(implementation = LearningProgressDTO.class)
    private LearningProgressDTO progress;

    @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer currentStepOrder;

    @Schema(example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer totalSteps;

    @ArraySchema(schema = @Schema(implementation = LearningDayStepDTO.class))
    private List<LearningDayStepDTO> steps;
}
