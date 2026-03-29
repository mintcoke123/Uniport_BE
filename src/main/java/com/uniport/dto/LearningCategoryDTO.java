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
@Schema(description = "학습 코스 카테고리")
public class LearningCategoryDTO {

    @Schema(example = "MAIN", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @Schema(example = "메인 코스", requiredMode = Schema.RequiredMode.REQUIRED)
    private String label;
}
