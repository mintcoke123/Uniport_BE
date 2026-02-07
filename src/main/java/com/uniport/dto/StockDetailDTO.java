package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** 명세 §3-5: GET /api/stocks/:id 응답 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockDetailDTO {

    private Long id;
    private String name;
    private String code;
    private BigDecimal currentPrice;
    private BigDecimal change;
    private BigDecimal changeRate;
    private String logoColor;
    private MyHoldingDTO myHolding;           // null if not holding
    private MarketDataDTO marketData;
    private List<FinancialDataItemDTO> financialData;
    private String companyInfo;
    private List<NewsItemDTO> news;
}
