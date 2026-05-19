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
@Schema(description = "모의투자 리더보드 묶음")
public class MockInvestmentLeaderboardsDTO {

    @Schema(example = "2026-05-19T11:45:00+09:00")
    private String updatedAt;

    @ArraySchema(schema = @Schema(implementation = MockInvestmentLeaderboardTabDTO.class))
    private List<MockInvestmentLeaderboardTabDTO> tabs;
}
