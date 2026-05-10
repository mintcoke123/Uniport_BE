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
@Schema(description = "인기 ETF 탐색 아이템")
public class EtfDiscoveryItemDTO {

    @Schema(example = "ETF_900", requiredMode = Schema.RequiredMode.REQUIRED)
    private String etfId;

    @Schema(example = "AI 테크", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(example = "성장 집중", requiredMode = Schema.RequiredMode.REQUIRED)
    private String subtitle;

    @Schema(example = "기술", requiredMode = Schema.RequiredMode.REQUIRED)
    private String theme;

    @Schema(example = "인기")
    private String badgeLabel;

    @Schema(example = "24.8", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double returnRate3M;

    @Schema(example = "24.8", requiredMode = Schema.RequiredMode.REQUIRED,
            description = "인기 ETF 목록 카드의 일일 예상 수익률 표시값. 기존 returnRate3M과 같은 값을 내려 호환성을 유지합니다.")
    private Double dailyExpectedReturnRate;

    @Schema(example = "12500", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer followerCount;

    @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean favorite;

    @Schema(example = "https://example.com/etf-ai-tech.png")
    private String thumbnailUrl;
}
