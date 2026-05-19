package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "모의투자 리더보드 탭")
public class MockInvestmentLeaderboardTabDTO {

    @Schema(example = "TOTAL_ASSET", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(example = "총자산", requiredMode = Schema.RequiredMode.REQUIRED)
    private String label;

    @Schema(example = "GLOBAL")
    private String leaderboardScope;

    @Schema(example = "20260519")
    private Long tournamentId;

    @ArraySchema(schema = @Schema(implementation = MockInvestmentLeaderboardItemDTO.class))
    private List<MockInvestmentLeaderboardItemDTO> items;
}
