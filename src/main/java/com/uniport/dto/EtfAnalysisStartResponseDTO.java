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
@Schema(description = "ETF 분석 시작 응답")
public class EtfAnalysisStartResponseDTO {

    @Schema(example = "REPORT_301", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reportId;

    @Schema(example = "ETF_101", requiredMode = Schema.RequiredMode.REQUIRED)
    private String etfId;

    @Schema(example = "COMPLETED", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

    @Schema(example = "2026-03-11T16:30:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    private String createdAt;
}
