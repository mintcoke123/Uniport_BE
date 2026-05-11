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
@Schema(description = "실시간 뉴스 관련 종목")
public class RealtimeNewsRelatedStockDTO {

    @Schema(example = "KR_005930")
    private String stockId;

    @Schema(example = "삼성전자")
    private String name;

    @Schema(example = "005930")
    private String symbol;

    @Schema(example = "KOSPI")
    private String market;
}
