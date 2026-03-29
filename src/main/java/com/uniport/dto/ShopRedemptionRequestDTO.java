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
@Schema(description = "기프티콘 교환 요청")
public class ShopRedemptionRequestDTO {

    @Schema(example = "ITEM_101", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemId;
}
