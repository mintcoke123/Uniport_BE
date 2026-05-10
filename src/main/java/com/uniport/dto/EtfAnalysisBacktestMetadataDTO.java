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

    @Schema(example = "none", description = "MVP에서는 LLM을 호출하지 않습니다.")
    private String llmModel;

    @Schema(example = "none", description = "MVP에서는 LLM 프롬프트를 사용하지 않습니다.")
    private String promptVersion;
}
