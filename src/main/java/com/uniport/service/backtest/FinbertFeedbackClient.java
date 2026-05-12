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
@Order(10)
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
                    "newsId", "etf-risk-" + safe(facts.portfolioLabel()).hashCode(),
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
        String summary = buildSummary(facts, negative);
        return Optional.of(new RuleBasedFeedback(
                "AI 리스크 진단",
                summary,
                buildBullets(facts, negative),
                negative ? "CAUTION" : "BALANCED",
                facts.disclaimer(),
                false
        ));
    }

    private String buildSummary(InsightFacts facts, boolean negative) {
        String prefix = negative ? "FinBERT가 리스크 신호를 더 강하게 봤어요." : "FinBERT가 긍정 신호를 더 강하게 봤어요.";
        return prefix + " " + topHoldingPhrase(facts) + sectorPhrase(facts);
    }

    private List<FeedbackBullet> buildBullets(InsightFacts facts, boolean negative) {
        List<BacktestHolding> holdings = facts.holdings() != null ? facts.holdings() : List.of();
        if (!holdings.isEmpty()) {
            return holdings.stream()
                    .sorted((left, right) -> normalize(right.weightPercent()).compareTo(normalize(left.weightPercent())))
                    .limit(3)
                    .map(holding -> new FeedbackBullet(
                            negative && normalize(holding.weightPercent()).compareTo(BigDecimal.valueOf(30)) >= 0 ? "RISK" : "INFO",
                            holding.name() + " " + formatPercent(holding.weightPercent())
                                    + ": " + sectorLabel(holding) + " 문맥을 FinBERT 진단에 반영했어요."
                    ))
                    .toList();
        }
        List<String> factsList = negative ? facts.riskFacts() : facts.positiveFacts();
        if (factsList != null && !factsList.isEmpty()) {
            return List.of(new FeedbackBullet(negative ? "RISK" : "STRENGTH", factsList.get(0)));
        }
        return List.of(new FeedbackBullet("INFO", "보유 종목과 리스크 점수를 함께 확인해주세요."));
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

    private String topHoldingPhrase(InsightFacts facts) {
        if (facts.topHoldingName() == null || facts.topHoldingName().isBlank()) {
            return "보유 종목별 비중을 확인해주세요.";
        }
        return "최대 비중은 " + facts.topHoldingName() + " " + formatPercent(facts.topHoldingWeightPercent()) + "입니다.";
    }

    private String sectorPhrase(InsightFacts facts) {
        if (facts.dominantSector() == null || facts.dominantSector().isBlank()) {
            return "";
        }
        return " " + facts.dominantSector() + " 비중은 " + formatPercent(facts.dominantSectorWeightPercent()) + "입니다.";
    }

    private String sectorLabel(BacktestHolding holding) {
        return holding.sector() != null && !holding.sector().isBlank() ? holding.sector() : "해당 종목";
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
