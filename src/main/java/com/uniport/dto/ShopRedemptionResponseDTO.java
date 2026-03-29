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
@Schema(description = "기프티콘 교환 응답")
public class ShopRedemptionResponseDTO {

    @Schema(example = "REDEEM_201", requiredMode = Schema.RequiredMode.REQUIRED)
    private String redemptionId;

    @Schema(example = "ITEM_101", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemId;

    @Schema(example = "4500", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer usedPoint;

    @Schema(example = "900", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer remainingPoint;

    @Schema(example = "COMPLETED", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

    @Schema(example = "2026-03-11T19:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    private String createdAt;
}
