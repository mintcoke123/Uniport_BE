package com.uniport.service;

import com.uniport.service.backtest.BacktestHolding;
import com.uniport.service.backtest.BacktestNavPoint;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EtfBacktestEngineNotionRulesTest {

    private final EtfBacktestEngine engine = new EtfBacktestEngine();

    @Test
    void run_usesMonthEndIntegerSharesAndKeepsRemainingCash() {
        BacktestResult result = engine.run(BacktestRequest.builder()
                .principalAmountKrw(BigDecimal.valueOf(1_000))
                .rebalancePolicy("NONE")
                .holdings(List.of(new BacktestHolding("A", "Alpha", BigDecimal.valueOf(100), "Tech")))
                .priceSeriesBySecurityId(Map.of("A", List.of(
                        point("2026-01-15", "100"),
                        point("2026-01-31", "300"),
                        point("2026-02-10", "320"),
                        point("2026-02-28", "330")
                )))
                .benchmarkSeries(List.of(
                        point("2026-01-31", "100"),
                        point("2026-02-28", "110")
                ))
                .build());

        assertEquals(2, result.navSeries().size());
        assertNavPoint(result.navSeries().get(0), "2026-01-31", "1000.00");
        assertNavPoint(result.navSeries().get(1), "2026-02-28", "1090.00");
        assertEquals(0, new BigDecimal("9.0").compareTo(result.totalReturnPercent()));
        assertEquals(0, new BigDecimal("9.2").compareTo(result.cashWeightPercent()));
    }

    @Test
    void run_rebalancesAtSelectedMonthIntervalUsingIntegerShares() {
        BacktestResult result = engine.run(BacktestRequest.builder()
                .principalAmountKrw(BigDecimal.valueOf(1_000))
                .rebalancePolicy("MONTHLY")
                .holdings(List.of(
                        new BacktestHolding("A", "Alpha", BigDecimal.valueOf(50), "Tech"),
                        new BacktestHolding("B", "Beta", BigDecimal.valueOf(50), "Defensive")
                ))
                .priceSeriesBySecurityId(Map.of(
                        "A", List.of(
                                point("2026-01-31", "100"),
                                point("2026-02-28", "200"),
                                point("2026-03-31", "100")
                        ),
                        "B", List.of(
                                point("2026-01-31", "100"),
                                point("2026-02-28", "100"),
                                point("2026-03-31", "100")
                        )
                ))
                .benchmarkSeries(List.of(
                        point("2026-01-31", "100"),
                        point("2026-02-28", "100"),
                        point("2026-03-31", "100")
                ))
                .build());

        assertNavPoint(result.navSeries().get(2), "2026-03-31", "1200.00");
        assertEquals(0, new BigDecimal("20.0").compareTo(result.totalReturnPercent()));
        assertEquals(0, BigDecimal.ZERO.setScale(1).compareTo(result.cashWeightPercent()));
    }

    @Test
    void run_outputsNotionAnalysisMetricsForBenchmarkAndConcentration() {
        BacktestResult result = engine.run(BacktestRequest.builder()
                .principalAmountKrw(BigDecimal.valueOf(1_000))
                .rebalancePolicy("NONE")
                .holdings(List.of(
                        new BacktestHolding("A", "Alpha", BigDecimal.valueOf(60), "Growth"),
                        new BacktestHolding("B", "Beta", BigDecimal.valueOf(40), "Value")
                ))
                .priceSeriesBySecurityId(Map.of(
                        "A", List.of(
                                point("2026-01-31", "100"),
                                point("2026-02-28", "120"),
                                point("2026-03-31", "90"),
                                point("2026-04-30", "130")
                        ),
                        "B", List.of(
                                point("2026-01-31", "100"),
                                point("2026-02-28", "100"),
                                point("2026-03-31", "110"),
                                point("2026-04-30", "120")
                        )
                ))
                .benchmarkSeries(List.of(
                        point("2026-01-31", "100"),
                        point("2026-02-28", "105"),
                        point("2026-03-31", "103"),
                        point("2026-04-30", "106")
                ))
                .build());

        assertNotNull(result.sortinoRatio());
        assertNotNull(result.beta());
        assertNotNull(result.trackingErrorPercent());
        assertNotNull(result.informationRatio());
        assertNotNull(result.winRatePercent());
        assertNotNull(result.effectiveHoldings());
        assertNotNull(result.top5WeightPercent());
        assertNotNull(result.benchmarkAnnualizedReturnPercent());
        assertNotNull(result.benchmarkVolatilityPercent());
        assertNotNull(result.benchmarkMaxDrawdownPercent());
        assertTrue(result.top5WeightPercent().compareTo(result.top3WeightPercent()) >= 0);
    }

    private BacktestPricePoint point(String date, String price) {
        return new BacktestPricePoint(LocalDate.parse(date), new BigDecimal(price));
    }

    private void assertNavPoint(BacktestNavPoint point, String date, String value) {
        assertEquals(LocalDate.parse(date), point.date());
        assertEquals(0, new BigDecimal(value).compareTo(point.valueKrw()));
    }
}
