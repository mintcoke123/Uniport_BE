package com.uniport.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FestivalSessionCompleteRequestDTO {
    private BigDecimal endCash;
    private BigDecimal endPortfolioValue;
    private BigDecimal endTotalValue;
    private BigDecimal returnRate;
    private String mainStockName;
    private Integer tradeCount;
    private Integer unfilledOrderCount;
    private JsonNode holdingsSnapshot;
    private JsonNode tradeHistory;
}
