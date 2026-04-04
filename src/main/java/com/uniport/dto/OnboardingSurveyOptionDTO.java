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
@Schema(description = "온보딩 설문 선택지")
public class OnboardingSurveyOptionDTO {

    @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(example = "바로 손절한다", requiredMode = Schema.RequiredMode.REQUIRED)
    private String label;

    @Schema(example = "손실을 더 보고 싶지 않다", nullable = true)
    private String sublabel;
}
