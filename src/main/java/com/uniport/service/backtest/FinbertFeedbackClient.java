package com.uniport.service.backtest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@Order(20)
public class FinbertFeedbackClient implements LlmFeedbackClient {

    private static final int MAX_ANALYSIS_TEXT_LENGTH = 1_500;
    private static final String PROMPT_VERSION = "finbert-etf-feedback-v1";

    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final String baseUrl;

    public FinbertFeedbackClient(RestTemplate restTemplate,
                                 @Value("${finbert.feedback.enabled:${finbert.sentiment.enabled:false}}") boolean enabled,
                                 @Value("${finbert.sentiment.base-url:http://localhost:8011}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.enabled = enabled;
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    @Override
    public Optional<RuleBasedFeedback> generate(InsightFacts facts) {
        if (!enabled || baseUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> request = Map.of(
                    "newsId", "etf-risk-" + Integer.toHexString(textForAnalysis(facts).hashCode()),
                    "text", truncate(textForAnalysis(facts))
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    baseUrl + "/analyze",
                    new HttpEntity<>(request, headers),
                    Map.class
            );
            return toFeedback(response, facts);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public String modelName() {
        return enabled && !baseUrl.isBlank() ? "finbert" : "none";
    }

    @Override
    public String promptVersion() {
        return enabled && !baseUrl.isBlank() ? PROMPT_VERSION : "none";
    }

    private Optional<RuleBasedFeedback> toFeedback(Map<String, Object> response, InsightFacts facts) {
        if (response == null) {
            return Optional.empty();
        }
        String label = stringValue(response.get("label"));
        if (label.isBlank()) {
            label = stringValue(response.get("sentiment"));
        }
        String normalizedLabel = label.toUpperCase(Locale.ROOT);
        boolean negative;
        if (normalizedLabel.contains("NEGATIVE") || normalizedLabel.contains("부정") || "LABEL_0".equals(normalizedLabel)) {
            negative = true;
        } else if (normalizedLabel.contains("POSITIVE") || normalizedLabel.contains("긍정") || "LABEL_2".equals(normalizedLabel)) {
            negative = false;
        } else {
            return Optional.empty();
        }
        return Optional.of(EtfFeedbackMessageComposer.compose(facts, negative, false));
    }

    private String textForAnalysis(InsightFacts facts) {
        return String.join(" ",
                "포트폴리오", safe(facts.portfolioLabel()),
                "수익률", formatPercent(facts.totalReturnPercent()),
                "변동성", formatPercent(facts.volatilityPercent()),
                "최대낙폭", formatPercent(facts.maxDrawdownPercent()),
                "최대비중", safe(facts.topHoldingName()), formatPercent(facts.topHoldingWeightPercent()),
                "섹터", safe(facts.dominantSector()), formatPercent(facts.dominantSectorWeightPercent()),
                "긍정사실", String.join(" ", safeList(facts.positiveFacts())),
                "위험사실", String.join(" ", safeList(facts.riskFacts())),
                "보유종목", holdingsText(facts.holdings())
        ).trim();
    }

    private String holdingsText(List<BacktestHolding> holdings) {
        if (holdings == null || holdings.isEmpty()) {
            return "";
        }
        return holdings.stream()
                .map(holding -> safe(holding.name()) + " " + safe(holding.sector()) + " " + formatPercent(holding.weightPercent()))
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private List<String> safeList(List<String> values) {
        return values != null ? values : List.of();
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }

    private BigDecimal normalize(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String formatPercent(BigDecimal value) {
        return normalize(value).setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
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
