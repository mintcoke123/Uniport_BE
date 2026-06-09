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
@Schema(description = "나만의 ETF 자산 검색 결과 아이템")
public class CustomEtfAssetSearchItemDTO {

    @Schema(example = "KRX_005930", description = "ETF 구성 요청에 사용할 자산 ID")
    private String assetId;

    @Schema(example = "KRX_005930", description = "기존 나만의 ETF 요청 DTO와 호환되는 stockId")
    private String stockId;

    @Schema(example = "삼성전자", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(example = "005930", requiredMode = Schema.RequiredMode.REQUIRED)
    private String symbol;

    @Schema(example = "KOSPI", requiredMode = Schema.RequiredMode.REQUIRED)
    private String market;

    @Schema(example = "STOCK", allowableValues = {"STOCK"}, description = "나만의 ETF 직접 구성은 주식형 자산만 지원")
    private String assetType;

    @Schema(example = "KRW", allowableValues = {"KRW", "USD"})
    private String currency;

    @Schema(example = "true", description = "ETF 구성/분석 요청 가능 여부. 실제 가격이 부족하면 추정 백테스트가 사용될 수 있음")
    private Boolean backtestEnabled;

    @Schema(example = "VERIFIED", description = "가격 데이터 검증 상태")
    private String dataStatus;

    @Schema(example = "No recent KIS price", description = "미검증 또는 비활성 사유. 사용 가능 자산이면 null")
    private String dataStatusMessage;

    @Schema(implementation = CustomEtfPriceCoverageDTO.class)
    private CustomEtfPriceCoverageDTO priceCoverage1Y;

    @Schema(implementation = CustomEtfPriceCoverageDTO.class)
    private CustomEtfPriceCoverageDTO priceCoverage3Y;

    @Schema(implementation = CustomEtfPriceCoverageDTO.class)
    private CustomEtfPriceCoverageDTO priceCoverage5Y;

    @Schema(example = "https://cdn.example.com/samsung.png")
    private String logoUrl;

    @Schema(implementation = StockVisualDTO.class)
    private StockVisualDTO visual;
}
