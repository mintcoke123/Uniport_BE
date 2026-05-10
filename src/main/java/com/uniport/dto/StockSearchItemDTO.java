package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "주식 검색 결과 아이템")
public class StockSearchItemDTO {

    @Schema(example = "KRX_005930", requiredMode = Schema.RequiredMode.REQUIRED)
    private String stockId;

    @Schema(example = "삼성전자", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(example = "005930", requiredMode = Schema.RequiredMode.REQUIRED)
    private String symbol;

    @Schema(example = "KOSPI", requiredMode = Schema.RequiredMode.REQUIRED)
    private String market;

    @Schema(example = "https://cdn.example.com/samsung.png")
    private String logoUrl;

    @Schema(implementation = StockVisualDTO.class)
    private StockVisualDTO visual;
}
