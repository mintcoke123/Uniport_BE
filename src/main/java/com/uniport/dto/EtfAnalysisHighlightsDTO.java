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
@Schema(description = "ETF 분석 핵심 지표")
public class EtfAnalysisHighlightsDTO {

    @Schema(example = "12.4", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double returnRate;

    @Schema(example = "3.2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double benchmarkExcessReturn;

    @Schema(example = "14.2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double volatility;

    @Schema(example = "-8.5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double maxDrawdown;
}
