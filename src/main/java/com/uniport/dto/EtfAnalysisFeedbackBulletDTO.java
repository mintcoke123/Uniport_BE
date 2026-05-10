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
@Schema(description = "AI 피드백 핵심 문장")
public class EtfAnalysisFeedbackBulletDTO {

    @Schema(example = "RISK", allowableValues = {"STRENGTH", "RISK", "INFO"})
    private String type;

    @Schema(example = "상위 3개 종목 비중이 78.0%로 높습니다.")
    private String message;
}
