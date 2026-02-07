package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 명세 §3-6: POST /api/trades 응답 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeResponseDTO {

    private boolean success;
    private String message;
    private String orderId;
    private String executedAt;   // ISO 8601
}
