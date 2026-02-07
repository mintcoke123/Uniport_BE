package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 명세 §3-1: 시장 지수 배열 항목 (id, name, value, change, changeRate) */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketIndexItemDTO {

    private Long id;
    private String name;
    private BigDecimal value;
    private BigDecimal change;
    private BigDecimal changeRate;
}
