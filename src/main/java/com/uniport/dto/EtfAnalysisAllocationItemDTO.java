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
@Schema(description = "자산 비중 아이템")
public class EtfAnalysisAllocationItemDTO {

    @Schema(example = "KRX_005930", description = "원본 종목 ID")
    private String securityId;

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

    @Schema(example = "40", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer weight;

    @Schema(example = "https://uniportbe-production.up.railway.app/api/stock-symbols/NASDAQ/AAPL.svg?text=APP&bg=F3E8FF&fg=9333EA")
    private String logoUrl;

    @Schema(implementation = StockVisualDTO.class)
    private StockVisualDTO visual;
}
