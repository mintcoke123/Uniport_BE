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
    private String market;
    private String logoUrl;
    private StockVisualDTO visual;
    private BigDecimal currentPrice;
    private BigDecimal openPrice;     // 시가
    private BigDecimal closePrice;    // 종가
    private BigDecimal lowPrice;      // 저가
    private BigDecimal highPrice;     // 고가
    private BigDecimal changeAmount;   // 전일 대비 변동 금액
    private BigDecimal changeRate;    // 변동률 (%)
    private Long volume;              // 거래량
}
