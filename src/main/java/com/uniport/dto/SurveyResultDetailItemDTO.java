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

    @Schema(description = "항목명", example = "특징", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "항목 설명", example = "타이밍보다 꾸준함을 믿는 편이다.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;
}
