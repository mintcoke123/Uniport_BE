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
@Schema(description = "학습 홈 조회 응답")
public class LearningHomeResponseDTO {

    @Schema(example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer level;

    @Schema(example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer point;

    @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean todayLearningCompleted;

    @Schema(implementation = LearningHomeCourseDTO.class)
    private LearningHomeCourseDTO course;

    @ArraySchema(schema = @Schema(implementation = LearningRoadmapItemDTO.class))
    private List<LearningRoadmapItemDTO> roadmap;

    @Schema(implementation = LearningCurrentContentDTO.class, nullable = true)
    private LearningCurrentContentDTO currentContent;
}
