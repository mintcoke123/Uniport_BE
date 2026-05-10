package com.uniport.service;

import com.uniport.service.backtest.BacktestHolding;
import com.uniport.service.backtest.BacktestPricePoint;
import com.uniport.service.backtest.BacktestRequest;
import com.uniport.service.backtest.BacktestResult;
import com.uniport.service.backtest.EtfBacktestEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EtfBacktestEngineTest {

    private final EtfBacktestEngine engine = new EtfBacktestEngine();

    @Test
    void runBacktest_appliesDailyReturnsAndCalculatesTotalReturn() {
        BacktestRequest request = BacktestRequest.builder()
                .principalAmountKrw(BigDecimal.valueOf(100))
                .transactionFeeRate(BigDecimal.ZERO)
                .slippageRate(BigDecimal.ZERO)
                .holdings(List.of(new BacktestHolding("KRX_000001", "테스트", BigDecimal.valueOf(100))))
                .priceSeriesBySecurityId(Map.of("KRX_000001", List.of(
                        point("2026-01-02", "100"),
                        point("2026-01-03", "110"),
                        point("2026-01-04", "99")
                )))
                .build();

        BacktestResult result = engine.run(request);

        assertEquals(0, BigDecimal.valueOf(99).compareTo(result.finalNavKrw()));
        assertEquals(0, BigDecimal.valueOf(-1.0).compareTo(result.totalReturnPercent()));
    }

    @Test
    void runBacktest_calculatesMaximumDrawdownFromNavSeries() {
        BacktestRequest request = BacktestRequest.builder()
                .principalAmountKrw(BigDecimal.valueOf(100))
                .transactionFeeRate(BigDecimal.ZERO)
                .slippageRate(BigDecimal.ZERO)
                .holdings(List.of(new BacktestHolding("KRX_000001", "테스트", BigDecimal.valueOf(100))))
                .priceSeriesBySecurityId(Map.of("KRX_000001", List.of(
                        point("2026-01-02", "100"),
                        point("2026-01-03", "120"),
                        point("2026-01-04", "90"),
                        point("2026-01-05", "110")
                )))
                .build();

        BacktestResult result = engine.run(request);

        assertEquals(0, BigDecimal.valueOf(-25.0).compareTo(result.maxDrawdownPercent()));
    }

    @Test
    void runBacktest_respectsRebalancePolicyCadence() {
        Map<String, List<BacktestPricePoint>> prices = Map.of(
                "KRX_A", List.of(
                        point("2026-01-02", "100"),
                        point("2026-02-02", "200"),
                        point("2026-04-02", "200")
                ),
                "KRX_B", List.of(
                        point("2026-01-02", "100"),
                        point("2026-02-02", "100"),
                        point("2026-04-02", "200")
                )
        );

        assertEquals(0, new BigDecimal("225.00").compareTo(runWithPolicy("MONTHLY", prices).finalNavKrw()));
        assertEquals(0, new BigDecimal("200.00").compareTo(runWithPolicy("QUARTERLY", prices).finalNavKrw()));
        assertEquals(0, new BigDecimal("200.00").compareTo(runWithPolicy("SEMI_ANNUAL", prices).finalNavKrw()));
        assertEquals(0, new BigDecimal("200.00").compareTo(runWithPolicy("NONE", prices).finalNavKrw()));
    }

    private BacktestResult runWithPolicy(String policy, Map<String, List<BacktestPricePoint>> prices) {
        return engine.run(BacktestRequest.builder()
                .principalAmountKrw(BigDecimal.valueOf(100))
                .transactionFeeRate(BigDecimal.ZERO)
                .slippageRate(BigDecimal.ZERO)
                .rebalancePolicy(policy)
                .holdings(List.of(
                        new BacktestHolding("KRX_A", "A", BigDecimal.valueOf(50)),
                        new BacktestHolding("KRX_B", "B", BigDecimal.valueOf(50))
                ))
                .priceSeriesBySecurityId(prices)
                .build());
    }

    private BacktestPricePoint point(String date, String adjustedCloseKrw) {
        return new BacktestPricePoint(LocalDate.parse(date), new BigDecimal(adjustedCloseKrw));
    }
}
