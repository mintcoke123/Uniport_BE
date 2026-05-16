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
@Schema(description = "PRD 포인트샵 교환 신청 응답")
public class PointShopOrderResponseDTO {

    private String orderId;
    private String status;
    private Integer usedPoint;
    private Integer balanceAfter;
    private String message;
}
