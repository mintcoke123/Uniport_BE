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
@Schema(description = "온보딩 설문 제출 결과")
public class SurveyOnboardingResponseDTO {

    @Schema(description = "결과 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private long id;

    @Schema(description = "투자 성향", example = "균형 잡힌 판단형", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(description = "결과 제목", example = "균형 잡힌 판단형", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "결과 설명", example = "안정성과 수익 사이의 균형을 중요하게 생각하는 투자 성향입니다.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;

    @Schema(description = "결과 이미지", example = "https://example.com/images/result-balanced.png", nullable = true)
    private String imageUrl;

    @ArraySchema(schema = @Schema(implementation = SurveyResultSectionDTO.class))
    private List<SurveyResultSectionDTO> features;

    @ArraySchema(schema = @Schema(implementation = SurveyResultSectionDTO.class))
    private List<SurveyResultSectionDTO> guides;
}
