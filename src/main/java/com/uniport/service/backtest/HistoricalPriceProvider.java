package com.uniport.service.backtest;

import java.time.LocalDate;
import java.util.List;

public interface HistoricalPriceProvider {

    List<BacktestPricePoint> getSecurityPriceSeries(String securityId, LocalDate startDate, LocalDate endDate);

    default List<BacktestPricePoint> getSecurityPriceSeriesForEligibility(String securityId,
                                                                          LocalDate startDate,
                                                                          LocalDate endDate) {
        return getSecurityPriceSeries(securityId, startDate, endDate);
    }

    List<BacktestPricePoint> getBenchmarkSeries(String benchmarkId, LocalDate startDate, LocalDate endDate);
}
