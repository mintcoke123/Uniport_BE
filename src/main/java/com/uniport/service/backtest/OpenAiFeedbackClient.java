package com.uniport.service.backtest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OpenAiFeedbackClient implements LlmFeedbackClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PROMPT_VERSION = "etf-feedback-v1";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final boolean enabled;

    public OpenAiFeedbackClient(RestTemplate restTemplate,
                                @Value("${openai.api-key:}") String apiKey,
                                @Value("${openai.base-url:https://api.openai.com}") String baseUrl,
                                @Value("${openai.model:gpt-4.1-mini}") String model,
                                @Value("${openai.feedback.enabled:false}") boolean enabled) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : "https://api.openai.com";
        this.model = model != null && !model.isBlank() ? model.trim() : "gpt-4.1-mini";
        this.enabled = enabled;
    }

    @Override
    public Optional<RuleBasedFeedback> generate(InsightFacts facts) {
        if (!enabled || apiKey.isBlank()) {
            return Optional.empty();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            Map<String, Object> body = Map.of(
                    "model", model,
                    "input", List.of(
                            Map.of("role", "system", "content", systemPrompt()),
                            Map.of("role", "user", "content", OBJECT_MAPPER.writeValueAsString(facts))
                    ),
                    "text", Map.of("format", responseFormat())
            );
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    baseUrl + "/v1/responses",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<>() {
                    }
            );
            String outputText = extractOutputText(response.getBody());
            if (outputText == null || outputText.isBlank()) {
                return Optional.empty();
            }
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(outputText, new TypeReference<>() {
            });
            return Optional.of(new RuleBasedFeedback(
                    stringValue(parsed.get("title")),
                    stringValue(parsed.get("summary")),
                    parseBullets(parsed.get("bullets")),
                    stringValue(parsed.get("tone")),
                    facts.disclaimer(),
                    false
            ));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public String modelName() {
        return enabled && !apiKey.isBlank() ? model : "none";
    }

    @Override
    public String promptVersion() {
        return enabled && !apiKey.isBlank() ? PROMPT_VERSION : "none";
    }

    private String systemPrompt() {
        return "You write short Korean ETF backtest feedback. "
                + "Use only facts in the JSON. Do not invent numbers. "
                + "Do not give investment advice, buy/sell calls, guarantees, or predictions. "
                + "Return JSON only.";
    }

    private Map<String, Object> responseFormat() {
        return Map.of(
                "type", "json_schema",
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
        );
    }

    private String extractOutputText(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object direct = body.get("output_text");
        if (direct instanceof String value) {
            return value;
        }
        Object output = body.get("output");
        if (output instanceof List<?> outputItems) {
            for (Object outputItem : outputItems) {
                if (outputItem instanceof Map<?, ?> outputMap) {
                    Object content = outputMap.get("content");
                    if (content instanceof List<?> contentItems) {
                        for (Object contentItem : contentItems) {
                            if (contentItem instanceof Map<?, ?> contentMap) {
                                Object text = contentMap.get("text");
                                if (text instanceof String value) {
                                    return value;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
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
