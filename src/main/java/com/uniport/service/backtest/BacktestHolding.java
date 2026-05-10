package com.uniport.service.backtest;

import java.math.BigDecimal;

public record BacktestHolding(String securityId, String name, BigDecimal weightPercent, String sector) {

    public BacktestHolding(String securityId, String name, BigDecimal weightPercent) {
        this(securityId, name, weightPercent, null);
    }
}
