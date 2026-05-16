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
@Schema(description = "PRD 포인트샵 상품 목록 응답")
public class PointShopProductsResponseDTO {

    private Integer myPoint;
    private List<String> categories;

    @ArraySchema(schema = @Schema(implementation = PointShopProductDTO.class))
    private List<PointShopProductDTO> products;
}
