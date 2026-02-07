package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 명세 §2-1: investmentData */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentDataDTO {

    private BigDecimal totalAssets;
    private BigDecimal profitLoss;
    private BigDecimal profitLossPercentage;
    private BigDecimal investmentPrincipal;
    private BigDecimal cashBalance;
}
