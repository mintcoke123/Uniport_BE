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
@Schema(description = "채팅 투자 이슈 공유 카드 데이터")
public class InvestmentIssueSharePreviewDTO {

    @Schema(example = "issue_20260519_hbm_semiconductor_8f3a12")
    private String issueId;

    @Schema(example = "HBM 기대감에 반도체주 강세")
    private String title;

    @Schema(example = "positive")
    private String label;

    @Schema(example = "호재")
    private String labelText;

    @Schema(example = "AI 서버 투자 확대와 HBM 수요 증가 기대가 맞물리며 반도체 관련 종목들이 주목받고 있어요.")
    private String summary;

    @Schema(example = "[\"삼성전자\", \"SK하이닉스\"]")
    private List<String> relatedStocks;

    @Schema(example = "6")
    private Integer sourceCount;
}
