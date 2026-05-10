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
@Schema(description = "나만의 ETF 구성 종목")
public class CustomEtfHoldingDTO {

    @Schema(example = "US_AAPL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String stockId;

    @Schema(example = "Apple Inc.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(example = "AAPL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String symbol;

    @Schema(example = "NASDAQ", requiredMode = Schema.RequiredMode.REQUIRED)
    private String market;

    @Schema(example = "STOCK", allowableValues = {"STOCK", "BOND", "CASH"})
    private String assetType;

    @Schema(example = "USD", allowableValues = {"KRW", "USD"})
    private String currency;

    @Schema(example = "50", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer weight;

    @Schema(example = "https://cdn.example.com/aapl.png")
    private String logoUrl;

    @Schema(implementation = StockVisualDTO.class)
    private StockVisualDTO visual;
}
