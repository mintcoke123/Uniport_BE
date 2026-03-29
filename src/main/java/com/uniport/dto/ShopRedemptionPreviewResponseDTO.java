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
@Schema(description = "포인트샵 교환 미리보기 응답")
public class ShopRedemptionPreviewResponseDTO {

    private ShopItemDTO item;

    private ShopPreviewPointDTO point;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean canRedeem;

    @Schema(example = "보유 포인트가 부족합니다")
    private String reason;
}
