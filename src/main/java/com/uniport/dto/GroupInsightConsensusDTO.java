package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "집단지성 종목 인사이트")
public class GroupInsightConsensusDTO {

    @Schema(example = "NVDA", requiredMode = Schema.RequiredMode.REQUIRED)
    private String stockCode;

    @Schema(example = "엔비디아", requiredMode = Schema.RequiredMode.REQUIRED)
    private String stockName;

    @Schema(example = "US")
    private String market;

    @Schema(example = "https://cdn.example.com/nvda.png")
    private String logoUrl;

    @Schema(implementation = StockVisualDTO.class)
    private StockVisualDTO visual;

    @Schema(example = "92", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer confidenceRate;

    @Schema(example = "12.5", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal dailyReturnRate;

    @Schema(example = "BUY", requiredMode = Schema.RequiredMode.REQUIRED)
    private String signal;
}
