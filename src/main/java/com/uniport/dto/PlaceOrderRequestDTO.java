package com.uniport.dto;

import com.uniport.entity.OrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 주문 요청 DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceOrderRequestDTO {

    private String stockCode;
    /** 종목명 (표시용, 팀 보유 저장 시 함께 저장) */
    private String stockName;
    private int quantity;
    private BigDecimal price;
    private OrderType orderType;
}
