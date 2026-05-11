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
@Schema(description = "뉴스 목록 응답")
public class NewsListResponseDTO {

    private NewsItemResponseDTO featured;

    private List<NewsItemResponseDTO> items;

    @Schema(example = "0")
    private Integer page;

    @Schema(example = "20")
    private Integer size;

    @Schema(example = "true")
    private Boolean hasNext;
}
