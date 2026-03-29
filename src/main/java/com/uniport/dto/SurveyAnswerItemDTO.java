package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "온보딩 설문 응답 항목")
public class SurveyAnswerItemDTO {

    @Schema(description = "질문 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long questionId;

    @Schema(description = "선택지 ID", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long optionId;
}
