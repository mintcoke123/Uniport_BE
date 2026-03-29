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
@Schema(description = "ETF 분석 리포트 적용 응답")
public class EtfAnalysisApplyResponseDTO {

    @Schema(example = "ETF_101", requiredMode = Schema.RequiredMode.REQUIRED)
    private String etfId;

    @Schema(example = "REPORT_301", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reportId;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean applied;

    @Schema(example = "2026-03-11T16:40:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    private String updatedAt;
}
