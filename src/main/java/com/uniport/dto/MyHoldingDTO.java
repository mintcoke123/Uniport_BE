package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 명세 §3-5: myHolding */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyHoldingDTO {

    private Integer quantity;
    private BigDecimal avgPrice;
    private BigDecimal totalValue;
    private BigDecimal totalProfit;
    private BigDecimal profitRate;
}
