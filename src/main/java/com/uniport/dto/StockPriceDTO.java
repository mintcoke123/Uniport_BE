package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 주식 시세 정보 DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockPriceDTO {

    private String stockCode;
    private String stockName;
    private BigDecimal currentPrice;
    private BigDecimal changeAmount;   // 전일 대비 변동 금액
    private BigDecimal changeRate;    // 변동률 (%)
    private Long volume;              // 거래량
}
