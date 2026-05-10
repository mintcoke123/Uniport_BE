package com.uniport.service.backtest;

import com.uniport.dto.IndexChartPriceItemDTO;
import com.uniport.exception.ApiException;
import com.uniport.service.KisApiService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;

@Service
public class KisHistoricalPriceProvider implements HistoricalPriceProvider {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final KisApiService kisApiService;
    private final FxRateProvider fxRateProvider;

    public KisHistoricalPriceProvider(KisApiService kisApiService, FxRateProvider fxRateProvider) {
        this.kisApiService = kisApiService;
        this.fxRateProvider = fxRateProvider;
    }

    @Override
    public List<BacktestPricePoint> getSecurityPriceSeries(String securityId, LocalDate startDate, LocalDate endDate) {
        OverseasSymbol overseasSymbol = toOverseasSymbol(securityId);
        if (overseasSymbol != null) {
            List<IndexChartPriceItemDTO> rows = fetchOverseasDailyRows(overseasSymbol, startDate, endDate);
            return toBacktestPoints(rows, "USD");
        }
        String stockCode = toDomesticStockCode(securityId);
        List<IndexChartPriceItemDTO> rows = fetchDailyRows(startDate, endDate,
                (chunkStart, chunkEnd) -> kisApiService.getStockDailyChartPrice(
                        stockCode,
                        chunkStart.format(YYYYMMDD),
                        chunkEnd.format(YYYYMMDD),
                        "D"
                ));
        return toBacktestPoints(rows, "KRW");
    }

    @Override
    public List<BacktestPricePoint> getBenchmarkSeries(String benchmarkId, LocalDate startDate, LocalDate endDate) {
        String normalized = benchmarkId == null ? "" : benchmarkId.trim().toUpperCase(Locale.ROOT);
        if ("SP500".equals(normalized)) {
            List<IndexChartPriceItemDTO> rows = fetchOverseasDailyRows(new OverseasSymbol("AMS", "SPY"), startDate, endDate);
            return toBacktestPoints(rows, "USD");
        }
        if ("NASDAQ".equals(normalized)) {
            List<IndexChartPriceItemDTO> rows = fetchOverseasDailyRows(new OverseasSymbol("NAS", "QQQ"), startDate, endDate);
            return toBacktestPoints(rows, "USD");
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
        return toBacktestPoints(rows, "KRW");
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
            return new OverseasSymbol("NAS", normalized.substring(3));
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

    private List<BacktestPricePoint> toBacktestPoints(List<IndexChartPriceItemDTO> rows, String currency) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .filter(row -> row != null && row.getClose() != null && row.getClose().compareTo(BigDecimal.ZERO) > 0)
                .map(row -> {
                    LocalDate date = parseDate(row.getDate());
                    BigDecimal closeKrw = row.getClose().multiply(fxRateProvider.getKrwRate(currency, date));
                    return new BacktestPricePoint(date, closeKrw);
                })
                .filter(point -> point.date() != null)
                .sorted(Comparator.comparing(BacktestPricePoint::date))
                .toList();
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
}
