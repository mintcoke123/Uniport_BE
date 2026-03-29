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
@Schema(description = "보유 포인트 조회 응답")
public class PointBalanceResponseDTO {

    @Schema(example = "5400", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer pointBalance;
}
