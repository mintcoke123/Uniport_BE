package com.uniport.service.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BacktestNavPoint(LocalDate date, BigDecimal valueKrw) {
}
