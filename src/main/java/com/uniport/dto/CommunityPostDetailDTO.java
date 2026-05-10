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
@Schema(description = "Community post detail")
public class CommunityPostDetailDTO {

    @Schema(example = "POST_101", requiredMode = Schema.RequiredMode.REQUIRED)
    private String postId;

    @Schema(example = "GENERAL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    private CommunityAuthorDTO author;

    @Schema(example = "삼성전자 지금 매수 적기일까요?")
    private String title;

    @Schema(example = "외인 수급과 실적 흐름을 보면 반등 가능성이 높다고 봅니다.")
    private String content;

    @Schema(example = "005930")
    private String stockCode;

    @Schema(example = "삼성전자")
    private String stockName;

    @Schema(example = "KRX")
    private String market;

    @Schema(example = "https://cdn.example.com/samsung.png")
    private String logoUrl;

    @Schema(implementation = StockVisualDTO.class)
    private StockVisualDTO visual;

    @Schema(example = "BULLISH")
    private String sentiment;

    @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer likeCount;

    @Schema(example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer commentCount;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean liked;

    @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean isMine;

    @Schema(example = "2026-03-11T08:20:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    private String createdAt;

    @Schema(example = "2026-03-11T09:00:00Z")
    private String updatedAt;
}
