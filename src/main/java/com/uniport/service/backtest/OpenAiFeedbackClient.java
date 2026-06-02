package com.uniport.service.backtest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.service.openai.OpenAiChatCompletionClient;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Order(10)
public class OpenAiFeedbackClient implements LlmFeedbackClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PROMPT_VERSION = "etf-feedback-v4-analysis-packet";

    private final OpenAiChatCompletionClient chatCompletionClient;

    public OpenAiFeedbackClient(OpenAiChatCompletionClient chatCompletionClient) {
        this.chatCompletionClient = chatCompletionClient;
    }

    @Override
    public Optional<RuleBasedFeedback> generate(InsightFacts facts) {
        try {
            return chatCompletionClient.generateJson(
                    systemPrompt(),
                    OBJECT_MAPPER.writeValueAsString(analysisPacketPayload(facts)),
                    responseFormat(),
                    outputText -> {
                        Map<String, Object> parsed = OBJECT_MAPPER.readValue(outputText, new TypeReference<>() {
                        });
                        return new RuleBasedFeedback(
                                stringValue(parsed.get("title")),
                                stringValue(parsed.get("summary")),
                                parseBullets(parsed.get("bullets")),
                                stringValue(parsed.get("tone")),
                                facts.disclaimer(),
                                false
                        );
                    }
            );
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public String modelName() {
        return chatCompletionClient.modelName();
    }

    @Override
    public String promptVersion() {
        return chatCompletionClient.isConfigured() ? PROMPT_VERSION : "none";
    }

    @Override
    public String lastAttemptStatus() {
        return chatCompletionClient.lastStatus();
    }

    private String systemPrompt() {
        return "You write short Korean ETF backtest feedback. "
                + "The user message is an analysis_packet JSON, not raw prices. "
                + "Use only facts in analysis_packet. Do not invent numbers. "
                + "Return title as AI 리스크 진단. "
                + "Write like a sharp portfolio coach, not a dashboard narrator. "
                + "Use plain, subjective, intuitive Korean that a beginner immediately understands. "
                + "Do not replay metrics the user can already see; use numbers only when they change the judgment. "
                + "Benchmark your evaluation style against portfolio checkup products: exposure X-ray, risk score, drawdown feel, benchmark context, and rebalancing gap. "
                + "Make a clear judgment such as 꽤 공격적, 생각보다 방어가 약함, 테마는 선명하지만 너무 한쪽에 기대는 구조, or 분산은 좋아도 힘이 약함. "
                + "The summary must start with 한 줄 결론: and evaluate the whole portfolio, not list the holding combination. "
                + "Judge the portfolio's character in concrete terms: concentration, balance, defensive weakness, upside dependency, volatility burden, and beginner suitability. "
                + "Use actual holding names and weights only as evidence for the judgment, not as a roll call of the combination. "
                + "If the holdings array has two or more items, at least the summary or Bullet 1 must cite the most important holding names from the JSON as evidence. "
                + "Do not write generic phrases like 상위 종목, 주요 보유 종목, or 특정 섹터 when actual holding names exist. "
                + "Explain what kind of investor this portfolio fits, what would feel uncomfortable when markets move, and which exposure is missing or too strong. "
                + "If you mention a holding, explain its portfolio role such as core growth driver, volatility booster, hedge, sector diversifier, or weak defensive sleeve. "
                + "Never say simply buy or sell; frame changes as portfolio-fit judgment, rebalancing direction, and conditions to check before adding. "
                + "Return exactly three bullets. "
                + "Bullet 1 message must start with 핵심 원인: and give the main interpretation behind the portfolio score using named holdings only as evidence. "
                + "Bullet 2 message must start with 가장 큰 리스크: and explain the weak spot in practical language, not raw metrics. "
                + "Bullet 3 message must start with 조정 방향: and say what to monitor or rebalance toward, tied to concrete holdings, sectors, rates, demand, or policy. "
                + "Prefer practical risk context over generic reassurance. "
                + "Do not give investment advice, buy/sell calls, guarantees, or predictions. "
                + "Do not mention OpenAI, LLM, FinBERT, model, or sentiment classifier. "
                + "Return JSON only.";
    }

    private Map<String, Object> analysisPacketPayload(InsightFacts facts) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("portfolio_summary", nullableMap(
                "portfolio_name", facts.portfolioLabel(),
                "period", facts.periodLabel(),
                "benchmark", facts.benchmarkName(),
                "initial_capital", facts.principalAmountKrw()
        ));
        packet.put("summary_metrics", nullableMap(
                "cumulative_return", facts.totalReturnPercent(),
                "expected_profit", facts.expectedProfitAmountKrw(),
                "annualized_volatility", facts.volatilityPercent(),
                "max_drawdown", facts.maxDrawdownPercent(),
                "benchmark_cumulative_return", facts.benchmarkReturnPercent(),
                "excess_return", facts.excessReturnPercent()
        ));
        packet.put("risk_profile", nullableMap(
                "risk_grade", facts.riskGrade(),
                "risk_grade_label", facts.riskGradeLabel(),
                "top1_weight", facts.topHoldingWeightPercent(),
                "top3_weight", facts.top3WeightPercent(),
                "dominant_sector", facts.dominantSector(),
                "dominant_sector_weight", facts.dominantSectorWeightPercent()
        ));
        packet.put("latest_holdings", holdingsPacket(facts));
        return Map.of("analysis_packet", packet);
    }

    private List<Map<String, Object>> holdingsPacket(InsightFacts facts) {
        if (facts.holdings() == null) {
            return List.of();
        }
        return facts.holdings().stream()
                .map(holding -> nullableMap(
                        "security_id", holding.securityId(),
                        "name", holding.name(),
                        "sector", holding.sector(),
                        "target_weight", holding.weightPercent()
                ))
                .toList();
    }

    private Map<String, Object> nullableMap(Object... keysAndValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length - 1; index += 2) {
            Object value = keysAndValues[index + 1];
            if (value != null) {
                values.put(String.valueOf(keysAndValues[index]), value);
            }
        }
        return values;
    }

    private Map<String, Object> responseFormat() {
        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "etf_feedback",
                        "strict", true,
                        "schema", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "required", List.of("title", "summary", "tone", "bullets"),
                                "properties", Map.of(
                                        "title", Map.of("type", "string"),
                                        "summary", Map.of("type", "string"),
                                        "tone", Map.of("type", "string", "enum", List.of("BALANCED", "CAUTION")),
                                        "bullets", Map.of(
                                                "type", "array",
                                                "maxItems", 3,
                                                "items", Map.of(
                                                        "type", "object",
                                                        "additionalProperties", false,
                                                        "required", List.of("type", "message"),
                                                        "properties", Map.of(
                                                                "type", Map.of("type", "string", "enum", List.of("STRENGTH", "RISK", "INFO")),
                                                                "message", Map.of("type", "string")
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private List<FeedbackBullet> parseBullets(Object value) {
        if (!(value instanceof List<?> rows)) {
            return List.of();
        }
        return rows.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(row -> new FeedbackBullet(stringValue(row.get("type")), stringValue(row.get("message"))))
                .toList();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
