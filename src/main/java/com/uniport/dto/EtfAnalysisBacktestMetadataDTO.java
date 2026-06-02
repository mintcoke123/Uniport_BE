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
@Schema(description = "ETF 분석 백테스트 메타데이터")
public class EtfAnalysisBacktestMetadataDTO {

    @Schema(example = "backtest-v1.0.0")
    private String analysisVersion;

    @Schema(example = "ai-feedback-v1.0.0")
    private String messageVersion;

    @Schema(example = "MONTHLY")
    private String rebalancePolicy;

    @Schema(example = "1", description = "월말 리밸런싱 주기. NONE이면 0입니다.")
    private Integer rebalanceIntervalMonths;

    @Schema(example = "0.0005", description = "거래 수수료율")
    private Double transactionFeeRate;

    @Schema(example = "0.0003", description = "슬리피지율")
    private Double slippageRate;

    @Schema(example = "100000000")
    private Long principalAmountKrw;

    @Schema(example = "248")
    private Integer tradingDays;

    @Schema(example = "MONTH_END", description = "성과 계산에 사용한 가격 주기")
    private String priceFrequency;

    @Schema(example = "INTEGER_FLOOR", description = "목표 비중 배분 시 주식 수 처리 방식")
    private String shareRoundingPolicy;

    @Schema(example = "false", description = "배당 반영 여부")
    private Boolean dividendConsidered;

    @Schema(example = "GLOBAL_STOCK_AND_ETF", description = "분석 대상 자산 범위")
    private String marketScope;

    @Schema(example = "true")
    private Boolean usedFallbackMessage;

    @Schema(example = "5Y", description = "사용자가 요청한 분석 기간")
    private String requestedPeriod;

    @Schema(example = "3Y", description = "실제 공통 가격 데이터로 계산한 분석 기간")
    private String actualPeriod;

    @Schema(example = "2023-05-19", description = "백테스트에 사용 가능한 공통 가격 데이터 시작일")
    private String priceDataStartDate;

    @Schema(example = "2026-05-18", description = "백테스트에 사용 가능한 공통 가격 데이터 종료일")
    private String priceDataEndDate;

    @Schema(example = "true", description = "요청 기간보다 짧은 기간으로 자동 조정되었는지 여부")
    private Boolean periodDowngraded;

    @Schema(description = "가격 데이터 기간 조정 및 가용성 경고")
    private List<String> dataWarnings;

    @Schema(example = "KIS_DOMESTIC_ADJUSTED_CLOSE", description = "가격 데이터 출처")
    private String priceSource;

    @Schema(example = "asset_price_daily", description = "가격 캐시 정책")
    private String priceCachePolicy;

    @Schema(example = "fx_rate_daily", description = "환율 캐시 정책")
    private String fxCachePolicy;

    @Schema(description = "백테스트 계산 가정")
    private List<String> assumptions;

    @Schema(description = "백테스트 한계 및 데이터 주의사항")
    private List<String> limitations;

    @Schema(example = "none", description = "MVP에서는 LLM을 호출하지 않습니다.")
    private String llmModel;

    @Schema(example = "none", description = "MVP에서는 LLM 프롬프트를 사용하지 않습니다.")
    private String promptVersion;

    @Schema(example = "success:json_schema", description = "LLM 호출 시도 결과")
    private String llmStatus;

    @Schema(example = "accepted", description = "LLM 결과 채택 또는 fallback 사유")
    private String llmFallbackReason;
}
