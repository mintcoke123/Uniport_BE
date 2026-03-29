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
@Schema(description = "내 투자 요약")
public class HomeInvestmentSummaryDTO {

    @Schema(example = "9999999", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal totalAssets;

    @Schema(example = "108999", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal profitLoss;

    @Schema(example = "1.09", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal profitLossRate;
}
