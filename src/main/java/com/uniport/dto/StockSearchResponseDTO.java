package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "주식 검색 응답")
public class StockSearchResponseDTO {

    @ArraySchema(schema = @Schema(implementation = StockSearchItemDTO.class))
    private List<StockSearchItemDTO> items;

    @Schema(example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer page;

    @Schema(example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer size;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean hasNext;
}
