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
@Schema(description = "교환 내역 아이템")
public class ShopRedemptionListItemDTO {

    @Schema(example = "REDEEM_201", requiredMode = Schema.RequiredMode.REQUIRED)
    private String redemptionId;

    @Schema(example = "ITEM_101", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemId;

    @Schema(example = "BHC")
    private String brand;

    @Schema(example = "후라이드 치킨 + 콜라 1.25L")
    private String name;

    @Schema(example = "https://cdn.example.com/item1.png")
    private String imageUrl;

    @Schema(example = "3000", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer usedPoint;

    @Schema(example = "1시간 전")
    private String requestedAgoLabel;

    @Schema(example = "2026-04-30", requiredMode = Schema.RequiredMode.REQUIRED)
    private String expiresAt;

    @Schema(example = "14", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer expiresInDays;

    @Schema(example = "COMPLETED", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

    @Schema(example = "사용 가능", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusLabel;
}
