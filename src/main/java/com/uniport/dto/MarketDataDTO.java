package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 명세 §3-5: marketData */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketDataDTO {

    private BigDecimal openPrice;
    private BigDecimal closePrice;
    private Long volume;
    private BigDecimal lowPrice;
    private BigDecimal highPrice;
}
