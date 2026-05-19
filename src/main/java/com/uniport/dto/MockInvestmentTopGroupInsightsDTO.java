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
@Schema(description = "모의투자 상위 그룹 인사이트")
public class MockInvestmentTopGroupInsightsDTO {

    @Schema(example = "YESTERDAY_RETURN", requiredMode = Schema.RequiredMode.REQUIRED)
    private String rankingBasis;

    @Schema(example = "20")
    private Integer totalCount;

    @Schema(example = "3")
    private Integer freeCount;

    @Schema(example = "17")
    private Integer lockedCount;

    @ArraySchema(schema = @Schema(implementation = MockInvestmentTopGroupInsightItemDTO.class))
    private List<MockInvestmentTopGroupInsightItemDTO> items;

    @Schema(example = "2026-05-19T11:45:00+09:00")
    private String updatedAt;
}
