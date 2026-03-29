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
@Schema(description = "AI 리스크 진단")
public class EtfAnalysisRiskDiagnosisDTO {

    @Schema(example = "시장 대비 리스크 관리가 원활하며 균형 잡힌 분산 투자가 이루어진 설계입니다.")
    private String summary;
}
