package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "주식 뉴스 목록 아이템")
public class StockNewsListItemDTO {

    @Schema(example = "NEWS_101")
    private String newsId;

    @Schema(example = "삼성전자, 3분기 영업이익 2.4조원 기록... 반도체 부문 흑자 전환 성공적 안착")
    private String title;

    @Schema(example = "출처 · 일주일 전")
    private String sourceLabel;

    @Schema(example = "https://example.com/news/samsung-q3.png")
    private String imageUrl;

    private List<StockNewsTagDTO> tags;

    @Schema(example = "2026-03-28T09:00:00Z")
    private String publishedAt;

    @Schema(example = "124")
    private Integer popularityScore;
}
