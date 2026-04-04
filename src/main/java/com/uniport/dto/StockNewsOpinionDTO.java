package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 투자 의견")
public class StockNewsOpinionDTO {

    @Schema(example = "강력 호재")
    private String label;

    @Schema(example = "Bullish")
    private String englishLabel;
}
