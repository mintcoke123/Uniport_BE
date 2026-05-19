package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "모의투자 리더보드 아이템")
public class MockInvestmentLeaderboardItemDTO {

    @Schema(example = "1")
    private Integer rank;

    @Schema(example = "101")
    private Long groupId;

    @Schema(example = "수익률원정대")
    private String groupName;

    @Schema(example = "TEAM-20260519-001")
    private String teamGameId;

    @Schema(example = "2026-05-19T09:00:00+09:00")
    private String startedAt;

    @Schema(example = "2026-05-19T16:00:00+09:00")
    private String endsAt;

    @Schema(example = "12500000")
    private BigDecimal totalAssetAmount;

    @Schema(example = "4.25")
    private BigDecimal returnRate;

    @Schema(example = "https://cdn.example.com/groups/101.png")
    private String avatarUrl;
}
