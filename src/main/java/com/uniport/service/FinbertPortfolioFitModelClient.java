package com.uniport.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Primary
@Component
class FinbertPortfolioFitModelClient implements PortfolioFitModelClient {

    private static final int MAX_ANALYSIS_TEXT_LENGTH = 1_500;

    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final String baseUrl;

    FinbertPortfolioFitModelClient(RestTemplate restTemplate,
                                   @Value("${finbert.portfolio-fit.enabled:${finbert.sentiment.enabled:false}}") boolean enabled,
                                   @Value("${finbert.sentiment.base-url:http://localhost:8011}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.enabled = enabled;
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    @Override
    public Optional<PortfolioFitModelScore> score(PortfolioFitModelInput input) {
        if (!enabled || baseUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> request = Map.of(
                    "newsId", "portfolio-fit-" + safe(input.candidateSymbol()),
                    "text", truncate(textForAnalysis(input))
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    baseUrl + "/analyze",
                    new HttpEntity<>(request, headers),
                    Map.class
            );
            return toScore(response);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<PortfolioFitModelScore> toScore(Map<String, Object> response) {
        if (response == null) {
            return Optional.empty();
        }
        String label = stringValue(response.get("label"));
        if (label.isBlank()) {
            label = stringValue(response.get("sentiment"));
        }
        String normalizedLabel = label.toUpperCase(Locale.ROOT);
        boolean positive;
        if (normalizedLabel.contains("POSITIVE") || normalizedLabel.contains("긍정") || "LABEL_2".equals(normalizedLabel)) {
            positive = true;
        } else if (normalizedLabel.contains("NEGATIVE") || normalizedLabel.contains("부정") || "LABEL_0".equals(normalizedLabel)) {
            positive = false;
        } else {
            return Optional.empty();
        }
        double confidence = doubleValue(response.get("score"), doubleValue(response.get("confidence"), 0.0));
        return Optional.of(new PortfolioFitModelScore(
                positive,
                confidence,
                "FinBERT가 포트폴리오 후보 문맥을 " + (positive ? "긍정" : "부정") + "으로 분류했어요."
        ));
    }

    private String textForAnalysis(PortfolioFitModelInput input) {
        return String.join(" ",
                "포트폴리오", safe(input.portfolioLabel()),
                "보유 테마", String.join(" ", safeList(input.portfolioKeywords())),
                "후보", safe(input.candidateName()), safe(input.candidateSymbol()), safe(input.candidateMarket()),
                "후보 근거", String.join(" ", safeList(input.candidateSignals()))
        ).trim();
    }

    private List<String> safeList(List<String> values) {
        return values != null ? values : List.of();
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
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

    private String safe(String value) {
        return value != null ? value : "";
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
