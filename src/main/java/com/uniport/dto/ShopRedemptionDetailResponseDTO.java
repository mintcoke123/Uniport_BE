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
@Schema(description = "교환 내역 상세 응답")
public class ShopRedemptionDetailResponseDTO {

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

    @Schema(example = "3000")
    private Integer usedPoint;

    @Schema(example = "2026-04-30")
    private String expiresAt;

    @Schema(example = "14")
    private Integer expiresInDays;

    @Schema(example = "카카오톡 발송")
    private String deliveryMethod;

    @Schema(example = "매장 직원에게 바코드를 보여주세요. 유효기간 내 1회 사용 가능합니다.")
    private String usageGuide;

    @Schema(example = "COMPLETED")
    private String status;

    @Schema(example = "사용 가능")
    private String statusLabel;

    @Schema(example = "BHC 앱 또는 매장에서 사용 가능")
    private String notice;
}
