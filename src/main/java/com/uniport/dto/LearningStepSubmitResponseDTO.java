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
@Schema(description = "학습 step 제출 응답")
public class LearningStepSubmitResponseDTO {

    @Schema(example = "102", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long stepId;

    @Schema(example = "true", nullable = true)
    private Boolean isCorrect;

    @Schema(example = "2", nullable = true)
    private Long correctAnswerId;

    @Schema(example = "몸통은 시가와 종가의 차이를 의미합니다.", nullable = true)
    private String explanation;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean submitted;

    @Schema(example = "103", nullable = true)
    private Long nextStepId;

    @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean dayCompleted;

    @Schema(example = "정답이에요!", nullable = true)
    private String resultTitle;

    @Schema(example = "몸통은 시가와 종가의 차이를 의미합니다.", nullable = true)
    private String resultDescription;
}
