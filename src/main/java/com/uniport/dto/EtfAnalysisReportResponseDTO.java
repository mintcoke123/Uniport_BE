package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "ETF 분석 리포트 응답")
public class EtfAnalysisReportResponseDTO {

    @Schema(example = "REPORT_301", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reportId;

    @Schema(example = "ETF_101", requiredMode = Schema.RequiredMode.REQUIRED)
    private String etfId;

    @Schema(example = "1Y", requiredMode = Schema.RequiredMode.REQUIRED)
    private String period;

    @Schema(example = "SP500", requiredMode = Schema.RequiredMode.REQUIRED)
    private String benchmark;

    private EtfAnalysisHighlightsDTO highlights;

    private EtfAnalysisCumulativeProfitDTO cumulativeProfit;

    private EtfAnalysisRiskDiagnosisDTO riskDiagnosis;

    private EtfAnalysisAllocationDTO allocation;

    private EtfAnalysisAiFeedbackDTO aiFeedback;

    private EtfAnalysisBacktestMetadataDTO metadata;

    @Schema(description = "AI 문장 생성에 넘길 수 있는 검증된 숫자/사실 원본. MVP에서는 룰 기반 피드백 생성에 사용합니다.")
    private Map<String, Object> insightFacts;

    @Schema(example = "2026-03-11T16:30:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    private String createdAt;
}
