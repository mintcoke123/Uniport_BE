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
@Schema(description = "진행 중 대회 요약")
public class HomeCompetitionSummaryDTO {

    @Schema(example = "3", nullable = true)
    private Long id;

    @Schema(example = "제 12회 실전 투자 대회", nullable = true)
    private String name;

    @Schema(example = "2026-11-30T23:59:59", nullable = true)
    private String endDate;
}
