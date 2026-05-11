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
@Schema(description = "실시간 뉴스 상세 응답")
public class RealtimeNewsDetailResponseDTO {

    @Schema(example = "NEWS_20260511_002")
    private String newsId;

    @Schema(example = "COMPANY")
    private String category;

    @Schema(example = "종목")
    private String categoryLabel;

    @Schema(example = "삼성전자 반등")
    private String title;

    @Schema(example = "반도체 투자 심리가 회복되며 삼성전자 관련 뉴스 관심이 커지고 있어요.")
    private String summary;

    @Schema(example = "한국경제")
    private String sourceName;

    @Schema(example = "2026-05-11T16:10:00+09:00")
    private String publishedAt;

    @Schema(example = "https://www.hankyung.com/example")
    private String externalUrl;

    @Schema(example = "삼성전자 반등 뉴스는 반도체 업황 기대와 대형주 수급 회복을 함께 봐야 하는 흐름이에요.")
    private String coreSummary;

    @Schema(example = "POSITIVE")
    private String sentiment;

    @Schema(example = "호재")
    private String sentimentLabel;

    @Schema(example = "0.91")
    private Double sentimentScore;

    @Schema(example = "FinBERT가 금융 문맥상 긍정 신호로 분류했어요.")
    private String sentimentReason;

    private List<String> investmentPoints;

    private List<String> riskPoints;

    private List<RealtimeNewsRelatedStockDTO> relatedStocks;

    private List<RealtimeNewsSourceArticleDTO> sourceArticles;
}
