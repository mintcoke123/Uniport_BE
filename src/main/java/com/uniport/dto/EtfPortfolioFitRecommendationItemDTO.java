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
@Schema(description = "포트폴리오 적합 추천 종목")
public class EtfPortfolioFitRecommendationItemDTO {

    @Schema(example = "FIT_KRX_035420", requiredMode = Schema.RequiredMode.REQUIRED)
    private String recommendationId;

    @Schema(example = "KRX_035420", requiredMode = Schema.RequiredMode.REQUIRED)
    private String stockId;

    @Schema(example = "NAVER", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(example = "035420", requiredMode = Schema.RequiredMode.REQUIRED)
    private String symbol;

    @Schema(example = "KOSPI", requiredMode = Schema.RequiredMode.REQUIRED)
    private String market;

    @Schema(example = "0.91", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double fitScore;

    @Schema(example = "현재 포트폴리오의 성장주 중심 구성과 어울리는 보완 후보예요.")
    private String reason;

    @Schema(example = "[\"시장 연계\", \"성장 보완\"]")
    private List<String> tags;

    @Schema(example = "true")
    private Boolean backtestEnabled;

    @Schema(example = "VERIFIED")
    private String dataStatus;

    @Schema(example = "실가격이 부족하면 추정 가격으로 백테스트합니다.")
    private String dataStatusMessage;

    @Schema(example = "https://cdn.example.com/naver.png")
    private String logoUrl;

    @Schema(implementation = StockVisualDTO.class)
    private StockVisualDTO visual;
}
