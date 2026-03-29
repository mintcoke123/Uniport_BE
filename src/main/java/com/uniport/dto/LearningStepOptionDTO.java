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
@Schema(description = "학습 step 선택지")
public class LearningStepOptionDTO {

    @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(example = "시가와 종가의 차이", requiredMode = Schema.RequiredMode.REQUIRED)
    private String label;
}
