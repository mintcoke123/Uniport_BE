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
@Schema(description = "투자 성향 설문 선택지")
public class InvestmentSurveyQuestionOptionDTO {

    @Schema(description = "선택지 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private long id;

    @Schema(description = "선택지 내용", example = "바로 팔고 다시는 안 한다", requiredMode = Schema.RequiredMode.REQUIRED)
    private String label;

    @Schema(description = "선택지 부가 내용", example = "손실을 더 보고 싶지 않다", nullable = true)
    private String sublabel;
}
