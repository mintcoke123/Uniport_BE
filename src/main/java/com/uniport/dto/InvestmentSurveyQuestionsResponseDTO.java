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
@Schema(description = "투자 성향 설문 질문 조회 응답")
public class InvestmentSurveyQuestionsResponseDTO {

    @ArraySchema(schema = @Schema(implementation = InvestmentSurveyQuestionDTO.class))
    private List<InvestmentSurveyQuestionDTO> questions;

    @Schema(description = "현재 사용자가 이미 투자 성향 결과를 보유하고 있는지 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean hasResult;

    @Schema(description = "이미 저장된 투자 성향 결과", example = "BALANCED", nullable = true)
    private String investmentProfileResult;

    @Schema(description = "응답 메시지", example = "투자 성향 설문 질문 조회 성공", nullable = true)
    private String message;
}
