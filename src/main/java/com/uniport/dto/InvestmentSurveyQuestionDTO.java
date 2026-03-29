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
@Schema(description = "투자 성향 설문 질문")
public class InvestmentSurveyQuestionDTO {

    @Schema(description = "질문 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private long id;

    @Schema(description = "질문 순서", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private int order;

    @Schema(description = "질문 내용", example = "100만원이 1주일 사이 5만원(5%) 떨어졌어.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "질문 부가 내용", example = "나랑 제일 가까운 반응은?", nullable = true)
    private String subtitle;

    @ArraySchema(schema = @Schema(implementation = InvestmentSurveyQuestionOptionDTO.class))
    private List<InvestmentSurveyQuestionOptionDTO> options;
}
