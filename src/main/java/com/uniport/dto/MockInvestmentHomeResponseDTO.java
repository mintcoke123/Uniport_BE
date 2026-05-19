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
@Schema(description = "모의투자 신규 홈 응답")
public class MockInvestmentHomeResponseDTO {

    @Schema(example = "ALWAYS_ON", requiredMode = Schema.RequiredMode.REQUIRED)
    private String mode;

    @Schema(example = "2026-05-19T12:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private String serverTime;

    @Schema(implementation = MockInvestmentHeroStatusDTO.class)
    private MockInvestmentHeroStatusDTO heroStatus;

    @Schema(implementation = MockInvestmentCollectiveSignalDTO.class)
    private MockInvestmentCollectiveSignalDTO collectiveSignal;

    @Schema(implementation = MockInvestmentTopGroupInsightsDTO.class)
    private MockInvestmentTopGroupInsightsDTO topGroupInsights;

    @Schema(implementation = MockInvestmentLeaderboardsDTO.class)
    private MockInvestmentLeaderboardsDTO leaderboards;
}
