package com.uniport.service.backtest;

import com.uniport.entity.AssetPriceDaily;
import com.uniport.repository.AssetPriceDailyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CachedFallbackHistoricalPriceProvider implements HistoricalPriceProvider {

    private final AssetPriceDailyRepository assetPriceDailyRepository;
    private final boolean syntheticPriceFallbackEnabled;

    public CachedFallbackHistoricalPriceProvider(AssetPriceDailyRepository assetPriceDailyRepository,
                                                 @Value("${backtest.price-fallback.enabled:false}") boolean syntheticPriceFallbackEnabled) {
        this.assetPriceDailyRepository = assetPriceDailyRepository;
        this.syntheticPriceFallbackEnabled = syntheticPriceFallbackEnabled;
    }

    @Override
    public List<BacktestPricePoint> getSecurityPriceSeries(String securityId, LocalDate startDate, LocalDate endDate) {
        String normalizedSecurityId = normalizeAssetId(securityId);
        if (normalizedSecurityId.startsWith("CASH_")) {
            return fixedYieldSyntheticSeries(startDate, endDate, BigDecimal.ZERO);
        }
        if (normalizedSecurityId.startsWith("BOND_")) {
            return fixedYieldSyntheticSeries(startDate, endDate, annualYieldForBond(normalizedSecurityId));
        }
        return cachedOrFallbackSeries(normalizedSecurityId, startDate, endDate);
    }

    @Override
    public List<BacktestPricePoint> getBenchmarkSeries(String benchmarkId, LocalDate startDate, LocalDate endDate) {
        String normalizedBenchmarkId = normalizeAssetId(benchmarkId);
        String cacheAssetId = normalizedBenchmarkId.isBlank() ? "" : "BENCHMARK_" + normalizedBenchmarkId;
        return cachedOrFallbackSeries(cacheAssetId, startDate, endDate);
    }

    private List<BacktestPricePoint> cachedOrFallbackSeries(String assetId, LocalDate startDate, LocalDate endDate) {
        List<BacktestPricePoint> cached = cachedPriceSeries(assetId, startDate, endDate);
        if (cacheCovers(cached, startDate, endDate)) {
            return cached;
        }
        if (syntheticPriceFallbackEnabled) {
            return deterministicFallbackSeries(assetId, startDate, endDate);
        }
        return List.of();
    }

    private List<BacktestPricePoint> cachedPriceSeries(String assetId, LocalDate startDate, LocalDate endDate) {
        if (assetId == null
                || assetId.isBlank()
                || startDate == null
                || endDate == null
                || startDate.isAfter(endDate)) {
            return List.of();
        }
        return assetPriceDailyRepository
                .findByAssetIdAndTradeDateBetweenOrderByTradeDateAsc(assetId, startDate, endDate)
                .stream()
                .filter(row -> row.getTradeDate() != null
                        && row.getCloseKrw() != null
                        && row.getCloseKrw().compareTo(BigDecimal.ZERO) > 0)
                .map(row -> new BacktestPricePoint(row.getTradeDate(), row.getCloseKrw()))
                .toList();
    }

    private boolean cacheCovers(List<BacktestPricePoint> cached, LocalDate startDate, LocalDate endDate) {
        if (cached == null || cached.size() < 2 || startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return false;
        }
        LocalDate firstRequired = firstWeekdayOnOrAfter(startDate);
        LocalDate lastRequired = lastWeekdayOnOrBefore(endDate);
        if (firstRequired == null || lastRequired == null) {
            return false;
        }
        LocalDate firstCached = cached.get(0).date();
        LocalDate lastCached = cached.get(cached.size() - 1).date();
        return firstCached != null
                && lastCached != null
                && !firstCached.isAfter(firstRequired)
                && !lastCached.isBefore(lastRequired);
    }

    private List<BacktestPricePoint> deterministicFallbackSeries(String assetId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return List.of();
        }
        String normalized = assetId == null || assetId.isBlank() ? "LOCAL_SYNTHETIC" : normalizeAssetId(assetId);
        int hash = Math.floorMod(normalized.hashCode(), 10_000);
        BigDecimal base = BigDecimal.valueOf(1_000L + Math.floorMod(hash, 900));
        BigDecimal annualReturnRate = syntheticAnnualReturnRate(normalized, hash);
        List<BacktestPricePoint> points = new ArrayList<>();
        LocalDate cursor = startDate;
        int elapsedDays = 0;
        while (!cursor.isAfter(endDate)) {
            if (cursor.getDayOfWeek().getValue() <= 5) {
                BigDecimal growth = annualReturnRate
                        .multiply(BigDecimal.valueOf(elapsedDays))
                        .divide(BigDecimal.valueOf(365), 12, RoundingMode.HALF_UP);
                BigDecimal multiplier = BigDecimal.ONE.add(growth);
                points.add(new BacktestPricePoint(cursor, base.multiply(multiplier).setScale(6, RoundingMode.HALF_UP)));
            }
            elapsedDays++;
            cursor = cursor.plusDays(1);
        }
        return points;
    }

    private BigDecimal syntheticAnnualReturnRate(String normalizedAssetId, int hash) {
        if (normalizedAssetId.startsWith("BENCHMARK_")) {
            return new BigDecimal("0.055").add(BigDecimal.valueOf(Math.floorMod(hash, 31))
                    .divide(BigDecimal.valueOf(1_000), 12, RoundingMode.HALF_UP));
        }
        return new BigDecimal("0.045").add(BigDecimal.valueOf(Math.floorMod(hash, 81))
                .divide(BigDecimal.valueOf(1_000), 12, RoundingMode.HALF_UP));
    }

    private List<BacktestPricePoint> fixedYieldSyntheticSeries(LocalDate startDate, LocalDate endDate, BigDecimal annualReturnRate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return List.of();
        }
        ArrayList<BacktestPricePoint> points = new ArrayList<>();
        BigDecimal base = BigDecimal.valueOf(1000);
        int elapsedDays = 0;
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            if (cursor.getDayOfWeek().getValue() <= 5) {
                BigDecimal growth = annualReturnRate
                        .multiply(BigDecimal.valueOf(elapsedDays))
                        .divide(BigDecimal.valueOf(365), 12, RoundingMode.HALF_UP);
                points.add(new BacktestPricePoint(cursor, base.multiply(BigDecimal.ONE.add(growth)).setScale(6, RoundingMode.HALF_UP)));
            }
            elapsedDays++;
            cursor = cursor.plusDays(1);
        }
        return points;
    }

    private BigDecimal annualYieldForBond(String normalizedSecurityId) {
        if (normalizedSecurityId.contains("US_TREASURY_20Y")) {
            return new BigDecimal("0.044");
        }
        if (normalizedSecurityId.contains("US_TREASURY_10Y")) {
            return new BigDecimal("0.040");
        }
        if (normalizedSecurityId.contains("KR_GOV_10Y")) {
            return new BigDecimal("0.034");
        }
        return new BigDecimal("0.030");
    }

    private LocalDate firstWeekdayOnOrAfter(LocalDate date) {
        LocalDate cursor = date;
        for (int i = 0; i < 7; i++) {
            if (cursor.getDayOfWeek().getValue() <= 5) {
                return cursor;
            }
            cursor = cursor.plusDays(1);
        }
        return null;
    }

    private LocalDate lastWeekdayOnOrBefore(LocalDate date) {
        LocalDate cursor = date;
        for (int i = 0; i < 7; i++) {
            if (cursor.getDayOfWeek().getValue() <= 5) {
                return cursor;
            }
            cursor = cursor.minusDays(1);
        }
        return null;
    }

    private String normalizeAssetId(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
