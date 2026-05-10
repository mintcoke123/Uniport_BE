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
@Schema(description = "뉴스 태그 정보")
public class StockNewsTagDTO {

    @Schema(example = "삼성전자")
    private String label;

    @Schema(example = "KRX")
    private String market;

    @Schema(example = "https://cdn.example.com/samsung.png")
    private String logoUrl;

    @Schema(implementation = StockVisualDTO.class)
    private StockVisualDTO visual;

    @Schema(example = "UP")
    private String direction;

    @Schema(example = "39.0")
    private Double changeRate;
}
