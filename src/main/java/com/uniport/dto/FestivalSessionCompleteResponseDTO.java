package com.uniport.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class FestivalSessionCompleteResponseDTO {
    private Long sessionId;
    private String displayName;
    private BigDecimal startCash;
    private BigDecimal endTotalValue;
    private BigDecimal returnRate;
    private String basePrize;
    private String finalPrize;
    private Integer currentRank;
    private List<FestivalLeaderboardItemDTO> leaderboard;
}
