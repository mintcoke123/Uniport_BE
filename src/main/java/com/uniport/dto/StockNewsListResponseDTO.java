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
@Schema(description = "주식 뉴스 목록 응답")
public class StockNewsListResponseDTO {

    private List<StockNewsListItemDTO> items;

    @Schema(example = "LATEST")
    private String sort;

    @Schema(example = "삼성전자")
    private String keyword;

    @Schema(example = "0")
    private Integer page;

    @Schema(example = "10")
    private Integer size;

    @Schema(example = "true")
    private Boolean hasNext;
}
