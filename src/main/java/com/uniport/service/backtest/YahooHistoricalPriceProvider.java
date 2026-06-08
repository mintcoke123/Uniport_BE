package com.uniport.service.backtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.entity.AssetMaster;
import com.uniport.repository.AssetMasterRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class YahooHistoricalPriceProvider implements HistoricalPriceProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CURRENCY_USD = "USD";
    private static final String CURRENCY_KRW = "KRW";
    private static final String USER_AGENT = "Mozilla/5.0 UniportETFBacktest/1.0";

    private final RestTemplate restTemplate;
    private final FxRateProvider fxRateProvider;
    private final AssetMasterRepository assetMasterRepository;
    private final boolean syntheticPriceFallbackEnabled;
    private final String chartBaseUrl;

    public YahooHistoricalPriceProvider(RestTemplate restTemplate,
                                        FxRateProvider fxRateProvider,
                                        AssetMasterRepository assetMasterRepository,
                                        @Value("${backtest.price-fallback.enabled:false}") boolean syntheticPriceFallbackEnabled,
                                        @Value("${backtest.yahoo.chart-base-url:https://query1.finance.yahoo.com}") String chartBaseUrl) {
        this.restTemplate = restTemplate;
        this.fxRateProvider = fxRateProvider;
        this.assetMasterRepository = assetMasterRepository;
        this.syntheticPriceFallbackEnabled = syntheticPriceFallbackEnabled;
        this.chartBaseUrl = trimTrailingSlash(chartBaseUrl);
    }

    @Override
    public List<BacktestPricePoint> getSecurityPriceSeries(String securityId, LocalDate startDate, LocalDate endDate) {
        String normalizedSecurityId = normalize(securityId);
        if (normalizedSecurityId.startsWith("CASH_")) {
            return fixedYieldSyntheticSeries(startDate, endDate, BigDecimal.ZERO);
        }
        if (normalizedSecurityId.startsWith("BOND_")) {
            return fixedYieldSyntheticSeries(startDate, endDate, annualYieldForBond(normalizedSecurityId));
        }
        AssetTicker ticker = toSecurityTicker(normalizedSecurityId);
        return externalOrFallback(normalizedSecurityId, ticker, startDate, endDate);
    }

    @Override
    public List<BacktestPricePoint> getSecurityPriceSeriesForEligibility(String securityId,
                                                                         LocalDate startDate,
                                                                         LocalDate endDate) {
        String normalizedSecurityId = normalize(securityId);
        if (normalizedSecurityId.startsWith("CASH_") || normalizedSecurityId.startsWith("BOND_")) {
            return List.of();
        }
        return fetchYahooSeries(toSecurityTicker(normalizedSecurityId), startDate, endDate);
    }

    @Override
    public List<BacktestPricePoint> getBenchmarkSeries(String benchmarkId, LocalDate startDate, LocalDate endDate) {
        String normalized = normalize(benchmarkId);
        AssetTicker ticker = switch (normalized) {
            case "NASDAQ" -> new AssetTicker("QQQ", CURRENCY_USD);
            case "KOSPI" -> new AssetTicker("^KS11", CURRENCY_KRW);
            case "KOSDAQ" -> new AssetTicker("^KQ11", CURRENCY_KRW);
            default -> new AssetTicker("SPY", CURRENCY_USD);
        };
        String fallbackKey = normalized.isBlank() ? "BENCHMARK_SP500" : "BENCHMARK_" + normalized;
        return externalOrFallback(fallbackKey, ticker, startDate, endDate);
    }

    private List<BacktestPricePoint> externalOrFallback(String fallbackKey,
                                                        AssetTicker ticker,
                                                        LocalDate startDate,
                                                        LocalDate endDate) {
        List<BacktestPricePoint> external = fetchYahooSeries(ticker, startDate, endDate);
        if (external.size() >= 2) {
            return external;
        }
        if (syntheticPriceFallbackEnabled) {
            return deterministicFallbackSeries(fallbackKey, startDate, endDate);
        }
        return List.of();
    }

    private AssetTicker toSecurityTicker(String normalizedSecurityId) {
        Optional<AssetMaster> asset = assetMasterRepository.findByAssetIdAndActiveTrue(normalizedSecurityId);
        if (asset.isPresent()) {
            AssetMaster found = asset.get();
            String symbol = found.getSymbol() == null || found.getSymbol().isBlank()
                    ? symbolFromAssetId(normalizedSecurityId)
                    : found.getSymbol().trim().toUpperCase(Locale.ROOT);
            String currency = normalize(found.getCurrency()).isBlank() ? defaultCurrency(normalizedSecurityId) : normalize(found.getCurrency());
            if (normalizedSecurityId.startsWith("KRX_")) {
                return new AssetTicker(toKrxYahooTicker(symbol, found.getMarket()), CURRENCY_KRW);
            }
            return new AssetTicker(symbol, currency);
        }
        if (normalizedSecurityId.startsWith("KRX_")) {
            return new AssetTicker(symbolFromAssetId(normalizedSecurityId) + ".KS", CURRENCY_KRW);
        }
        if (normalizedSecurityId.startsWith("US_")) {
            return new AssetTicker(symbolFromAssetId(normalizedSecurityId), CURRENCY_USD);
        }
        return new AssetTicker(symbolFromAssetId(normalizedSecurityId), defaultCurrency(normalizedSecurityId));
    }

    private String toKrxYahooTicker(String symbol, String market) {
        String suffix = "KOSDAQ".equals(normalize(market)) ? ".KQ" : ".KS";
        return symbol + suffix;
    }

    private String symbolFromAssetId(String assetId) {
        String normalized = normalize(assetId);
        if (normalized.startsWith("KRX_")) {
            return normalized.substring(4);
        }
        if (normalized.startsWith("US_")) {
            return normalized.substring(3);
        }
        return normalized;
    }

    private String defaultCurrency(String normalizedSecurityId) {
        return normalizedSecurityId.startsWith("KRX_") ? CURRENCY_KRW : CURRENCY_USD;
    }

    private List<BacktestPricePoint> fetchYahooSeries(AssetTicker ticker, LocalDate startDate, LocalDate endDate) {
        if (ticker.symbol().isBlank() || startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return List.of();
        }
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    chartUri(ticker.symbol(), startDate, endDate),
                    HttpMethod.GET,
                    yahooRequestEntity(),
                    String.class
            );
            String body = response.getBody();
            return parseChartResponse(body, ticker.currency());
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private HttpEntity<Void> yahooRequestEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        return new HttpEntity<>(headers);
    }

    private URI chartUri(String symbol, LocalDate startDate, LocalDate endDate) {
        long period1 = startDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long period2 = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        String encodedSymbol = URLEncoder.encode(symbol, StandardCharsets.UTF_8).replace("+", "%20");
        return URI.create(chartBaseUrl + "/v8/finance/chart/" + encodedSymbol
                + "?period1=" + period1
                + "&period2=" + period2
                + "&interval=1d&events=history&includeAdjustedClose=true");
    }

    private List<BacktestPricePoint> parseChartResponse(String body, String currency) throws Exception {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        JsonNode result = OBJECT_MAPPER.readTree(body)
                .path("chart")
                .path("result");
        if (!result.isArray() || result.isEmpty()) {
            return List.of();
        }
        JsonNode first = result.get(0);
        JsonNode timestamps = first.path("timestamp");
        JsonNode quotes = first.path("indicators").path("quote");
        JsonNode closes = quotes.isArray() && !quotes.isEmpty() ? quotes.get(0).path("close") : OBJECT_MAPPER.createArrayNode();
        JsonNode adjusted = first.path("indicators").path("adjclose");
        JsonNode adjustedCloses = adjusted.isArray() && !adjusted.isEmpty() ? adjusted.get(0).path("adjclose") : OBJECT_MAPPER.createArrayNode();

        List<BacktestPricePoint> points = new ArrayList<>();
        int size = Math.min(timestamps.size(), Math.max(closes.size(), adjustedCloses.size()));
        for (int i = 0; i < size; i++) {
            int index = i;
            BigDecimal close = priceAt(adjustedCloses, index).or(() -> priceAt(closes, index)).orElse(null);
            if (close == null || close.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            LocalDate date = Instant.ofEpochSecond(timestamps.get(i).asLong())
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate();
            BigDecimal closeKrw = close.multiply(fxRateProvider.getKrwRate(currency, date))
                    .setScale(6, RoundingMode.HALF_UP);
            points.add(new BacktestPricePoint(date, closeKrw));
        }
        return points.stream()
                .sorted(Comparator.comparing(BacktestPricePoint::date))
                .toList();
    }

    private Optional<BigDecimal> priceAt(JsonNode values, int index) {
        if (!values.isArray() || index >= values.size() || values.get(index).isNull()) {
            return Optional.empty();
        }
        return Optional.of(values.get(index).decimalValue());
    }

    private List<BacktestPricePoint> deterministicFallbackSeries(String assetId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return List.of();
        }
        String normalized = assetId == null || assetId.isBlank() ? "LOCAL_SYNTHETIC" : normalize(assetId);
        int hash = Math.floorMod(normalized.hashCode(), 10_000);
        BigDecimal base = BigDecimal.valueOf(1_000L + Math.floorMod(hash, 900));
        BigDecimal annualReturnRate = new BigDecimal("0.045").add(BigDecimal.valueOf(Math.floorMod(hash, 81))
                .divide(BigDecimal.valueOf(1_000), 12, RoundingMode.HALF_UP));
        return fixedYieldSyntheticSeries(startDate, endDate, annualReturnRate, base);
    }

    private List<BacktestPricePoint> fixedYieldSyntheticSeries(LocalDate startDate, LocalDate endDate, BigDecimal annualReturnRate) {
        return fixedYieldSyntheticSeries(startDate, endDate, annualReturnRate, BigDecimal.valueOf(1000));
    }

    private List<BacktestPricePoint> fixedYieldSyntheticSeries(LocalDate startDate,
                                                              LocalDate endDate,
                                                              BigDecimal annualReturnRate,
                                                              BigDecimal base) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return List.of();
        }
        ArrayList<BacktestPricePoint> points = new ArrayList<>();
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

    private String trimTrailingSlash(String value) {
        String normalized = value == null || value.isBlank() ? "https://query1.finance.yahoo.com" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record AssetTicker(String symbol, String currency) {
    }
}
