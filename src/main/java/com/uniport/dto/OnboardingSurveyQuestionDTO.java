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
@Schema(description = "온보딩 설문 질문")
public class OnboardingSurveyQuestionDTO {

    @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer order;

    @Schema(example = "SINGLE_SELECT", allowableValues = {"SINGLE_SELECT", "MULTI_SELECT"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(example = "1주일 사이 100만원이 95만원(-5%)으로 떨어졌어", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(example = "나랑 제일 가까운 반응은?", nullable = true)
    private String subtitle;

    @Schema(example = "1", nullable = true)
    private Integer minSelection;

    @Schema(example = "3", nullable = true)
    private Integer maxSelection;

    @ArraySchema(schema = @Schema(implementation = OnboardingSurveyOptionDTO.class))
    private List<OnboardingSurveyOptionDTO> options;
}
