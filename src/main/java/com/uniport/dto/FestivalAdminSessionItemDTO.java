package com.uniport.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class FestivalAdminSessionItemDTO {
    private Long sessionId;
    private String status;
    private String participantName;
    private String displayName;
    private String department;
    private String studentId;
    private String phoneNumber;
    private BigDecimal startCash;
    private BigDecimal endCash;
    private BigDecimal endPortfolioValue;
    private BigDecimal endTotalValue;
    private BigDecimal returnRate;
    private String mainStockName;
    private String basePrize;
    private String finalPrize;
    private Integer tradeCount;
    private Integer unfilledOrderCount;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private JsonNode holdingsSnapshot;
    private JsonNode tradeHistory;
}
