package com.uniport.dto;

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
@Schema(description = "PRD 포인트샵 상품 상세 응답")
public class PointShopProductDetailResponseDTO {

    private String id;
    private String brand;
    private String name;
    private String category;
    private Integer pricePoint;
    private Integer myPoint;
    private Integer pointAfterExchange;
    private String imageUrl;
    private String description;
    private List<String> notice;
    private String status;
    private Boolean canExchange;
}
