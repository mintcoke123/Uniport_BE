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
@Schema(description = "AI 리스크 진단")
public class EtfAnalysisRiskDiagnosisDTO {

    @Schema(example = "시장 대비 리스크 관리가 원활하며 균형 잡힌 분산 투자가 이루어진 설계입니다.")
    private String summary;

    @Schema(example = "MEDIUM", description = "룰 기반 리스크 등급")
    private String riskGrade;

    @Schema(example = "보통", description = "룰 기반 리스크 등급 한글 라벨")
    private String riskGradeLabel;

    @Schema(example = "47", description = "0~100 리스크 점수")
    private Integer riskScore;

    @Schema(description = "백테스트 결과에서 도출된 긍정 사실")
    private List<String> positiveFacts;

    @Schema(description = "백테스트 결과에서 도출된 위험 사실")
    private List<String> riskFacts;
}
