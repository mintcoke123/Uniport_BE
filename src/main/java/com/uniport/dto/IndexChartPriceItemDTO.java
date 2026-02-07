package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 지수 일/주/월/년 차트 시세 1건 DTO (KIS inquire-daily-indexchartprice).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndexChartPriceItemDTO {

    private String date;       // 조회일자 (yyyyMMdd 등)
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
}
