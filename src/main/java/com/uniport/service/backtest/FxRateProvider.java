package com.uniport.service.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface FxRateProvider {

    BigDecimal getKrwRate(String currency, LocalDate date);
}
