package com.uniport.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class FestivalLeaderboardItemDTO {
    private Long sessionId;
    private Integer rank;
    private String displayName;
    private String mainStockName;
    private BigDecimal endTotalValue;
    private BigDecimal returnRate;
    private String prize;
    private LocalDateTime endedAt;
}
