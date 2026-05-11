package com.uniport.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class YahooAssetSearchClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String USER_AGENT = "Mozilla/5.0 UniportETFSearch/1.0";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public YahooAssetSearchClient(RestTemplate restTemplate,
                                  @Value("${backtest.yahoo.chart-base-url:https://query1.finance.yahoo.com}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    public List<YahooAssetResult> searchUsEquities(String keyword, int limit) {
        String query = keyword == null ? "" : keyword.trim();
        if (query.isBlank() || limit <= 0) {
            return List.of();
        }
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    searchUri(query, limit),
                    HttpMethod.GET,
                    yahooRequestEntity(),
                    String.class
            );
            return parseResults(response.getBody(), limit);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private URI searchUri(String query, int limit) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
        return URI.create(baseUrl + "/v1/finance/search?q=" + encoded
                + "&quotesCount=" + limit
                + "&newsCount=0");
    }

    private HttpEntity<Void> yahooRequestEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        return new HttpEntity<>(headers);
    }

    private List<YahooAssetResult> parseResults(String body, int limit) throws Exception {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        JsonNode quotes = OBJECT_MAPPER.readTree(body).path("quotes");
        if (!quotes.isArray() || quotes.isEmpty()) {
            return List.of();
        }
        List<YahooAssetResult> results = new ArrayList<>();
        for (JsonNode quote : quotes) {
            if (results.size() >= limit) {
                break;
            }
            if (!"EQUITY".equalsIgnoreCase(text(quote, "quoteType"))) {
                continue;
            }
            String symbol = normalizeSymbol(text(quote, "symbol"));
            String market = toUsMarket(text(quote, "exchange"), text(quote, "exchDisp"));
            if (symbol.isBlank() || market.isBlank()) {
                continue;
            }
            String name = firstNonBlank(text(quote, "longname"), text(quote, "shortname"), symbol);
            results.add(new YahooAssetResult(symbol, name, market, "USD"));
        }
        return results;
    }

    private String toUsMarket(String exchange, String exchangeDisplay) {
        String normalizedExchange = normalize(exchange);
        String normalizedDisplay = normalize(exchangeDisplay);
        if (List.of("NMS", "NGM", "NCM", "NAS").contains(normalizedExchange)
                || normalizedDisplay.contains("NASDAQ")) {
            return "NASDAQ";
        }
        if ("NYQ".equals(normalizedExchange) || normalizedDisplay.contains("NYSE")) {
            return "NYSE";
        }
        if ("ASE".equals(normalizedExchange) || normalizedDisplay.contains("AMEX")) {
            return "AMEX";
        }
        return "";
    }

    private String normalizeSymbol(String value) {
        String symbol = normalize(value);
        if (!symbol.matches("[A-Z][A-Z0-9.\\-]{0,9}")) {
            return "";
        }
        return symbol;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : "";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimTrailingSlash(String value) {
        String normalized = value == null || value.isBlank() ? "https://query1.finance.yahoo.com" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public record YahooAssetResult(String symbol, String name, String market, String currency) {
    }
}
