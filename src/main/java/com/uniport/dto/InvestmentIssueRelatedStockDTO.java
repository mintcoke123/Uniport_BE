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
@Schema(description = "투자 이슈 관련 종목")
public class InvestmentIssueRelatedStockDTO {

    @Schema(example = "삼성전자")
    private String name;

    @Schema(example = "005930")
    private String symbol;

    @Schema(example = "KOSPI")
    private String market;

    @Schema(example = "HBM 공급 확대 기대와 직접 관련")
    private String reason;
}
