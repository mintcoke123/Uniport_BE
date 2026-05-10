package com.uniport.service.backtest;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class BacktestRequest {

    private BigDecimal principalAmountKrw;
    private BigDecimal transactionFeeRate;
    private BigDecimal slippageRate;
    private String rebalancePolicy;
    private String periodLabel;
    private String benchmarkName;
    private List<BacktestHolding> holdings;
    private Map<String, List<BacktestPricePoint>> priceSeriesBySecurityId;
    private List<BacktestPricePoint> benchmarkSeries;
}
