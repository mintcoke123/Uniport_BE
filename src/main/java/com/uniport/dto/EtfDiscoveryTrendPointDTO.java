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
@Schema(description = "인기 ETF 추이 포인트")
public class EtfDiscoveryTrendPointDTO {

    @Schema(example = "2026-01-01", requiredMode = Schema.RequiredMode.REQUIRED)
    private String date;

    @Schema(example = "124.5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double value;
}
