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
@Schema(description = "코스 진입 정보 조회 응답")
public class LearningCourseDetailResponseDTO {

    @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer day;

    @Schema(example = "CHAPTER 02", nullable = true)
    private String chapter;

    @Schema(example = "캔들스틱 차트의 이해", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(example = "캔들스틱 차트는 특정 기간 동안의 가격 움직임을 시각적으로 보여줍니다.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;

    @Schema(example = "https://example.com/detail.png", nullable = true)
    private String thumbnailUrl;

    @ArraySchema(schema = @Schema(implementation = LearningKeyConceptDTO.class))
    private List<LearningKeyConceptDTO> keyConcepts;

    @Schema(implementation = LearningProgressDTO.class)
    private LearningProgressDTO progress;

    @Schema(example = "IN_PROGRESS", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
}
