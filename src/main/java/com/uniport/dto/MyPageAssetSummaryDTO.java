package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyPageAssetSummaryDTO {
    private BigDecimal totalAssets;
    private BigDecimal investmentAmount;
    private BigDecimal profitLoss;
    private BigDecimal profitLossRate;
    private Integer pointBalance;
}
