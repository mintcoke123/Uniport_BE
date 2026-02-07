package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 명세 §3-2~3-4: /api/market/stocks 배열 항목 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketStockItemDTO {

    private Long id;
    private String name;
    private String code;
    private BigDecimal currentPrice;
    private BigDecimal change;
    private BigDecimal changeRate;
    private String logoColor;
}
