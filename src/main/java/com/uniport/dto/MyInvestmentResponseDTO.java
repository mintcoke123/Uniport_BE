package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 명세 §2-1: GET /api/me/investment 응답 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyInvestmentResponseDTO {

    private InvestmentDataDTO investmentData;
    private List<StockHoldingItemDTO> stockHoldings;
    private CompetitionDataDTO competitionData;
    /** 해당 멤버가 모의투자를 시작했는지 (방에 3명 모여 시작한 경우 true). */
    private Boolean mockTradingStarted;
}
