package com.uniport.dto;

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
@Schema(description = "투자 성향 결과 상세 항목")
public class SurveyResultDetailItemDTO {

    @Schema(description = "항목명", example = "특징", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "항목 설명", example = "타이밍보다 꾸준함을 믿는 편이다.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;

    @Builder.Default
    @Schema(description = "항목명에서 강조할 부분 문자열 목록", example = "[\"코어 70/위성 30\"]")
    private List<String> nameHighlights = List.of();

    @Builder.Default
    @Schema(description = "항목 설명에서 강조할 부분 문자열 목록", example = "[\"손실 관리\"]")
    private List<String> descriptionHighlights = List.of();
}
