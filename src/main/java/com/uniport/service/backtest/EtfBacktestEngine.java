package com.uniport.service.backtest;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class EtfBacktestEngine {

    private final MonthlyBacktestCalculator monthlyBacktestCalculator = new MonthlyBacktestCalculator();

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final double SQRT_TRADING_DAYS = Math.sqrt(252.0);
    private static final String REBALANCE_MONTHLY = "MONTHLY";
    private static final String REBALANCE_QUARTERLY = "QUARTERLY";
    private static final String REBALANCE_SEMI_ANNUAL = "SEMI_ANNUAL";
    private static final String REBALANCE_NONE = "NONE";

    public BacktestResult run(BacktestRequest request) {
        return monthlyBacktestCalculator.run(request);
    }

    private BacktestResult runDailyFractionalBacktest(BacktestRequest request) {
        validate(request);
        BigDecimal principal = defaultPositive(request.getPrincipalAmountKrw(), BigDecimal.valueOf(100_000_000L));
        BigDecimal costRate = defaultZero(request.getTransactionFeeRate()).add(defaultZero(request.getSlippageRate()));
        String rebalancePolicy = normalizeRebalancePolicy(request.getRebalancePolicy());
        List<BacktestHolding> holdings = request.getHoldings();
        Map<String, NavigableMap<LocalDate, BigDecimal>> priceMaps = normalizePriceMaps(request.getPriceSeriesBySecurityId());
        List<LocalDate> dates = collectDates(priceMaps);
        if (dates.size() < 2) {
            throw new IllegalArgumentException("At least two historical price dates are required");
        }
        validateCoverage(holdings, priceMaps, dates.size());

        Map<String, BigDecimal> units = new HashMap<>();
        BigDecimal cash = principal;
        BigDecimal previousNav = null;
        BigDecimal peak = null;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        LocalDate previousRebalanceDate = null;
        List<BigDecimal> returns = new ArrayList<>();
        List<BacktestNavPoint> navSeries = new ArrayList<>();

        for (LocalDate date : dates) {
            BigDecimal navBeforeRebalance = cash.add(positionValue(units, priceMaps, date));
            boolean shouldRebalance = shouldRebalance(date, previousRebalanceDate, previousNav, rebalancePolicy);
            BigDecimal nav = navBeforeRebalance;

            if (shouldRebalance) {
                RebalanceState rebalanced = rebalance(principal, navBeforeRebalance, holdings, units, priceMaps, date, costRate);
                units = rebalanced.units();
                cash = rebalanced.cash();
                nav = cash.add(positionValue(units, priceMaps, date));
                previousRebalanceDate = date;
            }

            if (previousNav != null && previousNav.compareTo(BigDecimal.ZERO) > 0) {
                returns.add(nav.divide(previousNav, 12, RoundingMode.HALF_UP).subtract(BigDecimal.ONE));
            }
            peak = peak == null || nav.compareTo(peak) > 0 ? nav : peak;
            if (peak != null && peak.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal drawdown = nav.divide(peak, 12, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
                if (drawdown.compareTo(maxDrawdown) < 0) {
                    maxDrawdown = drawdown;
                }
            }
            navSeries.add(new BacktestNavPoint(date, nav.setScale(2, RoundingMode.HALF_UP)));
            previousNav = nav;
        }

        BigDecimal finalNav = navSeries.get(navSeries.size() - 1).valueKrw();
        BigDecimal totalReturn = finalNav.divide(principal, 12, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
        int tradingDays = Math.max(1, returns.size());
        BigDecimal annualizedReturn = annualize(finalNav, principal, tradingDays);
        BigDecimal volatility = BigDecimal.valueOf(stddev(returns) * SQRT_TRADING_DAYS);
        BigDecimal benchmarkReturn = calculateBenchmarkReturn(request.getBenchmarkSeries());
        BigDecimal excessReturn = benchmarkReturn != null ? totalReturn.subtract(benchmarkReturn) : null;
        BigDecimal sharpe = volatility.compareTo(BigDecimal.ZERO) > 0
                ? annualizedReturn.divide(volatility, 6, RoundingMode.HALF_UP)
                : null;

        Concentration concentration = concentration(holdings);
        Risk risk = risk(volatility, maxDrawdown, concentration);

        return new BacktestResult(
                principal.setScale(2, RoundingMode.HALF_UP),
                finalNav,
                finalNav.subtract(principal).setScale(2, RoundingMode.HALF_UP),
                percent(totalReturn),
                percent(annualizedReturn),
                percent(volatility),
                percent(maxDrawdown),
                benchmarkReturn != null ? percent(benchmarkReturn) : null,
                excessReturn != null ? percent(excessReturn) : null,
                sharpe != null ? sharpe.setScale(2, RoundingMode.HALF_UP) : null,
                concentration.hhi().setScale(4, RoundingMode.HALF_UP),
                concentration.topHoldingName(),
                concentration.topHoldingWeightPercent(),
                concentration.top3WeightPercent(),
                concentration.dominantSector(),
                concentration.dominantSectorWeightPercent(),
                risk.score(),
                risk.grade(),
                risk.label(),
                tradingDays,
                navSeries
        );
    }

    private RebalanceState rebalance(BigDecimal principal,
                                     BigDecimal navBeforeCost,
                                     List<BacktestHolding> holdings,
                                     Map<String, BigDecimal> previousUnits,
                                     Map<String, NavigableMap<LocalDate, BigDecimal>> priceMaps,
                                     LocalDate date,
                                     BigDecimal costRate) {
        BigDecimal tradeValue = BigDecimal.ZERO;
        for (BacktestHolding holding : holdings) {
            BigDecimal price = priceAt(priceMaps.get(holding.securityId()), date);
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal currentValue = previousUnits.getOrDefault(holding.securityId(), BigDecimal.ZERO).multiply(price);
            BigDecimal targetValue = navBeforeCost.multiply(weightRatio(holding));
            tradeValue = tradeValue.add(targetValue.subtract(currentValue).abs());
        }
        BigDecimal cost = tradeValue.multiply(costRate);
        BigDecimal navAfterCost = navBeforeCost.subtract(cost).max(BigDecimal.ZERO);
        Map<String, BigDecimal> nextUnits = new HashMap<>();
        BigDecimal invested = BigDecimal.ZERO;
        for (BacktestHolding holding : holdings) {
            BigDecimal price = priceAt(priceMaps.get(holding.securityId()), date);
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal targetValue = navAfterCost.multiply(weightRatio(holding));
            invested = invested.add(targetValue);
            nextUnits.put(holding.securityId(), targetValue.divide(price, 12, RoundingMode.HALF_UP));
        }
        BigDecimal cash = navAfterCost.subtract(invested).max(BigDecimal.ZERO);
        return new RebalanceState(nextUnits, cash);
    }

    private BigDecimal positionValue(Map<String, BigDecimal> units,
                                     Map<String, NavigableMap<LocalDate, BigDecimal>> priceMaps,
                                     LocalDate date) {
        BigDecimal value = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : units.entrySet()) {
            BigDecimal price = priceAt(priceMaps.get(entry.getKey()), date);
            if (price != null) {
                value = value.add(entry.getValue().multiply(price));
            }
        }
        return value;
    }

    private BigDecimal priceAt(NavigableMap<LocalDate, BigDecimal> prices, LocalDate date) {
        if (prices == null) {
            return null;
        }
        Map.Entry<LocalDate, BigDecimal> entry = prices.floorEntry(date);
        return entry != null ? entry.getValue() : null;
    }

    private boolean shouldRebalance(LocalDate date,
                                    LocalDate previousRebalanceDate,
                                    BigDecimal previousNav,
                                    String rebalancePolicy) {
        if (previousNav == null || previousRebalanceDate == null) {
            return true;
        }
        return switch (rebalancePolicy) {
            case REBALANCE_NONE -> false;
            case REBALANCE_QUARTERLY -> quarterIndex(date) != quarterIndex(previousRebalanceDate)
                    || date.getYear() != previousRebalanceDate.getYear();
            case REBALANCE_SEMI_ANNUAL -> halfYearIndex(date) != halfYearIndex(previousRebalanceDate)
                    || date.getYear() != previousRebalanceDate.getYear();
            default -> !YearMonth.from(date).equals(YearMonth.from(previousRebalanceDate));
        };
    }

    private int quarterIndex(LocalDate date) {
        return ((date.getMonthValue() - 1) / 3) + 1;
    }

    private int halfYearIndex(LocalDate date) {
        return date.getMonthValue() <= 6 ? 1 : 2;
    }

    private Map<String, NavigableMap<LocalDate, BigDecimal>> normalizePriceMaps(Map<String, List<BacktestPricePoint>> seriesById) {
        Map<String, NavigableMap<LocalDate, BigDecimal>> result = new HashMap<>();
        for (Map.Entry<String, List<BacktestPricePoint>> entry : seriesById.entrySet()) {
            NavigableMap<LocalDate, BigDecimal> prices = new TreeMap<>();
            for (BacktestPricePoint point : entry.getValue()) {
                if (point != null && point.date() != null && point.adjustedCloseKrw() != null
                        && point.adjustedCloseKrw().compareTo(BigDecimal.ZERO) > 0) {
                    prices.put(point.date(), point.adjustedCloseKrw());
                }
            }
            result.put(entry.getKey(), prices);
        }
        return result;
    }

    private List<LocalDate> collectDates(Map<String, NavigableMap<LocalDate, BigDecimal>> priceMaps) {
        TreeSet<LocalDate> dates = new TreeSet<>();
        priceMaps.values().forEach(map -> dates.addAll(map.keySet()));
        return new ArrayList<>(dates);
    }

    private void validateCoverage(List<BacktestHolding> holdings,
                                  Map<String, NavigableMap<LocalDate, BigDecimal>> priceMaps,
                                  int expectedDateCount) {
        for (BacktestHolding holding : holdings) {
            int count = priceMaps.getOrDefault(holding.securityId(), new TreeMap<>()).size();
            double coverage = expectedDateCount == 0 ? 0.0 : (double) count / expectedDateCount;
            if (coverage < 0.8d) {
                throw new IllegalArgumentException("Insufficient historical price coverage for " + holding.securityId());
            }
        }
    }

    private BigDecimal calculateBenchmarkReturn(List<BacktestPricePoint> benchmarkSeries) {
        if (benchmarkSeries == null || benchmarkSeries.size() < 2) {
            return null;
        }
        List<BacktestPricePoint> sorted = benchmarkSeries.stream()
                .filter(point -> point != null && point.adjustedCloseKrw() != null && point.adjustedCloseKrw().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(BacktestPricePoint::date))
                .toList();
        if (sorted.size() < 2) {
            return null;
        }
        BigDecimal first = sorted.get(0).adjustedCloseKrw();
        BigDecimal last = sorted.get(sorted.size() - 1).adjustedCloseKrw();
        return last.divide(first, 12, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
    }

    private Concentration concentration(List<BacktestHolding> holdings) {
        List<BacktestHolding> sorted = holdings.stream()
                .sorted(Comparator.comparing(BacktestHolding::weightPercent, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        BigDecimal hhi = BigDecimal.ZERO;
        for (BacktestHolding holding : holdings) {
            BigDecimal ratio = weightRatio(holding);
            hhi = hhi.add(ratio.multiply(ratio));
        }
        String topName = sorted.isEmpty() ? null : sorted.get(0).name();
        BigDecimal topWeight = sorted.isEmpty() ? BigDecimal.ZERO : normalizePercent(sorted.get(0).weightPercent());
        BigDecimal top3 = sorted.stream()
                .limit(3)
                .map(holding -> normalizePercent(holding.weightPercent()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> sectorWeights = holdings.stream()
                .filter(holding -> holding.sector() != null && !holding.sector().isBlank())
                .collect(Collectors.groupingBy(
                        BacktestHolding::sector,
                        Collectors.reducing(BigDecimal.ZERO, holding -> normalizePercent(holding.weightPercent()), BigDecimal::add)
                ));
        String dominantSector = null;
        BigDecimal dominantSectorWeight = null;
        for (Map.Entry<String, BigDecimal> entry : sectorWeights.entrySet()) {
            if (dominantSectorWeight == null || entry.getValue().compareTo(dominantSectorWeight) > 0) {
                dominantSector = entry.getKey();
                dominantSectorWeight = entry.getValue();
            }
        }
        return new Concentration(hhi, topName, topWeight, top3, dominantSector, dominantSectorWeight);
    }

    private Risk risk(BigDecimal volatility, BigDecimal maxDrawdown, Concentration concentration) {
        BigDecimal volatilityPercent = percent(volatility);
        BigDecimal drawdownAbsPercent = percent(maxDrawdown).abs();
        int score = volatilityScore(volatilityPercent)
                + drawdownScore(drawdownAbsPercent)
                + concentrationScore(concentration)
                + sectorScore(concentration.dominantSectorWeightPercent());
        if (score <= 19) {
            return new Risk(score, "VERY_LOW", "매우 낮음");
        }
        if (score <= 39) {
            return new Risk(score, "LOW", "낮음");
        }
        if (score <= 59) {
            return new Risk(score, "MEDIUM", "보통");
        }
        if (score <= 79) {
            return new Risk(score, "HIGH", "높음");
        }
        return new Risk(score, "VERY_HIGH", "매우 높음");
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

    private int concentrationScore(Concentration concentration) {
        int score = 0;
        if (concentration.hhi().compareTo(BigDecimal.valueOf(0.25)) >= 0) {
            score += 12;
        } else if (concentration.hhi().compareTo(BigDecimal.valueOf(0.15)) >= 0) {
            score += 6;
        }
        if (concentration.topHoldingWeightPercent().compareTo(BigDecimal.valueOf(40)) >= 0) {
            score += 5;
        }
        if (concentration.top3WeightPercent().compareTo(BigDecimal.valueOf(75)) >= 0) {
            score += 3;
        }
        return Math.min(20, score);
    }

    private int sectorScore(BigDecimal dominantSectorWeightPercent) {
        if (dominantSectorWeightPercent == null) {
            return 0;
        }
        return dominantSectorWeightPercent.compareTo(BigDecimal.valueOf(60)) >= 0 ? 10 : 0;
    }

    private BigDecimal annualize(BigDecimal finalNav, BigDecimal initialNav, int tradingDays) {
        if (initialNav.compareTo(BigDecimal.ZERO) <= 0 || finalNav.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        double ratio = finalNav.divide(initialNav, 12, RoundingMode.HALF_UP).doubleValue();
        return BigDecimal.valueOf(Math.pow(ratio, 252.0 / Math.max(1, tradingDays)) - 1.0);
    }

    private double stddev(List<BigDecimal> values) {
        if (values == null || values.size() < 2) {
            return 0.0d;
        }
        double mean = values.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0.0d);
        double variance = values.stream()
                .mapToDouble(value -> Math.pow(value.doubleValue() - mean, 2))
                .average()
                .orElse(0.0d);
        return Math.sqrt(variance);
    }

    private BigDecimal percent(BigDecimal ratio) {
        if (ratio == null) {
            return null;
        }
        return ratio.multiply(ONE_HUNDRED).setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal weightRatio(BacktestHolding holding) {
        return normalizePercent(holding.weightPercent()).divide(ONE_HUNDRED, 12, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizePercent(BigDecimal value) {
        return defaultZero(value).setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal defaultPositive(BigDecimal value, BigDecimal defaultValue) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0 ? value : defaultValue;
    }

    private String normalizeRebalancePolicy(String value) {
        String normalized = value == null || value.isBlank()
                ? REBALANCE_MONTHLY
                : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of(REBALANCE_MONTHLY, REBALANCE_QUARTERLY, REBALANCE_SEMI_ANNUAL, REBALANCE_NONE).contains(normalized)) {
            throw new IllegalArgumentException("rebalancePolicy must be one of MONTHLY, QUARTERLY, SEMI_ANNUAL, NONE");
        }
        return normalized;
    }

    private void validate(BacktestRequest request) {
        Objects.requireNonNull(request, "request is required");
        if (request.getHoldings() == null || request.getHoldings().isEmpty()) {
            throw new IllegalArgumentException("holdings are required");
        }
        if (request.getPriceSeriesBySecurityId() == null || request.getPriceSeriesBySecurityId().isEmpty()) {
            throw new IllegalArgumentException("historical prices are required");
        }
        BigDecimal totalWeight = request.getHoldings().stream()
                .map(BacktestHolding::weightPercent)
                .map(this::defaultZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.compareTo(ONE_HUNDRED) != 0) {
            throw new IllegalArgumentException("holding weights must sum to 100");
        }
    }

    private record RebalanceState(Map<String, BigDecimal> units, BigDecimal cash) {
    }

    private record Concentration(BigDecimal hhi,
                                 String topHoldingName,
                                 BigDecimal topHoldingWeightPercent,
                                 BigDecimal top3WeightPercent,
                                 String dominantSector,
                                 BigDecimal dominantSectorWeightPercent) {
    }

    private record Risk(int score, String grade, String label) {
    }
}
