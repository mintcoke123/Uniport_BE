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
@Schema(description = "기업 정보")
public class StockNewsCompanyInfoDTO {

    @Schema(example = "삼성전자")
    private String stockName;

    @Schema(example = "005930")
    private String stockCode;

    @Schema(example = "KRX")
    private String market;

    @Schema(example = "https://cdn.example.com/samsung.png")
    private String logoUrl;

    @Schema(implementation = StockVisualDTO.class)
    private StockVisualDTO visual;

    @Schema(example = "대한민국 삼성 그룹의 전자·반도체 제조 기업...")
    private String description;

    @Schema(example = "어쩌구저쩌구")
    private String source;

    @Schema(example = "/api/stocks/search?keyword=삼성전자")
    private String stockPricePath;
}
