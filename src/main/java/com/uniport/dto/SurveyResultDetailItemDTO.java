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
@Schema(description = "투자 성향 결과 상세 항목")
public class SurveyResultDetailItemDTO {

    @Schema(description = "항목명", example = "분석형 의사결정", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "항목 설명", example = "중요한 투자 결정을 내릴 때 신중하게 판단하는 편입니다.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;
}
