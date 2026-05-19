package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "모의투자 상위 그룹 인사이트 아이템")
public class MockInvestmentTopGroupInsightItemDTO {

    @Schema(example = "1")
    private Integer rank;

    @Schema(example = "101")
    private Long groupId;

    @Schema(example = "수익률원정대")
    private String groupName;

    @Schema(example = "5")
    private Integer memberCount;

    @Schema(example = "삼성전자")
    private String stockName;

    @Schema(example = "005930")
    private String ticker;

    @Schema(example = "BUY")
    private String action;

    @Schema(example = "3.42")
    private BigDecimal yesterdayReturnRate;

    @Schema(example = "반도체 수요 회복과 실적 개선 기대가 커졌어요.")
    private String buyReason;

    @Schema(example = "단기 급등 이후 차익 실현 가능성을 봤어요.")
    private String sellReason;

    @Schema(example = "상위 그룹은 실적 회복 기대를 핵심 근거로 보고 있어요.")
    private String summary;

    @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean locked;

    @Schema(example = "TOP 4부터는 잠겨 있어요")
    private String lockedTitle;

    @Schema(example = "내 그룹 랭킹을 올리면 더 많은 인사이트를 볼 수 있어요.")
    private String lockedDescription;
}
