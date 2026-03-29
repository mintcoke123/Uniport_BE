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
@Schema(description = "누적 수익금 그래프 포인트")
public class EtfAnalysisSeriesPointDTO {

    @Schema(example = "2025-03-01", requiredMode = Schema.RequiredMode.REQUIRED)
    private String date;

    @Schema(example = "1245000", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer value;
}
