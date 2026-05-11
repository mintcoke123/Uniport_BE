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
@Schema(description = "실시간 뉴스 목록 응답")
public class RealtimeNewsListResponseDTO {

    private List<RealtimeNewsCategoryDTO> categories;

    @Schema(example = "ALL")
    private String selectedCategory;

    private RealtimeNewsItemDTO heroNews;

    private List<RealtimeNewsItemDTO> items;

    @Schema(example = "NEWS_20260511_002")
    private String nextCursor;

    @Schema(example = "true")
    private Boolean hasNext;
}
