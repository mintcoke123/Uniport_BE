package com.uniport.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "뉴스 목록/상세 아이템")
public class NewsItemResponseDTO {

    @Schema(example = "news_001")
    private String id;

    @Schema(example = "MARKET")
    private String category;

    @Schema(example = "시황")
    private String categoryLabel;

    @Schema(example = "코스피, 반도체 강세에 장 초반 상승 출발")
    private String title;

    @Schema(example = "외국인 순매수와 대형 기술주 반등이 지수 흐름을 이끌고 있어요.")
    private String summary;

    @Schema(example = "기사 본문입니다.")
    private String body;

    @Schema(example = "UniPort Markets")
    private String sourceName;

    @Schema(example = "2026-05-11T11:48:00+09:00")
    private String publishedAt;

    @JsonProperty("isFeatured")
    @Schema(example = "true")
    private Boolean isFeatured;

    @Schema(example = "https://example.com/news.png")
    private String thumbnailUrl;

    @Schema(example = "https://example.com/original-news")
    private String externalUrl;
}
