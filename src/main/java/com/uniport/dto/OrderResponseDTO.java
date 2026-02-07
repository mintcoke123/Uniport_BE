package com.uniport.dto;

import com.uniport.entity.OrderStatus;
import com.uniport.entity.OrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 처리 결과 응답 DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponseDTO {

    private Long orderId;
    private String stockCode;
    private int quantity;
    private BigDecimal price;
    private OrderType orderType;
    private OrderStatus status;
    private LocalDateTime orderDate;
    /** KIS API 주문 체결번호 (외부 연동 시) */
    private String externalOrderNo;
    /** 처리 결과 메시지 */
    private String message;
}
