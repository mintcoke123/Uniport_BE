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
@Schema(description = "종목 fallback 심볼 표시 정보")
public class StockVisualDTO {

    @Schema(example = "FALLBACK_SYMBOL", description = "항상 FALLBACK_SYMBOL")
    private String type;

    @Schema(example = "삼성", description = "fallback 심볼 텍스트")
    private String text;

    @Schema(example = "#EEF2FF")
    private String bgColor;

    @Schema(example = "#4F46E5")
    private String textColor;
}
