package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "모의투자 홈 통합 요약 응답")
public class MockInvestingSummaryResponseDTO {

    @Schema(implementation = HomeActiveMatchDTO.class)
    private HomeActiveMatchDTO activeMatch;

    @Schema(implementation = HomeCompetitionSummaryDTO.class)
    private HomeCompetitionSummaryDTO ongoingCompetition;

    @Schema(implementation = HomeInvestmentSummaryDTO.class)
    private HomeInvestmentSummaryDTO myInvestment;

    @Schema(implementation = HomeMyGroupRankingDTO.class)
    private HomeMyGroupRankingDTO myGroupRanking;
}
