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
@Schema(description = "Community post create request")
public class CommunityPostCreateRequestDTO {

    @Schema(example = "GENERAL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(example = "삼성전자 지금 매수 적기일까요?")
    private String title;

    @Schema(example = "외인 수급과 실적 흐름을 보면 반등 가능성이 높다고 봅니다.")
    private String content;

    @Schema(example = "REPORT_301")
    private String analysisReportId;

    @Schema(example = "005930")
    private String stockCode;

    @Schema(example = "삼성전자")
    private String stockName;

    @Schema(example = "BULLISH")
    private String sentiment;
}
