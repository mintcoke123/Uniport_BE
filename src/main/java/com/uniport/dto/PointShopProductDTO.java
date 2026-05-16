package com.uniport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "PRD 포인트샵 상품")
public class PointShopProductDTO {

    private String id;
    private String brand;
    private String name;
    private String category;
    private Integer pricePoint;
    private String imageUrl;
    private String status;
    private Integer stockCount;
}
