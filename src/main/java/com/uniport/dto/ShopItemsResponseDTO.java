package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "포인트샵 상품 목록 응답")
public class ShopItemsResponseDTO {

    @ArraySchema(schema = @Schema(implementation = ShopItemDTO.class))
    private List<ShopItemDTO> items;

    @Schema(example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer page;

    @Schema(example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer size;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean hasNext;
}
