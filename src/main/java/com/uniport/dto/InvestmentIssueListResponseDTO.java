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
@Schema(description = "투자 이슈 목록 응답")
public class InvestmentIssueListResponseDTO {

    private List<InvestmentIssueCategoryDTO> categories;

    @Schema(example = "THEME")
    private String selectedCategory;

    private InvestmentIssueItemDTO heroIssue;

    private List<InvestmentIssueItemDTO> items;

    @Schema(example = "issue_20260519_ai_infra_1a2b3c")
    private String nextCursor;

    @Schema(example = "true")
    private Boolean hasNext;
}
