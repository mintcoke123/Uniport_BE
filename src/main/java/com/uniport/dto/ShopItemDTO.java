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
@Schema(description = "포인트샵 상품")
public class ShopItemDTO {

    @Schema(example = "ITEM_101", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemId;

    @Schema(example = "스타벅스")
    private String brand;

    @Schema(example = "아이스 카페 아메리카노 T", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(example = "https://cdn.example.com/item1.png")
    private String imageUrl;

    @Schema(example = "4500", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer requiredPoint;

    @Schema(example = "BEST")
    private String badge;

    @Schema(example = "AVAILABLE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String stockStatus;
}
