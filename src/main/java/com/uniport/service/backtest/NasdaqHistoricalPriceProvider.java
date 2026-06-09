package com.uniport.service.backtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.entity.AssetPriceDaily;
import com.uniport.repository.AssetPriceDailyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class NasdaqHistoricalPriceProvider implements HistoricalPriceProvider {

    private static final Logger log = LoggerFactory.getLogger(NasdaqHistoricalPriceProvider.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CURRENCY_USD = "USD";
    private static final String SOURCE_NASDAQ_HISTORICAL = "NASDAQ_HISTORICAL_API";
    private static final String USER_AGENT = "Mozilla/5.0 UniportETFBacktest/1.0";

    private final RestTemplate restTemplate;
    private final FxRateProvider fxRateProvider;
    private final AssetPriceDailyRepository assetPriceDailyRepository;
    private final String apiBaseUrl;

    public NasdaqHistoricalPriceProvider(RestTemplate restTemplate,
                                         FxRateProvider fxRateProvider,
                                         AssetPriceDailyRepository assetPriceDailyRepository,
                                         @Value("${backtest.nasdaq.api-base-url:https://api.nasdaq.com}") String apiBaseUrl) {
        this.restTemplate = restTemplate;
        this.fxRateProvider = fxRateProvider;
        this.assetPriceDailyRepository = assetPriceDailyRepository;
        this.apiBaseUrl = trimTrailingSlash(apiBaseUrl);
    }

    @Override
    public List<BacktestPricePoint> getSecurityPriceSeries(String securityId, LocalDate startDate, LocalDate endDate) {
        return List.of();
    }

    @Override
    public List<BacktestPricePoint> getSecurityPriceSeriesForEligibility(String securityId,
                                                                         LocalDate startDate,
                                                                         LocalDate endDate) {
        return List.of();
    }

    @Override
    public List<BacktestPricePoint> getBenchmarkSeries(String benchmarkId, LocalDate startDate, LocalDate endDate) {
        Optional<NasdaqBenchmarkTicker> ticker = benchmarkTicker(benchmarkId);
        if (ticker.isEmpty() || startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return List.of();
        }
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    historicalUri(ticker.get(), startDate, endDate),
                    HttpMethod.GET,
                    requestEntity(),
                    String.class
            );
            List<NasdaqPriceCandidate> candidates = parseRows(response.getBody(), ticker.get().cacheAssetId());
            saveFetchedPrices(ticker.get(), candidates);
            return candidates.stream()
                    .map(candidate -> new BacktestPricePoint(candidate.date(), candidate.closeKrw()))
                    .toList();
        } catch (RuntimeException e) {
            log.debug("[nasdaq-benchmark-price] failed to fetch {}: {}", ticker.get().symbol(), safeMessage(e));
            return List.of();
        } catch (Exception e) {
            log.debug("[nasdaq-benchmark-price] failed to parse {}: {}", ticker.get().symbol(), safeMessage(e));
            return List.of();
        }
    }

    private Optional<NasdaqBenchmarkTicker> benchmarkTicker(String benchmarkId) {
        String normalized = benchmarkId == null ? "" : benchmarkId.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SP500" -> Optional.of(new NasdaqBenchmarkTicker("SPY", "etf", "BENCHMARK_SP500"));
            case "NASDAQ" -> Optional.of(new NasdaqBenchmarkTicker("QQQ", "etf", "BENCHMARK_NASDAQ"));
            default -> Optional.empty();
        };
    }

    private URI historicalUri(NasdaqBenchmarkTicker ticker, LocalDate startDate, LocalDate endDate) {
        String symbol = URLEncoder.encode(ticker.symbol(), StandardCharsets.UTF_8);
        String assetClass = URLEncoder.encode(ticker.assetClass(), StandardCharsets.UTF_8);
        return URI.create(apiBaseUrl + "/api/quote/" + symbol + "/historical"
                + "?assetclass=" + assetClass
                + "&fromdate=" + startDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                + "&todate=" + endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                + "&limit=9999");
    }

    private HttpEntity<Void> requestEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.set(HttpHeaders.ACCEPT, "application/json, text/plain, */*");
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9");
        headers.set(HttpHeaders.ORIGIN, "https://www.nasdaq.com");
        headers.set(HttpHeaders.REFERER, "https://www.nasdaq.com/");
        return new HttpEntity<>(headers);
    }

    private List<NasdaqPriceCandidate> parseRows(String body, String cacheAssetId) throws Exception {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        JsonNode rows = OBJECT_MAPPER.readTree(body)
                .path("data")
                .path("tradesTable")
                .path("rows");
        if (!rows.isArray()) {
            return List.of();
        }
        java.util.ArrayList<NasdaqPriceCandidate> candidates = new java.util.ArrayList<>();
        for (JsonNode row : rows) {
            LocalDate date = parseDate(row.path("date").asText(""));
            BigDecimal closeNative = parsePrice(row.path("close").asText(""));
            if (date == null || closeNative == null || closeNative.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal closeKrw = closeNative.multiply(fxRateProvider.getKrwRate(CURRENCY_USD, date))
                    .setScale(6, RoundingMode.HALF_UP);
            candidates.add(new NasdaqPriceCandidate(cacheAssetId, date, closeNative, closeKrw));
        }
        return candidates.stream()
                .sorted(Comparator.comparing(NasdaqPriceCandidate::date))
                .toList();
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim(), DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private BigDecimal parsePrice(String value) {
        String normalized = value == null ? "" : value.replace("$", "").replace(",", "").trim();
        if (normalized.isBlank() || "N/A".equalsIgnoreCase(normalized)) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void saveFetchedPrices(NasdaqBenchmarkTicker ticker, List<NasdaqPriceCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        try {
            List<AssetPriceDaily> rows = candidates.stream()
                    .map(candidate -> {
                        AssetPriceDaily row = assetPriceDailyRepository
                                .findByAssetIdAndTradeDate(candidate.cacheAssetId(), candidate.date())
                                .orElseGet(() -> AssetPriceDaily.builder()
                                        .assetId(candidate.cacheAssetId())
                                        .tradeDate(candidate.date())
                                        .build());
                        row.setCloseNative(candidate.closeNative());
                        row.setCloseKrw(candidate.closeKrw());
                        row.setCurrency(CURRENCY_USD);
                        row.setSource(SOURCE_NASDAQ_HISTORICAL);
                        return row;
                    })
                    .toList();
            assetPriceDailyRepository.saveAll(rows);
        } catch (RuntimeException e) {
            log.warn("[nasdaq-benchmark-price-cache] failed to save fetched prices for {}", ticker.cacheAssetId(), e);
        }
    }

    private String trimTrailingSlash(String value) {
        String normalized = value == null || value.isBlank() ? "https://api.nasdaq.com" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private record NasdaqBenchmarkTicker(String symbol, String assetClass, String cacheAssetId) {}

    private record NasdaqPriceCandidate(String cacheAssetId,
                                        LocalDate date,
                                        BigDecimal closeNative,
                                        BigDecimal closeKrw) {}
}
