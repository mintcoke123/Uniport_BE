package com.uniport.service.backtest;

import com.uniport.dto.IndexChartPriceItemDTO;
import com.uniport.entity.AssetMaster;
import com.uniport.entity.AssetPriceDaily;
import com.uniport.exception.ApiException;
import com.uniport.repository.AssetMasterRepository;
import com.uniport.repository.AssetPriceDailyRepository;
import com.uniport.service.KisApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiFunction;

@Service
public class KisHistoricalPriceProvider implements HistoricalPriceProvider {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String SOURCE_KIS_DOMESTIC_STOCK = "KIS_DOMESTIC_ADJUSTED_CLOSE";
    private static final String SOURCE_KIS_DOMESTIC_INDEX = "KIS_DOMESTIC_INDEX_DAILY_PRICE";
    private static final String SOURCE_KIS_OVERSEAS = "KIS_OVERSEAS_DAILY_PRICE";

    private final KisApiService kisApiService;
    private final FxRateProvider fxRateProvider;
    private final AssetPriceDailyRepository assetPriceDailyRepository;
    private final AssetMasterRepository assetMasterRepository;

    @Autowired
    public KisHistoricalPriceProvider(KisApiService kisApiService,
                                      FxRateProvider fxRateProvider,
                                      AssetPriceDailyRepository assetPriceDailyRepository,
                                      AssetMasterRepository assetMasterRepository) {
        this.kisApiService = kisApiService;
        this.fxRateProvider = fxRateProvider;
        this.assetPriceDailyRepository = assetPriceDailyRepository;
        this.assetMasterRepository = assetMasterRepository;
    }

    public KisHistoricalPriceProvider(KisApiService kisApiService,
                                      FxRateProvider fxRateProvider,
                                      AssetPriceDailyRepository assetPriceDailyRepository) {
        this(kisApiService, fxRateProvider, assetPriceDailyRepository, null);
    }

    public KisHistoricalPriceProvider(KisApiService kisApiService, FxRateProvider fxRateProvider) {
        this(kisApiService, fxRateProvider, null, null);
    }

    @Override
    public List<BacktestPricePoint> getSecurityPriceSeries(String securityId, LocalDate startDate, LocalDate endDate) {
        String normalizedSecurityId = securityId == null ? "" : securityId.trim().toUpperCase(Locale.ROOT);
        if (normalizedSecurityId.startsWith("CASH_")) {
            return syntheticSeries(startDate, endDate, BigDecimal.ZERO);
        }
        if (normalizedSecurityId.startsWith("BOND_")) {
            return syntheticSeries(startDate, endDate, annualYieldForBond(normalizedSecurityId));
        }
        List<BacktestPricePoint> cached = cachedPriceSeries(normalizedSecurityId, startDate, endDate);
        if (cacheCovers(cached, startDate, endDate)) {
            return cached;
        }
        OverseasSymbol overseasSymbol = toOverseasSymbol(securityId);
        if (overseasSymbol != null) {
            List<IndexChartPriceItemDTO> rows = fetchOverseasDailyRows(overseasSymbol, startDate, endDate);
            return toBacktestPoints(normalizedSecurityId, rows, "USD", SOURCE_KIS_OVERSEAS);
        }
        String stockCode = toDomesticStockCode(securityId);
        List<IndexChartPriceItemDTO> rows = fetchDailyRows(startDate, endDate,
                (chunkStart, chunkEnd) -> kisApiService.getStockDailyChartPrice(
                        stockCode,
                        chunkStart.format(YYYYMMDD),
                        chunkEnd.format(YYYYMMDD),
                        "D"
                ));
        return toBacktestPoints(normalizedSecurityId, rows, "KRW", SOURCE_KIS_DOMESTIC_STOCK);
    }

    private List<BacktestPricePoint> syntheticSeries(LocalDate startDate, LocalDate endDate, BigDecimal annualReturnRate) {
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

    @Override
    public List<BacktestPricePoint> getBenchmarkSeries(String benchmarkId, LocalDate startDate, LocalDate endDate) {
        String normalized = benchmarkId == null ? "" : benchmarkId.trim().toUpperCase(Locale.ROOT);
        String cacheAssetId = normalized.isBlank() ? "" : "BENCHMARK_" + normalized;
        List<BacktestPricePoint> cached = cachedPriceSeries(cacheAssetId, startDate, endDate);
        if (cacheCovers(cached, startDate, endDate)) {
            return cached;
        }
        if ("SP500".equals(normalized)) {
            List<IndexChartPriceItemDTO> rows = fetchOverseasDailyRows(new OverseasSymbol("AMS", "SPY"), startDate, endDate);
            return toBacktestPoints(cacheAssetId, rows, "USD", SOURCE_KIS_OVERSEAS);
        }
        if ("NASDAQ".equals(normalized)) {
            List<IndexChartPriceItemDTO> rows = fetchOverseasDailyRows(new OverseasSymbol("NAS", "QQQ"), startDate, endDate);
            return toBacktestPoints(cacheAssetId, rows, "USD", SOURCE_KIS_OVERSEAS);
        }
        if (!List.of("KOSPI", "KOSDAQ").contains(normalized)) {
            return List.of();
        }
        List<IndexChartPriceItemDTO> rows = fetchDailyRows(startDate, endDate,
                (chunkStart, chunkEnd) -> kisApiService.getIndexChartPrice(
                        normalized,
                        chunkStart.format(YYYYMMDD),
                        chunkEnd.format(YYYYMMDD),
                        "D"
                ));
        return toBacktestPoints(cacheAssetId, rows, "KRW", SOURCE_KIS_DOMESTIC_INDEX);
    }

    private List<IndexChartPriceItemDTO> fetchDailyRows(LocalDate startDate,
                                                        LocalDate endDate,
                                                        BiFunction<LocalDate, LocalDate, List<IndexChartPriceItemDTO>> fetcher) {
        List<IndexChartPriceItemDTO> rows = new ArrayList<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            LocalDate chunkEnd = cursor.plusDays(95).isAfter(endDate) ? endDate : cursor.plusDays(95);
            List<IndexChartPriceItemDTO> chunk = fetcher.apply(cursor, chunkEnd);
            if (chunk != null) {
                rows.addAll(chunk);
            }
            cursor = chunkEnd.plusDays(1);
        }
        return rows;
    }

    private List<IndexChartPriceItemDTO> fetchOverseasDailyRows(OverseasSymbol overseasSymbol,
                                                                LocalDate startDate,
                                                                LocalDate endDate) {
        List<IndexChartPriceItemDTO> rows = new ArrayList<>();
        LocalDate cursorEnd = endDate;
        LocalDate previousOldest = null;
        for (int attempt = 0; attempt < 40 && !cursorEnd.isBefore(startDate); attempt++) {
            List<IndexChartPriceItemDTO> chunk = kisApiService.getOverseasStockDailyChartPrice(
                    overseasSymbol.exchangeCode(),
                    overseasSymbol.symbol(),
                    cursorEnd.format(YYYYMMDD),
                    "0",
                    "1"
            );
            if (chunk == null || chunk.isEmpty()) {
                break;
            }
            rows.addAll(chunk);
            LocalDate oldest = chunk.stream()
                    .map(IndexChartPriceItemDTO::getDate)
                    .map(this::parseDate)
                    .filter(date -> date != null)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            if (oldest == null || !oldest.isAfter(startDate) || oldest.equals(previousOldest)) {
                break;
            }
            previousOldest = oldest;
            cursorEnd = oldest.minusDays(1);
        }
        return rows;
    }

    private String toDomesticStockCode(String securityId) {
        String normalized = securityId == null ? "" : securityId.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("KRX_")) {
            normalized = normalized.substring(4);
        }
        if (!normalized.matches("\\d{6}")) {
            throw new ApiException("Historical price data is only available for domestic 6-digit stock codes in MVP", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return normalized;
    }

    private OverseasSymbol toOverseasSymbol(String securityId) {
        String normalized = securityId == null ? "" : securityId.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("US_")) {
            return new OverseasSymbol(exchangeCodeForAsset(normalized), normalized.substring(3));
        }
        if (normalized.startsWith("NASDAQ_")) {
            return new OverseasSymbol("NAS", normalized.substring(7));
        }
        if (normalized.startsWith("NYSE_")) {
            return new OverseasSymbol("NYS", normalized.substring(5));
        }
        if (normalized.startsWith("AMEX_")) {
            return new OverseasSymbol("AMS", normalized.substring(5));
        }
        return null;
    }

    private String exchangeCodeForAsset(String assetId) {
        if (assetMasterRepository == null) {
            return "NAS";
        }
        return assetMasterRepository.findByAssetIdAndActiveTrue(assetId)
                .map(AssetMaster::getMarket)
                .map(this::toKisOverseasExchangeCode)
                .orElse("NAS");
    }

    private String toKisOverseasExchangeCode(String market) {
        String normalized = market == null ? "" : market.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "NYSE" -> "NYS";
            case "AMEX", "NYSE_ARCA", "ARCA" -> "AMS";
            case "NASDAQ" -> "NAS";
            default -> "NAS";
        };
    }

    private List<BacktestPricePoint> toBacktestPoints(String assetId,
                                                      List<IndexChartPriceItemDTO> rows,
                                                      String currency,
                                                      String source) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<CachedPriceCandidate> candidates = rows.stream()
                .filter(row -> row != null && row.getClose() != null && row.getClose().compareTo(BigDecimal.ZERO) > 0)
                .map(row -> {
                    LocalDate date = parseDate(row.getDate());
                    if (date == null) {
                        return null;
                    }
                    BigDecimal closeKrw = row.getClose().multiply(fxRateProvider.getKrwRate(currency, date));
                    return new CachedPriceCandidate(date, row.getClose(), closeKrw);
                })
                .filter(candidate -> candidate != null)
                .sorted(Comparator.comparing(CachedPriceCandidate::date))
                .toList();
        saveFetchedPrices(assetId, candidates, currency, source);
        return candidates.stream()
                .map(candidate -> new BacktestPricePoint(candidate.date(), candidate.closeKrw()))
                .toList();
    }

    private List<BacktestPricePoint> cachedPriceSeries(String assetId, LocalDate startDate, LocalDate endDate) {
        if (assetPriceDailyRepository == null
                || assetId == null
                || assetId.isBlank()
                || startDate == null
                || endDate == null
                || startDate.isAfter(endDate)) {
            return List.of();
        }
        return assetPriceDailyRepository
                .findByAssetIdAndTradeDateBetweenOrderByTradeDateAsc(assetId, startDate, endDate)
                .stream()
                .filter(row -> row.getTradeDate() != null && row.getCloseKrw() != null && row.getCloseKrw().compareTo(BigDecimal.ZERO) > 0)
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

    private void saveFetchedPrices(String assetId,
                                   List<CachedPriceCandidate> candidates,
                                   String currency,
                                   String source) {
        if (assetPriceDailyRepository == null || assetId == null || assetId.isBlank() || candidates == null || candidates.isEmpty()) {
            return;
        }
        List<AssetPriceDaily> rows = candidates.stream()
                .map(candidate -> {
                    Optional<AssetPriceDaily> existing = assetPriceDailyRepository.findByAssetIdAndTradeDate(assetId, candidate.date());
                    AssetPriceDaily row = existing.orElseGet(() -> AssetPriceDaily.builder()
                            .assetId(assetId)
                            .tradeDate(candidate.date())
                            .build());
                    row.setCloseNative(candidate.closeNative());
                    row.setCloseKrw(candidate.closeKrw());
                    row.setCurrency(currency);
                    row.setSource(source);
                    return row;
                })
                .toList();
        assetPriceDailyRepository.saveAll(rows);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.matches("\\d{8}")) {
            return LocalDate.parse(trimmed, YYYYMMDD);
        }
        return LocalDate.parse(trimmed);
    }

    private record OverseasSymbol(String exchangeCode, String symbol) {
    }

    private record CachedPriceCandidate(LocalDate date, BigDecimal closeNative, BigDecimal closeKrw) {
    }
}
