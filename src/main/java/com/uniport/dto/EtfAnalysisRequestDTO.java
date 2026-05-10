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
@Schema(description = "ETF 분석 요청")
public class EtfAnalysisRequestDTO {

    @Schema(example = "1Y", allowableValues = {"1Y", "3Y", "5Y", "ALL"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String period;

    @Schema(example = "SP500", allowableValues = {"SP500", "KOSPI", "NASDAQ"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String benchmark;

    @Schema(example = "100000000", description = "백테스트 시작 원금. 미전달 시 1억원 기준으로 계산합니다.")
    private Long principalAmountKrw;

    @Schema(example = "MONTHLY", allowableValues = {"MONTHLY", "QUARTERLY", "SEMI_ANNUAL", "NONE"}, description = "리밸런싱 방식")
    private String rebalancePolicy;
}
