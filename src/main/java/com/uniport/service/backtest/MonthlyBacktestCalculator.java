package com.uniport.service.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

class MonthlyBacktestCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final double SQRT_MONTHS = Math.sqrt(12.0d);
    private static final String REBALANCE_MONTHLY = "MONTHLY";
    private static final String REBALANCE_QUARTERLY = "QUARTERLY";
    private static final String REBALANCE_SEMI_ANNUAL = "SEMI_ANNUAL";
    private static final String REBALANCE_NONE = "NONE";

    BacktestResult run(BacktestRequest request) {
        validate(request);
        BigDecimal principal = positiveOrDefault(request.getPrincipalAmountKrw(), BigDecimal.valueOf(100_000_000L));
        String rebalancePolicy = normalizeRebalancePolicy(request.getRebalancePolicy());
        int rebalanceIntervalMonths = rebalanceIntervalMonths(rebalancePolicy);
        List<BacktestHolding> holdings = request.getHoldings();
        Map<String, NavigableMap<YearMonth, MonthlyPricePoint>> monthlyPriceMaps = toMonthlyPriceMaps(
                request.getPriceSeriesBySecurityId()
        );
        NavigableMap<YearMonth, MonthlyPricePoint> benchmarkPrices = toMonthlyPriceMap(request.getBenchmarkSeries());
        List<YearMonth> months = collectCommonMonths(monthlyPriceMaps, benchmarkPrices);
        if (months.size() < 2) {
            throw new IllegalArgumentException("At least two common month-end price points are required");
        }
        validateCoverage(holdings, monthlyPriceMaps, months.size());

        Map<String, BigDecimal> units = new HashMap<>();
        BigDecimal cash = principal;
        List<BacktestNavPoint> navSeries = new ArrayList<>();
        List<BigDecimal> navValues = new ArrayList<>();

        for (int index = 0; index < months.size(); index++) {
            YearMonth month = months.get(index);
            BigDecimal navBeforeRebalance = index == 0
                    ? principal
                    : cash.add(positionValue(units, monthlyPriceMaps, month));
            if (index == 0 || shouldRebalance(index, rebalanceIntervalMonths)) {
                Allocation allocation = allocateIntegerShares(navBeforeRebalance, holdings, monthlyPriceMaps, month);
                units = allocation.units();
                cash = allocation.cash();
            }
            BigDecimal nav = cash.add(positionValue(units, monthlyPriceMaps, month));
            navValues.add(nav);
            navSeries.add(new BacktestNavPoint(month.atEndOfMonth(), nav.setScale(2, RoundingMode.HALF_UP)));
        }

        List<BigDecimal> portfolioReturns = returns(navValues);
        List<BigDecimal> benchmarkValues = benchmarkPrices.isEmpty()
                ? List.of()
                : months.stream()
                .map(month -> benchmarkPrices.get(month).price())
                .toList();
        List<BigDecimal> benchmarkReturns = returns(benchmarkValues);
        BigDecimal finalNav = navValues.get(navValues.size() - 1);
        BigDecimal cumulativeReturn = ratio(finalNav, principal).subtract(BigDecimal.ONE);
        BigDecimal annualizedReturn = annualized(finalNav, principal, portfolioReturns.size());
        BigDecimal volatility = ratioFromDouble(stddev(portfolioReturns) * SQRT_MONTHS);
        BigDecimal maxDrawdown = maxDrawdown(navValues);
        BigDecimal benchmarkReturn = benchmarkValues.size() >= 2
                ? ratio(benchmarkValues.get(benchmarkValues.size() - 1), benchmarkValues.get(0)).subtract(BigDecimal.ONE)
                : null;
        BigDecimal benchmarkAnnualizedReturn = benchmarkValues.size() >= 2
                ? annualized(benchmarkValues.get(benchmarkValues.size() - 1), benchmarkValues.get(0), benchmarkReturns.size())
                : null;
        BigDecimal benchmarkVolatility = benchmarkReturns.size() >= 2
                ? ratioFromDouble(stddev(benchmarkReturns) * SQRT_MONTHS)
                : null;
        BigDecimal benchmarkMaxDrawdown = benchmarkValues.size() >= 2 ? maxDrawdown(benchmarkValues) : null;
        BigDecimal excessReturn = benchmarkReturn != null ? cumulativeReturn.subtract(benchmarkReturn) : null;
        BigDecimal sharpe = riskAdjustedRatio(portfolioReturns, stddev(portfolioReturns));
        BigDecimal sortino = sortinoRatio(portfolioReturns);
        BigDecimal beta = beta(portfolioReturns, benchmarkReturns);
        BigDecimal trackingError = trackingError(portfolioReturns, benchmarkReturns);
        BigDecimal informationRatio = informationRatio(portfolioReturns, benchmarkReturns);
        BigDecimal winRate = winRate(portfolioReturns, benchmarkReturns);
        Concentration concentration = concentration(holdings, units, monthlyPriceMaps, months.get(months.size() - 1), finalNav, cash);
        Risk risk = risk(volatility, maxDrawdown, concentration);

        return new BacktestResult(
                principal.setScale(2, RoundingMode.HALF_UP),
                finalNav.setScale(2, RoundingMode.HALF_UP),
                finalNav.subtract(principal).setScale(2, RoundingMode.HALF_UP),
                percent(cumulativeReturn),
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
                concentration.top5WeightPercent(),
                concentration.dominantSector(),
                concentration.dominantSectorWeightPercent(),
                concentration.effectiveHoldings(),
                concentration.cashWeightPercent(),
                benchmarkAnnualizedReturn != null ? percent(benchmarkAnnualizedReturn) : null,
                benchmarkVolatility != null ? percent(benchmarkVolatility) : null,
                benchmarkMaxDrawdown != null ? percent(benchmarkMaxDrawdown) : null,
                sortino != null ? sortino.setScale(2, RoundingMode.HALF_UP) : null,
                beta != null ? beta.setScale(2, RoundingMode.HALF_UP) : null,
                trackingError != null ? percent(trackingError) : null,
                informationRatio != null ? informationRatio.setScale(2, RoundingMode.HALF_UP) : null,
                winRate != null ? percent(winRate) : null,
                risk.score(),
                risk.grade(),
                risk.label(),
                portfolioReturns.size(),
                navSeries
        );
    }

    private void validate(BacktestRequest request) {
        if (request == null || request.getHoldings() == null || request.getHoldings().isEmpty()) {
            throw new IllegalArgumentException("At least one holding is required");
        }
        if (request.getPriceSeriesBySecurityId() == null || request.getPriceSeriesBySecurityId().isEmpty()) {
            throw new IllegalArgumentException("Historical price series are required");
        }
    }

    private Map<String, NavigableMap<YearMonth, MonthlyPricePoint>> toMonthlyPriceMaps(Map<String, List<BacktestPricePoint>> seriesById) {
        Map<String, NavigableMap<YearMonth, MonthlyPricePoint>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<BacktestPricePoint>> entry : seriesById.entrySet()) {
            result.put(entry.getKey(), toMonthlyPriceMap(entry.getValue()));
        }
        return result;
    }

    private NavigableMap<YearMonth, MonthlyPricePoint> toMonthlyPriceMap(List<BacktestPricePoint> series) {
        NavigableMap<YearMonth, MonthlyPricePoint> monthly = new TreeMap<>();
        if (series == null) {
            return monthly;
        }
        series.stream()
                .filter(point -> point != null && point.date() != null && point.adjustedCloseKrw() != null)
                .filter(point -> point.adjustedCloseKrw().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(BacktestPricePoint::date))
                .forEach(point -> monthly.put(
                        YearMonth.from(point.date()),
                        new MonthlyPricePoint(point.date(), point.adjustedCloseKrw())
                ));
        return monthly;
    }

    private List<YearMonth> collectCommonMonths(Map<String, NavigableMap<YearMonth, MonthlyPricePoint>> priceMaps,
                                                NavigableMap<YearMonth, MonthlyPricePoint> benchmarkPrices) {
        TreeSet<YearMonth> months = null;
        for (NavigableMap<YearMonth, MonthlyPricePoint> map : priceMaps.values()) {
            if (months == null) {
                months = new TreeSet<>(map.keySet());
            } else {
                months.retainAll(map.keySet());
            }
        }
        if (months == null) {
            return List.of();
        }
        if (benchmarkPrices != null && benchmarkPrices.size() >= 2) {
            months.retainAll(benchmarkPrices.keySet());
        }
        return new ArrayList<>(months);
    }

    private void validateCoverage(List<BacktestHolding> holdings,
                                  Map<String, NavigableMap<YearMonth, MonthlyPricePoint>> priceMaps,
                                  int monthCount) {
        for (BacktestHolding holding : holdings) {
            NavigableMap<YearMonth, MonthlyPricePoint> prices = priceMaps.get(holding.securityId());
            if (prices == null || prices.size() < 2) {
                throw new IllegalArgumentException("Missing historical prices for " + holding.securityId());
            }
            if (prices.size() < Math.max(2, monthCount / 2)) {
                throw new IllegalArgumentException("Insufficient historical price coverage for " + holding.securityId());
            }
        }
    }

    private boolean shouldRebalance(int monthIndex, int rebalanceIntervalMonths) {
        return rebalanceIntervalMonths > 0 && monthIndex > 0 && monthIndex % rebalanceIntervalMonths == 0;
    }

    private Allocation allocateIntegerShares(BigDecimal nav,
                                             List<BacktestHolding> holdings,
                                             Map<String, NavigableMap<YearMonth, MonthlyPricePoint>> priceMaps,
                                             YearMonth month) {
        Map<String, BigDecimal> units = new HashMap<>();
        BigDecimal invested = BigDecimal.ZERO;
        for (BacktestHolding holding : holdings) {
            BigDecimal price = priceAt(priceMaps, holding.securityId(), month);
            BigDecimal targetValue = nav.multiply(weightRatio(holding));
            BigDecimal quantity = price.compareTo(BigDecimal.ZERO) > 0
                    ? targetValue.divide(price, 0, RoundingMode.FLOOR)
                    : BigDecimal.ZERO;
            units.put(holding.securityId(), quantity);
            invested = invested.add(quantity.multiply(price));
        }
        return new Allocation(units, nav.subtract(invested).max(BigDecimal.ZERO));
    }

    private BigDecimal positionValue(Map<String, BigDecimal> units,
                                     Map<String, NavigableMap<YearMonth, MonthlyPricePoint>> priceMaps,
                                     YearMonth month) {
        BigDecimal value = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : units.entrySet()) {
            value = value.add(entry.getValue().multiply(priceAt(priceMaps, entry.getKey(), month)));
        }
        return value;
    }

    private BigDecimal priceAt(Map<String, NavigableMap<YearMonth, MonthlyPricePoint>> priceMaps, String securityId, YearMonth month) {
        NavigableMap<YearMonth, MonthlyPricePoint> prices = priceMaps.get(securityId);
        if (prices == null || !prices.containsKey(month)) {
            return BigDecimal.ZERO;
        }
        return prices.get(month).price();
    }

    private List<BigDecimal> returns(List<BigDecimal> values) {
        List<BigDecimal> result = new ArrayList<>();
        for (int index = 1; index < values.size(); index++) {
            BigDecimal previous = values.get(index - 1);
            BigDecimal current = values.get(index);
            if (previous.compareTo(BigDecimal.ZERO) > 0) {
                result.add(current.divide(previous, 12, RoundingMode.HALF_UP).subtract(BigDecimal.ONE));
            }
        }
        return result;
    }

    private BigDecimal annualized(BigDecimal finalValue, BigDecimal initialValue, int returnCount) {
        if (returnCount <= 0 || initialValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        double ratio = finalValue.divide(initialValue, 12, RoundingMode.HALF_UP).doubleValue();
        return ratioFromDouble(Math.pow(ratio, 12.0d / returnCount) - 1.0d);
    }

    private BigDecimal maxDrawdown(List<BigDecimal> values) {
        BigDecimal peak = null;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            peak = peak == null || value.compareTo(peak) > 0 ? value : peak;
            if (peak.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal drawdown = value.divide(peak, 12, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
                if (drawdown.compareTo(maxDrawdown) < 0) {
                    maxDrawdown = drawdown;
                }
            }
        }
        return maxDrawdown;
    }

    private BigDecimal riskAdjustedRatio(List<BigDecimal> returns, double denominator) {
        if (returns.isEmpty() || denominator <= 0.0d) {
            return null;
        }
        return ratioFromDouble(mean(returns) / denominator * SQRT_MONTHS);
    }

    private BigDecimal sortinoRatio(List<BigDecimal> returns) {
        if (returns.isEmpty()) {
            return null;
        }
        double downsideVariance = returns.stream()
                .mapToDouble(value -> Math.min(0.0d, value.doubleValue()))
                .map(value -> value * value)
                .average()
                .orElse(0.0d);
        double downsideDeviation = Math.sqrt(downsideVariance);
        return riskAdjustedRatio(returns, downsideDeviation);
    }

    private BigDecimal beta(List<BigDecimal> portfolioReturns, List<BigDecimal> benchmarkReturns) {
        int size = Math.min(portfolioReturns.size(), benchmarkReturns.size());
        if (size < 2) {
            return null;
        }
        List<BigDecimal> portfolio = portfolioReturns.subList(0, size);
        List<BigDecimal> benchmark = benchmarkReturns.subList(0, size);
        double variance = variance(benchmark);
        if (variance <= 0.0d) {
            return null;
        }
        return ratioFromDouble(covariance(portfolio, benchmark) / variance);
    }

    private BigDecimal trackingError(List<BigDecimal> portfolioReturns, List<BigDecimal> benchmarkReturns) {
        List<BigDecimal> activeReturns = activeReturns(portfolioReturns, benchmarkReturns);
        if (activeReturns.size() < 2) {
            return null;
        }
        return ratioFromDouble(stddev(activeReturns) * SQRT_MONTHS);
    }

    private BigDecimal informationRatio(List<BigDecimal> portfolioReturns, List<BigDecimal> benchmarkReturns) {
        List<BigDecimal> activeReturns = activeReturns(portfolioReturns, benchmarkReturns);
        if (activeReturns.size() < 2) {
            return null;
        }
        double stddev = stddev(activeReturns);
        if (stddev <= 0.0d) {
            return null;
        }
        return ratioFromDouble(mean(activeReturns) / stddev * SQRT_MONTHS);
    }

    private BigDecimal winRate(List<BigDecimal> portfolioReturns, List<BigDecimal> benchmarkReturns) {
        int size = Math.min(portfolioReturns.size(), benchmarkReturns.size());
        if (size == 0) {
            return null;
        }
        int wins = 0;
        for (int index = 0; index < size; index++) {
            if (portfolioReturns.get(index).compareTo(benchmarkReturns.get(index)) > 0) {
                wins++;
            }
        }
        return BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(size), 12, RoundingMode.HALF_UP);
    }

    private List<BigDecimal> activeReturns(List<BigDecimal> portfolioReturns, List<BigDecimal> benchmarkReturns) {
        int size = Math.min(portfolioReturns.size(), benchmarkReturns.size());
        List<BigDecimal> result = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            result.add(portfolioReturns.get(index).subtract(benchmarkReturns.get(index)));
        }
        return result;
    }

    private Concentration concentration(List<BacktestHolding> holdings,
                                        Map<String, BigDecimal> units,
                                        Map<String, NavigableMap<YearMonth, MonthlyPricePoint>> priceMaps,
                                        YearMonth finalMonth,
                                        BigDecimal finalNav,
                                        BigDecimal cash) {
        List<HoldingWeight> weights = holdings.stream()
                .map(holding -> {
                    BigDecimal value = units.getOrDefault(holding.securityId(), BigDecimal.ZERO)
                            .multiply(priceAt(priceMaps, holding.securityId(), finalMonth));
                    BigDecimal weightPercent = finalNav.compareTo(BigDecimal.ZERO) > 0
                            ? value.divide(finalNav, 12, RoundingMode.HALF_UP).multiply(ONE_HUNDRED)
                            : BigDecimal.ZERO;
                    return new HoldingWeight(holding, value, weightPercent);
                })
                .sorted(Comparator.comparing(HoldingWeight::weightPercent).reversed())
                .toList();
        BigDecimal hhi = weights.stream()
                .map(weight -> weight.weightPercent().divide(ONE_HUNDRED, 12, RoundingMode.HALF_UP))
                .map(ratio -> ratio.multiply(ratio))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal effectiveHoldings = hhi.compareTo(BigDecimal.ZERO) > 0
                ? BigDecimal.ONE.divide(hhi, 2, RoundingMode.HALF_UP)
                : null;
        BigDecimal top1 = weights.isEmpty() ? BigDecimal.ZERO : normalizePercent(weights.get(0).weightPercent());
        BigDecimal top3 = topWeight(weights, 3);
        BigDecimal top5 = topWeight(weights, 5);
        String topHoldingName = weights.isEmpty() ? null : weights.get(0).holding().name();
        Map<String, BigDecimal> sectorWeights = weights.stream()
                .filter(weight -> weight.holding().sector() != null && !weight.holding().sector().isBlank())
                .collect(Collectors.groupingBy(
                        weight -> weight.holding().sector(),
                        Collectors.reducing(BigDecimal.ZERO, HoldingWeight::weightPercent, BigDecimal::add)
                ));
        String dominantSector = null;
        BigDecimal dominantSectorWeight = null;
        for (Map.Entry<String, BigDecimal> entry : sectorWeights.entrySet()) {
            if (dominantSectorWeight == null || entry.getValue().compareTo(dominantSectorWeight) > 0) {
                dominantSector = entry.getKey();
                dominantSectorWeight = entry.getValue();
            }
        }
        BigDecimal cashWeight = finalNav.compareTo(BigDecimal.ZERO) > 0
                ? cash.divide(finalNav, 12, RoundingMode.HALF_UP).multiply(ONE_HUNDRED)
                : BigDecimal.ZERO;
        return new Concentration(
                hhi,
                topHoldingName,
                top1,
                top3,
                top5,
                dominantSector,
                dominantSectorWeight != null ? normalizePercent(dominantSectorWeight) : BigDecimal.ZERO,
                effectiveHoldings,
                normalizePercent(cashWeight)
        );
    }

    private BigDecimal topWeight(List<HoldingWeight> weights, int limit) {
        return weights.stream()
                .limit(limit)
                .map(HoldingWeight::weightPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(1, RoundingMode.HALF_UP);
    }

    private Risk risk(BigDecimal volatility, BigDecimal maxDrawdown, Concentration concentration) {
        int score = Math.min(100, volatilityScore(volatility) + drawdownScore(maxDrawdown) + concentrationScore(concentration));
        if (score >= 70) {
            return new Risk(score, "HIGH", "공격형");
        }
        if (score >= 40) {
            return new Risk(score, "MEDIUM", "균형형");
        }
        return new Risk(score, "LOW", "안정형");
    }

    private int volatilityScore(BigDecimal volatility) {
        if (volatility.compareTo(new BigDecimal("0.30")) >= 0) {
            return 40;
        }
        if (volatility.compareTo(new BigDecimal("0.18")) >= 0) {
            return 28;
        }
        return 14;
    }

    private int drawdownScore(BigDecimal maxDrawdown) {
        BigDecimal absolute = maxDrawdown.abs();
        if (absolute.compareTo(new BigDecimal("0.35")) >= 0) {
            return 35;
        }
        if (absolute.compareTo(new BigDecimal("0.20")) >= 0) {
            return 24;
        }
        return 10;
    }

    private int concentrationScore(Concentration concentration) {
        if (concentration.topHoldingWeightPercent().compareTo(BigDecimal.valueOf(40)) >= 0
                || concentration.hhi().compareTo(new BigDecimal("0.30")) >= 0) {
            return 25;
        }
        if (concentration.top3WeightPercent().compareTo(BigDecimal.valueOf(70)) >= 0
                || concentration.hhi().compareTo(new BigDecimal("0.18")) >= 0) {
            return 16;
        }
        return 8;
    }

    private double mean(List<BigDecimal> values) {
        return values.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0.0d);
    }

    private double variance(List<BigDecimal> values) {
        if (values.size() < 2) {
            return 0.0d;
        }
        double mean = mean(values);
        return values.stream()
                .mapToDouble(value -> Math.pow(value.doubleValue() - mean, 2))
                .average()
                .orElse(0.0d);
    }

    private double stddev(List<BigDecimal> values) {
        return Math.sqrt(variance(values));
    }

    private double covariance(List<BigDecimal> left, List<BigDecimal> right) {
        int size = Math.min(left.size(), right.size());
        double leftMean = mean(left.subList(0, size));
        double rightMean = mean(right.subList(0, size));
        double sum = 0.0d;
        for (int index = 0; index < size; index++) {
            sum += (left.get(index).doubleValue() - leftMean) * (right.get(index).doubleValue() - rightMean);
        }
        return sum / size;
    }

    private BigDecimal percent(BigDecimal ratio) {
        if (ratio == null) {
            return null;
        }
        return ratio.multiply(ONE_HUNDRED).setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 12, RoundingMode.HALF_UP);
    }

    private BigDecimal ratioFromDouble(double value) {
        if (!Double.isFinite(value)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(value);
    }

    private BigDecimal weightRatio(BacktestHolding holding) {
        return normalizePercent(holding.weightPercent()).divide(ONE_HUNDRED, 12, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizePercent(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal positiveOrDefault(BigDecimal value, BigDecimal defaultValue) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0 ? value : defaultValue;
    }

    private String normalizeRebalancePolicy(String value) {
        if (value == null || value.isBlank()) {
            return REBALANCE_MONTHLY;
        }
        return value.trim().toUpperCase();
    }

    private int rebalanceIntervalMonths(String value) {
        if (REBALANCE_NONE.equals(value)) {
            return 0;
        }
        if (REBALANCE_QUARTERLY.equals(value)) {
            return 3;
        }
        if (REBALANCE_SEMI_ANNUAL.equals(value)) {
            return 6;
        }
        if (value.matches("\\d+")) {
            int months = Integer.parseInt(value);
            return Math.max(1, Math.min(12, months));
        }
        return 1;
    }

    private record MonthlyPricePoint(LocalDate date, BigDecimal price) {
    }

    private record Allocation(Map<String, BigDecimal> units, BigDecimal cash) {
    }

    private record HoldingWeight(BacktestHolding holding, BigDecimal valueKrw, BigDecimal weightPercent) {
    }

    private record Concentration(BigDecimal hhi,
                                 String topHoldingName,
                                 BigDecimal topHoldingWeightPercent,
                                 BigDecimal top3WeightPercent,
                                 BigDecimal top5WeightPercent,
                                 String dominantSector,
                                 BigDecimal dominantSectorWeightPercent,
                                 BigDecimal effectiveHoldings,
                                 BigDecimal cashWeightPercent) {
    }

    private record Risk(int score, String grade, String label) {
    }
}
