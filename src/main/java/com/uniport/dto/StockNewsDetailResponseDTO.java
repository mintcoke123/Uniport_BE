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
@Schema(description = "주식 뉴스 상세 응답")
public class StockNewsDetailResponseDTO {

    @Schema(example = "NEWS_101")
    private String newsId;

    @Schema(example = "삼성전자, 3분기 영업이익 2.4조원 기록... 반도체 부문 흑자 전환 성공적 안착")
    private String title;

    @Schema(example = "어쩌구경제")
    private String source;

    @Schema(example = "출처 · 뉴스 게시 일자")
    private String sourceLabel;

    @Schema(example = "2026-03-28T09:00:00Z")
    private String publishedAt;

    private List<StockNewsTagDTO> tags;

    @Schema(example = "반도체 업황 회복에 힘입어 삼성전자가 3분기 흑자 전환에 성공했으며...")
    private String aiSummary;

    private StockNewsOpinionDTO aiOpinion;

    private List<String> bodyParagraphs;

    @Schema(example = "https://example.com/news/samsung-q3-detail.png")
    private String imageUrl;

    private List<String> keyPoints;

    private StockNewsCompanyInfoDTO company;

    @Schema(example = "본 뉴스는 인공지능 알고리즘에 의해 요약 및 분석되었습니다. 투자의 책임은 투자자 본인에게 있습니다.")
    private String disclaimer;
}
