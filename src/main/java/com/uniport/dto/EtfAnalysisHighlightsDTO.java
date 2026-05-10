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

    @Schema(example = "11.8", description = "연환산 수익률")
    private Double annualizedReturn;

    @Schema(example = "9.2", description = "동일 기간 벤치마크 수익률. 벤치마크 가격 데이터가 없으면 null입니다.")
    private Double benchmarkReturn;

    @Schema(example = "0.74", description = "무위험 수익률 0% 기준 샤프 비율")
    private Double sharpeRatio;
}
