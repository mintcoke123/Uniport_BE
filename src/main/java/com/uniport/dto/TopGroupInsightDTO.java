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
@Schema(description = "상위 그룹 인사이트")
public class TopGroupInsightDTO {

    @Schema(example = "7", nullable = true)
    private Long groupId;

    @Schema(example = "세종대 퀀트 길드", nullable = true)
    private String groupName;

    @Schema(example = "12.5", nullable = true)
    private BigDecimal dailyReturnRate;

    @Schema(example = "NVDA", nullable = true)
    private String topPick;

    @Schema(example = "실적 발표 전 기술적 신호에서 매수세가 확인된 전략입니다.", nullable = true)
    private String comment;
}
