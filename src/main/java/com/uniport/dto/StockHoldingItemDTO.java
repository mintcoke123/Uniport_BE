package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 명세 §2-1: stockHoldings[] 항목 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockHoldingItemDTO {

    private Long id;
    private String name;
    private Integer quantity;
    private BigDecimal currentValue;
    private BigDecimal profitLoss;
    private BigDecimal profitLossPercentage;
    private String logoColor;
}
