package com.uniport.service.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BacktestPricePoint(LocalDate date, BigDecimal adjustedCloseKrw) {
}
