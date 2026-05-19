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
@Schema(description = "모의투자 홈 히어로 상태")
public class MockInvestmentHeroStatusDTO {

    @Schema(example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long teamId;

    @Schema(example = "수익률원정대", requiredMode = Schema.RequiredMode.REQUIRED)
    private String teamName;

    @Schema(example = "TEAM-20260519-001")
    private String teamGameId;

    @Schema(example = "3")
    private Integer rank;

    @Schema(example = "3위")
    private String rankLabel;

    @Schema(example = "128")
    private Integer totalParticipants;

    @Schema(example = "active", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

    @Schema(example = "2026-05-19T09:00:00+09:00")
    private String startedAt;

    @Schema(example = "2026-05-19T16:00:00+09:00")
    private String endsAt;

    @Schema(example = "14400")
    private Long remainingSeconds;

    @Schema(implementation = MockInvestmentCtaDTO.class)
    private MockInvestmentCtaDTO cta;
}
