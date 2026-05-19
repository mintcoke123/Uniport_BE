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
@Schema(description = "투자 이슈 관련 ETF")
public class InvestmentIssueRelatedEtfDTO {

    @Schema(example = "KODEX 반도체")
    private String name;

    @Schema(example = "091160")
    private String symbol;

    @Schema(example = "반도체 업종 전반에 투자하는 ETF")
    private String reason;
}
