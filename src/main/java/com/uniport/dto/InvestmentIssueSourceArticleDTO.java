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
@Schema(description = "투자 이슈 근거 기사")
public class InvestmentIssueSourceArticleDTO {

    @Schema(example = "ARTICLE_20260519_1001")
    private String articleId;

    @Schema(example = "한국경제")
    private String sourceName;

    @Schema(example = "HBM 수요 기대 확대")
    private String title;

    @Schema(example = "AI 서버 투자 확대가 HBM 수요 기대와 연결되고 있다는 기사입니다.")
    private String summary;

    @Schema(example = "2026-05-19T09:30:00+09:00")
    private String publishedAt;

    @Schema(example = "https://www.hankyung.com/example")
    private String externalUrl;
}
