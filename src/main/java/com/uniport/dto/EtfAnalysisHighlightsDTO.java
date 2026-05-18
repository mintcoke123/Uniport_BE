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

    @Schema(example = "0.81", description = "하방위험 기준 소르티노 비율")
    private Double sortinoRatio;

    @Schema(example = "1.13", description = "벤치마크 월간 수익률 대비 베타")
    private Double beta;

    @Schema(example = "9.4", description = "월간 초과수익률 표준편차의 연환산 추적오차")
    private Double trackingError;

    @Schema(example = "0.24", description = "월간 초과수익률 기준 정보비율")
    private Double informationRatio;

    @Schema(example = "57.0", description = "월간 벤치마크 초과 달성 비율")
    private Double winRate;

    @Schema(example = "8.3", description = "벤치마크 CAGR")
    private Double benchmarkAnnualizedReturn;

    @Schema(example = "17.6", description = "벤치마크 연환산 변동성")
    private Double benchmarkVolatility;

    @Schema(example = "-27.0", description = "벤치마크 최대낙폭")
    private Double benchmarkMaxDrawdown;
}
