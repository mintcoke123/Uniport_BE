package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** 명세 §3-6: POST /api/trades 요청 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeRequestDTO {

    private Long stockId;           // 종목 ID (또는 코드로 매핑)
    private String side;            // "buy" | "sell"
    private Integer quantity;
    private BigDecimal pricePerShare;
    private String reason;
    private List<String> tags;
}
