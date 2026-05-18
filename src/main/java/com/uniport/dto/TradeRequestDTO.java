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
    /** 매칭방 ID. 여러 방에 참여 중일 때는 이 값으로 투자 컨텍스트를 명시한다. */
    private String roomId;
    private String reason;
    private List<String> tags;
}
