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
@Schema(description = "투자 이슈 목록 아이템")
public class InvestmentIssueItemDTO {

    @Schema(example = "issue_20260519_hbm_semiconductor_8f3a12")
    private String issueId;

    @Schema(example = "HBM 기대감에 반도체주 강세")
    private String title;

    @Schema(example = "THEME")
    private String category;

    @Schema(example = "테마")
    private String categoryLabel;

    @Schema(example = "positive")
    private String label;

    @Schema(example = "호재")
    private String labelText;

    @Schema(example = "AI 서버 투자 확대와 HBM 수요 증가 기대가 맞물리며 반도체 관련 종목들이 주목받고 있어요.")
    private String summary;

    @Schema(example = "[\"HBM 수요 확대 기대\", \"AI 인프라 투자 증가와 연결\"]")
    private List<String> reasonBullets;

    @Schema(example = "[\"단기 급등 종목은 변동성이 커질 수 있어요.\"]")
    private List<String> watchPoints;

    private List<InvestmentIssueRelatedStockDTO> relatedStocks;

    private List<InvestmentIssueRelatedEtfDTO> relatedEtfs;

    @Schema(example = "6")
    private Integer sourceCount;

    @Schema(example = "2026-05-19T09:30:00+09:00")
    private String publishedAt;

    @Schema(example = "2026-05-19T09:45:00+09:00")
    private String updatedAt;
}
