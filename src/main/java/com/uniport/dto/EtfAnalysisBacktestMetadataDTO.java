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

    @Schema(example = "0.0005", description = "거래 수수료율")
    private Double transactionFeeRate;

    @Schema(example = "0.0003", description = "슬리피지율")
    private Double slippageRate;

    @Schema(example = "100000000")
    private Long principalAmountKrw;

    @Schema(example = "248")
    private Integer tradingDays;

    @Schema(example = "true")
    private Boolean usedFallbackMessage;

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
}
