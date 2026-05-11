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
@Schema(description = "실시간 뉴스 카테고리")
public class RealtimeNewsCategoryDTO {

    @Schema(example = "EARNINGS")
    private String category;

    @Schema(example = "실적")
    private String label;
}
