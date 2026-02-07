package com.uniport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 명세 §3-5: financialData[] */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialDataItemDTO {

    private String quarter;
    private BigDecimal revenue;
    private BigDecimal grossProfit;
    private BigDecimal operatingProfit;
}
