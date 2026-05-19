package com.uniport.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.dto.MarketIndexDTO;
import com.uniport.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
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

@Component
public class YahooMarketIndexClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String USER_AGENT = "Mozilla/5.0 UniportMarketIndex/1.0";
    private static final String NASDAQ_COMPOSITE_SYMBOL = "^IXIC";

    private final RestTemplate restTemplate;
    private final String chartBaseUrl;

    public YahooMarketIndexClient(RestTemplate restTemplate,
                                  @Value("${backtest.yahoo.chart-base-url:https://query1.finance.yahoo.com}") String chartBaseUrl) {
        this.restTemplate = restTemplate;
        this.chartBaseUrl = trimTrailingSlash(chartBaseUrl);
    }

    public MarketIndexDTO getNasdaqCompositeIndex() {
        LocalDate endDate = LocalDate.now(ZoneOffset.UTC);
        LocalDate startDate = endDate.minusDays(14);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    chartUri(NASDAQ_COMPOSITE_SYMBOL, startDate, endDate),
                    HttpMethod.GET,
                    yahooRequestEntity(),
                    String.class
            );
            return toMarketIndex(parseClosePrices(response.getBody()));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("NASDAQ 지수를 불러오지 못했습니다. " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private MarketIndexDTO toMarketIndex(List<BigDecimal> closePrices) {
        if (closePrices.isEmpty()) {
            throw new ApiException("NASDAQ 지수 가격 데이터가 비어 있습니다.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        BigDecimal latest = closePrices.get(closePrices.size() - 1);
        BigDecimal previous = closePrices.size() >= 2 ? closePrices.get(closePrices.size() - 2) : latest;
        BigDecimal change = latest.subtract(previous);
        BigDecimal changeRate = previous.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : change.multiply(BigDecimal.valueOf(100)).divide(previous, 4, RoundingMode.HALF_UP);

        return MarketIndexDTO.builder()
                .indexCode("NASDAQ")
                .indexName("NASDAQ")
                .value(latest)
                .changeAmount(change)
                .changeRate(changeRate)
                .build();
    }

    private List<BigDecimal> parseClosePrices(String body) throws Exception {
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

        int size = Math.min(timestamps.size(), closes.size());
        List<PricePoint> points = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (closes.get(i).isNull()) {
                continue;
            }
            BigDecimal close = closes.get(i).decimalValue();
            if (close.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            LocalDate date = Instant.ofEpochSecond(timestamps.get(i).asLong())
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate();
            points.add(new PricePoint(date, close));
        }
        return points.stream()
                .sorted(Comparator.comparing(PricePoint::date))
                .map(PricePoint::close)
                .toList();
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
                + "&interval=1d&events=history");
    }

    private String trimTrailingSlash(String value) {
        String normalized = value == null || value.isBlank() ? "https://query1.finance.yahoo.com" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private record PricePoint(LocalDate date, BigDecimal close) {
    }
}
