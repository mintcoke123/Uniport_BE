package com.uniport.service;

import com.uniport.service.backtest.BacktestHolding;
import com.uniport.service.backtest.BacktestNavPoint;
import com.uniport.service.backtest.BacktestPricePoint;
import com.uniport.service.backtest.BacktestRequest;
import com.uniport.service.backtest.BacktestResult;
import com.uniport.service.backtest.EtfBacktestEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EtfBacktestEngineMetricSweepTest {

    private static final int CASES_PER_PERIOD = 1_000;
    private static final int SINGLE_STOCK_PORTFOLIO_CASES = 10_000;
    private static final double SQRT_TRADING_DAYS = Math.sqrt(252.0);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TRANSACTION_FEE_RATE = new BigDecimal("0.00015");
    private static final BigDecimal SLIPPAGE_RATE = new BigDecimal("0.00025");

    private final EtfBacktestEngine engine = new EtfBacktestEngine();

    @Test
    void tenThousandSingleStockPortfolios_produceFiniteConsistentMetricsForEveryResultField() {
        for (int index = 0; index < SINGLE_STOCK_PORTFOLIO_CASES; index++) {
            int caseIndex = index;
            PeriodCase period = periodForIndex(index);
            String securityId = "US_SYNTH_" + String.format("%05d", index);
            String securityName = "Synthetic Stock " + index;
            String sector = "Sector " + (index % 13);
            BigDecimal principal = BigDecimal.valueOf(10_000_000L + index);
            LocalDate startDate = LocalDate.parse("2020-01-02").plusDays(index % 23L);
            List<BacktestPricePoint> priceSeries = syntheticPriceSeries(startDate, period.tradingDays(), index);
            List<BacktestPricePoint> benchmarkSeries = syntheticPriceSeries(startDate, period.tradingDays(), index + 17_001);

            BacktestResult result = engine.run(BacktestRequest.builder()
                    .principalAmountKrw(principal)
                    .transactionFeeRate(TRANSACTION_FEE_RATE)
                    .slippageRate(SLIPPAGE_RATE)
                    .rebalancePolicy(rebalancePolicy(index))
                    .periodLabel(period.name())
                    .benchmarkName("Synthetic Benchmark")
                    .holdings(List.of(new BacktestHolding(securityId, securityName, BigDecimal.valueOf(100), sector)))
                    .priceSeriesBySecurityId(Map.of(securityId, priceSeries))
                    .benchmarkSeries(benchmarkSeries)
                    .build());

            assertCompleteResult(index, result);
            assertMetric(period.name(), index, "initial NAV", principal.setScale(2, RoundingMode.HALF_UP), result.initialNavKrw());
            assertEquals(securityName, result.topHoldingName(), () -> "case " + caseIndex + " top holding name");
            assertEquals(sector, result.dominantSector(), () -> "case " + caseIndex + " dominant sector");
        }
    }

    @Test
    void randomizedMetricSweep_producesFiniteMonthlyMetricsForOneThreeAndFiveYearPeriods() {
        for (PeriodCase period : List.of(
                new PeriodCase("1Y", 252, 101L),
                new PeriodCase("3Y", 756, 303L),
                new PeriodCase("5Y", 1_260, 505L)
        )) {
            Random random = new Random(period.seed());
            for (int index = 0; index < CASES_PER_PERIOD; index++) {
                BacktestResult result = engine.run(randomRequest(period, random, index));
                assertCompleteResult(index, result);
            }
        }
    }

    @Test
    void monthlyBacktestIgnoresIntramonthNoiseAndUsesMonthEndPrices() {
        BacktestResult result = engine.run(BacktestRequest.builder()
                .principalAmountKrw(BigDecimal.valueOf(1_000L))
                .rebalancePolicy("MONTHLY")
                .holdings(List.of(new BacktestHolding("KRX_000001", "테스트", BigDecimal.valueOf(100), "테스트섹터")))
                .priceSeriesBySecurityId(Map.of("KRX_000001", List.of(
                        point("2026-01-02", "100"),
                        point("2026-01-31", "100"),
                        point("2026-02-03", "150"),
                        point("2026-02-28", "110")
                )))
                .benchmarkSeries(List.of(
                        point("2026-01-31", "100"),
                        point("2026-02-28", "110")
                ))
                .build());

        assertMetric("MONTHLY", 0, "month-end return", new BigDecimal("10.0"), result.totalReturnPercent());
    }

    private BacktestRequest randomRequest(PeriodCase period, Random random, int caseIndex) {
        int holdingCount = 1 + random.nextInt(5);
        List<Integer> weights = weights(holdingCount);
        List<BacktestHolding> holdings = new ArrayList<>();
        Map<String, List<BacktestPricePoint>> priceSeriesBySecurityId = new LinkedHashMap<>();
        LocalDate startDate = LocalDate.parse("2021-01-04").plusDays((long) caseIndex % 17L);

        for (int holdingIndex = 0; holdingIndex < holdingCount; holdingIndex++) {
            String securityId = "KRX_" + String.format("%06d", caseIndex * 10 + holdingIndex + 1);
            holdings.add(new BacktestHolding(
                    securityId,
                    "테스트" + holdingIndex,
                    BigDecimal.valueOf(weights.get(holdingIndex)),
                    "테스트섹터"
            ));
            priceSeriesBySecurityId.put(
                    securityId,
                    randomPriceSeries(startDate, period.tradingDays(), random, 80.0 + holdingIndex * 5.0)
            );
        }

        return BacktestRequest.builder()
                .principalAmountKrw(BigDecimal.valueOf(100_000_000L + caseIndex))
                .transactionFeeRate(TRANSACTION_FEE_RATE)
                .slippageRate(SLIPPAGE_RATE)
                .rebalancePolicy(rebalancePolicy(caseIndex))
                .periodLabel(period.name())
                .holdings(holdings)
                .priceSeriesBySecurityId(priceSeriesBySecurityId)
                .benchmarkSeries(randomPriceSeries(startDate, period.tradingDays(), random, 100.0))
                .build();
    }

    private List<Integer> weights(int holdingCount) {
        List<Integer> weights = new ArrayList<>();
        int baseWeight = 100 / holdingCount;
        int remaining = 100;
        for (int index = 0; index < holdingCount; index++) {
            int weight = index == holdingCount - 1 ? remaining : baseWeight;
            weights.add(weight);
            remaining -= weight;
        }
        return weights;
    }

    private String rebalancePolicy(int caseIndex) {
        return switch (caseIndex % 4) {
            case 0 -> "NONE";
            case 1 -> "MONTHLY";
            case 2 -> "QUARTERLY";
            default -> "SEMI_ANNUAL";
        };
    }

    private List<BacktestPricePoint> randomPriceSeries(LocalDate startDate, int tradingDays, Random random, double initialPrice) {
        List<BacktestPricePoint> points = new ArrayList<>();
        LocalDate date = startDate;
        double price = initialPrice;
        int generated = 0;
        while (generated <= tradingDays) {
            if (date.getDayOfWeek().getValue() <= 5) {
                if (generated > 0) {
                    double dailyReturn = -0.0002 + random.nextGaussian() * 0.018;
                    price = Math.max(1.0, price * (1.0 + dailyReturn));
                }
                points.add(new BacktestPricePoint(
                        date,
                        BigDecimal.valueOf(price).setScale(6, RoundingMode.HALF_UP)
                ));
                generated++;
            }
            date = date.plusDays(1);
        }
        return points;
    }

    private List<BacktestPricePoint> syntheticPriceSeries(LocalDate startDate, int tradingDays, int seed) {
        List<BacktestPricePoint> points = new ArrayList<>();
        LocalDate date = startDate;
        double price = 40.0 + (seed % 240);
        double drift = ((seed % 17) - 8) * 0.00003;
        int generated = 0;
        while (generated <= tradingDays) {
            if (date.getDayOfWeek().getValue() <= 5) {
                if (generated > 0) {
                    double wave = Math.sin((generated + seed % 31) / 11.0) * 0.0028;
                    double cycle = Math.cos((generated + seed % 19) / 23.0) * 0.0011;
                    double shock = ((generated + seed) % 149 == 0 ? -0.033 : 0.0)
                            + ((generated + seed) % 211 == 0 ? 0.026 : 0.0);
                    price = Math.max(1.0, price * (1.0 + drift + wave + cycle + shock));
                }
                points.add(new BacktestPricePoint(
                        date,
                        BigDecimal.valueOf(price).setScale(6, RoundingMode.HALF_UP)
                ));
                generated++;
            }
            date = date.plusDays(1);
        }
        return points;
    }

    private PeriodCase periodForIndex(int index) {
        return switch (index % 3) {
            case 0 -> new PeriodCase("1Y", 252, 101L);
            case 1 -> new PeriodCase("3Y", 756, 303L);
            default -> new PeriodCase("5Y", 1_260, 505L);
        };
    }

    private ExpectedMetrics expectedMetrics(BigDecimal principal,
                                            List<BacktestNavPoint> navSeries,
                                            List<BacktestPricePoint> benchmarkSeries,
                                            String topHoldingName,
                                            String dominantSector) {
        BigDecimal finalNav = navSeries.get(navSeries.size() - 1).valueKrw();
        BigDecimal totalReturn = finalNav.divide(principal, 12, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
        BigDecimal annualizedReturn = annualize(finalNav, principal, Math.max(1, navSeries.size() - 1));
        BigDecimal peak = null;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        List<BigDecimal> returns = new ArrayList<>();

        for (int index = 0; index < navSeries.size(); index++) {
            BigDecimal nav = navSeries.get(index).valueKrw();
            if (index > 0) {
                BigDecimal previousNav = navSeries.get(index - 1).valueKrw();
                returns.add(nav.divide(previousNav, 12, RoundingMode.HALF_UP).subtract(BigDecimal.ONE));
            }
            peak = peak == null || nav.compareTo(peak) > 0 ? nav : peak;
            BigDecimal drawdown = nav.divide(peak, 12, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
            if (drawdown.compareTo(maxDrawdown) < 0) {
                maxDrawdown = drawdown;
            }
        }

        BigDecimal volatility = BigDecimal.valueOf(stddev(returns) * SQRT_TRADING_DAYS);
        BigDecimal benchmarkReturn = benchmarkReturn(benchmarkSeries);
        BigDecimal excessReturn = benchmarkReturn != null ? totalReturn.subtract(benchmarkReturn) : null;
        BigDecimal sharpe = volatility.compareTo(BigDecimal.ZERO) > 0
                ? annualizedReturn.divide(volatility, 6, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP)
                : null;
        int riskScore = riskScore(percent(volatility), percent(maxDrawdown).abs());
        RiskLabel riskLabel = riskLabel(riskScore);
        return new ExpectedMetrics(
                finalNav.subtract(principal).setScale(2, RoundingMode.HALF_UP),
                percent(totalReturn),
                percent(annualizedReturn),
                percent(volatility),
                percent(maxDrawdown),
                benchmarkReturn != null ? percent(benchmarkReturn) : null,
                excessReturn != null ? percent(excessReturn) : null,
                sharpe,
                topHoldingName,
                dominantSector,
                riskScore,
                riskLabel.grade(),
                riskLabel.label(),
                Math.max(1, returns.size())
        );
    }

    private BigDecimal annualize(BigDecimal finalNav, BigDecimal initialNav, int tradingDays) {
        double ratio = finalNav.divide(initialNav, 12, RoundingMode.HALF_UP).doubleValue();
        return BigDecimal.valueOf(Math.pow(ratio, 252.0 / Math.max(1, tradingDays)) - 1.0);
    }

    private BigDecimal benchmarkReturn(List<BacktestPricePoint> benchmarkSeries) {
        if (benchmarkSeries == null || benchmarkSeries.size() < 2) {
            return null;
        }
        BigDecimal first = benchmarkSeries.get(0).adjustedCloseKrw();
        BigDecimal last = benchmarkSeries.get(benchmarkSeries.size() - 1).adjustedCloseKrw();
        return last.divide(first, 12, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
    }

    private int riskScore(BigDecimal volatilityPercent, BigDecimal drawdownAbsPercent) {
        return volatilityScore(volatilityPercent) + drawdownScore(drawdownAbsPercent) + 30;
    }

    private int volatilityScore(BigDecimal volatilityPercent) {
        double value = volatilityPercent.doubleValue();
        if (value < 8.0) return 5;
        if (value < 15.0) return 15;
        if (value < 25.0) return 25;
        if (value < 40.0) return 35;
        return 40;
    }

    private int drawdownScore(BigDecimal drawdownAbsPercent) {
        double value = drawdownAbsPercent.doubleValue();
        if (value < 5.0) return 3;
        if (value < 10.0) return 8;
        if (value < 20.0) return 16;
        if (value < 35.0) return 24;
        return 30;
    }

    private RiskLabel riskLabel(int score) {
        if (score <= 19) {
            return new RiskLabel("VERY_LOW", "매우 낮음");
        }
        if (score <= 39) {
            return new RiskLabel("LOW", "낮음");
        }
        if (score <= 59) {
            return new RiskLabel("MEDIUM", "보통");
        }
        if (score <= 79) {
            return new RiskLabel("HIGH", "높음");
        }
        return new RiskLabel("VERY_HIGH", "매우 높음");
    }

    private double stddev(List<BigDecimal> values) {
        if (values.size() < 2) {
            return 0.0d;
        }
        double mean = values.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0.0d);
        double variance = values.stream()
                .mapToDouble(value -> Math.pow(value.doubleValue() - mean, 2))
                .average()
                .orElse(0.0d);
        return Math.sqrt(variance);
    }

    private BacktestPricePoint point(String date, String adjustedCloseKrw) {
        return new BacktestPricePoint(LocalDate.parse(date), new BigDecimal(adjustedCloseKrw));
    }

    private BigDecimal percent(BigDecimal ratio) {
        return ratio.multiply(ONE_HUNDRED).setScale(1, RoundingMode.HALF_UP);
    }

    private void assertMetric(String period, int caseIndex, String metric, BigDecimal expected, BigDecimal actual) {
        assertEquals(
                0,
                expected.compareTo(actual),
                () -> period + " case " + caseIndex + " " + metric + " expected " + expected + " but was " + actual
        );
    }

    private void assertCompleteResult(int caseIndex, BacktestResult result) {
        assertPositive(caseIndex, "initialNavKrw", result.initialNavKrw());
        assertPositive(caseIndex, "finalNavKrw", result.finalNavKrw());
        assertNotNullMetric(caseIndex, "profitAmountKrw", result.profitAmountKrw());
        assertNotNullMetric(caseIndex, "totalReturnPercent", result.totalReturnPercent());
        assertNotNullMetric(caseIndex, "annualizedReturnPercent", result.annualizedReturnPercent());
        assertNotNullMetric(caseIndex, "volatilityPercent", result.volatilityPercent());
        assertNotNullMetric(caseIndex, "maxDrawdownPercent", result.maxDrawdownPercent());
        assertNotNullMetric(caseIndex, "benchmarkReturnPercent", result.benchmarkReturnPercent());
        assertNotNullMetric(caseIndex, "excessReturnPercent", result.excessReturnPercent());
        assertNotNullMetric(caseIndex, "sharpeRatio", result.sharpeRatio());
        assertNotNullMetric(caseIndex, "sortinoRatio", result.sortinoRatio());
        assertNotNullMetric(caseIndex, "beta", result.beta());
        assertNotNullMetric(caseIndex, "trackingErrorPercent", result.trackingErrorPercent());
        assertNotNullMetric(caseIndex, "informationRatio", result.informationRatio());
        assertNotNullMetric(caseIndex, "winRatePercent", result.winRatePercent());
        assertNotNullMetric(caseIndex, "benchmarkAnnualizedReturnPercent", result.benchmarkAnnualizedReturnPercent());
        assertNotNullMetric(caseIndex, "benchmarkVolatilityPercent", result.benchmarkVolatilityPercent());
        assertNotNullMetric(caseIndex, "benchmarkMaxDrawdownPercent", result.benchmarkMaxDrawdownPercent());
        assertNotNullMetric(caseIndex, "hhi", result.hhi());
        assertNotNullMetric(caseIndex, "effectiveHoldings", result.effectiveHoldings());
        assertNotNullMetric(caseIndex, "cashWeightPercent", result.cashWeightPercent());
        assertNotNullMetric(caseIndex, "topHoldingWeightPercent", result.topHoldingWeightPercent());
        assertNotNullMetric(caseIndex, "top3WeightPercent", result.top3WeightPercent());
        assertNotNullMetric(caseIndex, "top5WeightPercent", result.top5WeightPercent());
        assertNotNullMetric(caseIndex, "dominantSectorWeightPercent", result.dominantSectorWeightPercent());
        assertTrue(result.riskScore() >= 0 && result.riskScore() <= 100, "case " + caseIndex + " risk score out of range");
        assertTrue(result.tradingDays() > 0, "case " + caseIndex + " trading days should be positive");
        assertTrue(result.navSeries().size() > 1, "case " + caseIndex + " nav series should have at least two points");
        LocalDate previousDate = null;
        for (BacktestNavPoint point : result.navSeries()) {
            assertPositive(caseIndex, "nav", point.valueKrw());
            if (previousDate != null) {
                assertTrue(point.date().isAfter(previousDate), "case " + caseIndex + " nav dates should be increasing");
            }
            previousDate = point.date();
        }
    }

    private void assertPositive(int caseIndex, String metric, BigDecimal value) {
        assertNotNullMetric(caseIndex, metric, value);
        assertTrue(value.compareTo(BigDecimal.ZERO) > 0, "case " + caseIndex + " " + metric + " should be positive");
    }

    private void assertNotNullMetric(int caseIndex, String metric, BigDecimal value) {
        assertTrue(value != null, "case " + caseIndex + " " + metric + " should not be null");
    }

    private record PeriodCase(String name, int tradingDays, long seed) {
    }

    private record ExpectedMetrics(
            BigDecimal profitAmountKrw,
            BigDecimal totalReturnPercent,
            BigDecimal annualizedReturnPercent,
            BigDecimal volatilityPercent,
            BigDecimal maxDrawdownPercent,
            BigDecimal benchmarkReturnPercent,
            BigDecimal excessReturnPercent,
            BigDecimal sharpeRatio,
            String topHoldingName,
            String dominantSector,
            int riskScore,
            String riskGrade,
            String riskGradeLabel,
            int tradingDays
    ) {
    }

    private record RiskLabel(
            String grade,
            String label
    ) {
    }
}
