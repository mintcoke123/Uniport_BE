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
@Schema(description = "교환 미리보기 포인트 정보")
public class ShopPreviewPointDTO {

    @Schema(example = "5400", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer currentBalance;

    @Schema(example = "4500", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer requiredPoint;

    @Schema(example = "900", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer remainingBalance;
}
