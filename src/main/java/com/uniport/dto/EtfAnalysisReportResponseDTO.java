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

    @Schema(example = "2026-03-11T16:30:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    private String createdAt;
}
