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
@Schema(description = "인기 ETF 상세 구성 종목")
public class EtfDiscoveryDetailHoldingDTO {

    @Schema(example = "LG에너지솔루션", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(example = "005930", requiredMode = Schema.RequiredMode.REQUIRED)
    private String symbol;

    @Schema(example = "KOSPI", requiredMode = Schema.RequiredMode.REQUIRED)
    private String market;

    @Schema(example = "STOCK", allowableValues = {"STOCK", "BOND", "CASH"})
    private String assetType;

    @Schema(example = "KRW", allowableValues = {"KRW", "USD"})
    private String currency;

    @Schema(example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer weight;

    @Schema(example = "39.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double changeRate;

    @Schema(example = "https://cdn.example.com/lges.png")
    private String logoUrl;

    @Schema(implementation = StockVisualDTO.class)
    private StockVisualDTO visual;
}
