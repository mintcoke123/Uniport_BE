package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "학습 step 제출 요청")
public class LearningStepSubmitRequestDTO {

    @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long selectedAnswerId;
}
