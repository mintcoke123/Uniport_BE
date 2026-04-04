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
@Schema(description = "온보딩 설문 플로우 응답")
public class OnboardingSurveyFlowResponseDTO {

    @Schema(example = "유니포트", nullable = true)
    private String nickname;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean nicknameRequired;

    @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean hasResult;

    @ArraySchema(schema = @Schema(implementation = OnboardingSurveyQuestionDTO.class))
    private List<OnboardingSurveyQuestionDTO> questions;

    @Schema(implementation = OnboardingSurveyResultDTO.class, nullable = true)
    private OnboardingSurveyResultDTO result;
}
