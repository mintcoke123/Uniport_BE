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
@Schema(description = "학습 코스 목록 조회 응답")
public class LearningCoursesResponseDTO {

    @ArraySchema(schema = @Schema(implementation = LearningCategoryDTO.class))
    private List<LearningCategoryDTO> categories;

    @Schema(example = "MAIN", requiredMode = Schema.RequiredMode.REQUIRED)
    private String selectedCategory;

    @ArraySchema(schema = @Schema(implementation = LearningCourseSummaryDTO.class))
    private List<LearningCourseSummaryDTO> courses;
}
