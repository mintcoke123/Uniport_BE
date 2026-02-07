package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 시장 지수 정보 DTO (KOSPI, KOSDAQ 등).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketIndexDTO {

    private String indexCode;   // e.g. "001", "101"
    private String indexName;   // e.g. "KOSPI", "KOSDAQ"
    private BigDecimal value;   // 현재 지수값
    private BigDecimal changeAmount;
    private BigDecimal changeRate;  // 등락률 (%)
}
