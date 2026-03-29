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
@Schema(description = "누적 수익금 정보")
public class EtfAnalysisCumulativeProfitDTO {

    @Schema(example = "1245000")
    private Integer amount;

    @ArraySchema(schema = @Schema(implementation = EtfAnalysisSeriesPointDTO.class))
    private List<EtfAnalysisSeriesPointDTO> series;
}
