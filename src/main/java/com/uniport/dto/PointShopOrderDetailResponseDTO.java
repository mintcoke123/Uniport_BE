package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointShopOrderDetailResponseDTO {
    private String orderId;
    private String productName;
    private String brand;
    private Integer usedPoint;
    private String status;
    private String gifticonUrl;
    private String gifticonCode;
    private String expiredAt;
}
