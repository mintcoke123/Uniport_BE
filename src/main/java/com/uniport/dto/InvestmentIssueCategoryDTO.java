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
@Schema(description = "투자 이슈 카테고리")
public class InvestmentIssueCategoryDTO {

    @Schema(example = "THEME")
    private String category;

    @Schema(example = "테마")
    private String label;
}
