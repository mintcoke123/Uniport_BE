package com.uniport.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
class FinbertSentimentClient {

    private static final int MAX_ANALYSIS_TEXT_LENGTH = 1_500;

    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final String baseUrl;

    FinbertSentimentClient(RestTemplate restTemplate,
                           @Value("${finbert.sentiment.enabled:false}") boolean enabled,
                           @Value("${finbert.sentiment.base-url:http://localhost:8011}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.enabled = enabled;
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    Optional<NewsSentimentAnalysis> analyze(NewsSentimentInput input) {
        if (!enabled || baseUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> request = Map.of(
                    "newsId", input.newsId() != null ? input.newsId() : "",
                    "text", truncate(input.textForAnalysis())
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    baseUrl + "/analyze",
                    new HttpEntity<>(request, headers),
                    Map.class
            );
            return toAnalysis(response);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<NewsSentimentAnalysis> toAnalysis(Map<String, Object> response) {
        if (response == null) {
            return Optional.empty();
        }
        NewsSentimentType type = toType(stringValue(response.get("label")));
        if (type == null) {
            type = toType(stringValue(response.get("sentiment")));
        }
        if (type == null) {
            return Optional.empty();
        }
        double score = doubleValue(response.get("score"), doubleValue(response.get("confidence"), 0.0));
        String reason = "FinBERT가 금융 문맥상 "
                + (type == NewsSentimentType.NEGATIVE ? "부정" : "긍정")
                + " 신호로 분류했어요.";
        return Optional.of(new NewsSentimentAnalysis(type, score, reason));
    }

    private NewsSentimentType toType(String rawLabel) {
        if (rawLabel == null || rawLabel.isBlank()) {
            return null;
        }
        String label = rawLabel.trim().toUpperCase(Locale.ROOT);
        if (label.contains("NEGATIVE") || label.contains("부정") || "LABEL_0".equals(label)) {
            return NewsSentimentType.NEGATIVE;
        }
        if (label.contains("POSITIVE") || label.contains("긍정") || "LABEL_2".equals(label)) {
            return NewsSentimentType.POSITIVE;
        }
        return null;
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private double doubleValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value != null ? Double.parseDouble(String.valueOf(value)) : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_ANALYSIS_TEXT_LENGTH) {
            return value != null ? value : "";
        }
        return value.substring(0, MAX_ANALYSIS_TEXT_LENGTH);
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
