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
@Schema(description = "실시간 뉴스 근거 기사")
public class RealtimeNewsSourceArticleDTO {

    @Schema(example = "ARTICLE_20260511_1001")
    private String articleId;

    @Schema(example = "한국경제")
    private String sourceName;

    @Schema(example = "삼성전자 반등")
    private String title;

    @Schema(example = "반도체 대형주 중심으로 투자 심리가 개선되고 있다는 내용의 기사입니다.")
    private String summary;

    @Schema(example = "2026-05-11T16:10:00+09:00")
    private String publishedAt;

    @Schema(example = "https://www.hankyung.com/example")
    private String externalUrl;
}
