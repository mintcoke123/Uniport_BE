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
@Schema(description = "모의투자 집단 매수/매도 시그널")
public class MockInvestmentCollectiveSignalDTO {

    @Schema(example = "삼성전자", requiredMode = Schema.RequiredMode.REQUIRED)
    private String stockName;

    @Schema(example = "005930", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ticker;

    @Schema(example = "BUY", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;

    @Schema(example = "72")
    private Integer consensusRate;

    @Schema(example = "94")
    private Integer participantCount;

    @Schema(example = "상위 그룹의 매수 의견이 강하게 모이고 있어요.")
    private String summary;

    @Schema(example = "2026-05-19T11:45:00+09:00")
    private String updatedAt;
}
