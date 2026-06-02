package com.uniport.service.feedback;

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
public class OpenAiGroupFeedbackClient implements GroupFeedbackLlmClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final String DEFAULT_MODEL = "gpt-4.1-mini";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final boolean enabled;

    public OpenAiGroupFeedbackClient(RestTemplate restTemplate,
                                     @Value("${openai.api-key:}") String apiKey,
                                     @Value("${openai.base-url:}") String baseUrl,
                                     @Value("${openai.model:}") String model,
                                     @Value("${openai.feedback.enabled:true}") boolean enabled) {
        this.restTemplate = restTemplate;
        this.apiKey = firstNonBlank(apiKey, System.getenv("OPENAI_API_KEY"), System.getenv("AI_PROVIDER_API_KEY"));
        this.baseUrl = firstNonBlank(baseUrl, System.getenv("OPENAI_BASE_URL"), System.getenv("AI_LLM_ENDPOINT"), DEFAULT_BASE_URL);
        this.model = firstNonBlank(model, System.getenv("OPENAI_MODEL"), DEFAULT_MODEL);
        this.enabled = enabled;
    }

    @Override
    public Optional<String> generate(GroupFeedbackFacts facts) {
        if (!enabled || apiKey.isBlank()) {
            return Optional.empty();
        }
        Optional<String> strictResult = generateWithResponseFormat(facts, responseFormat());
        if (strictResult.isPresent()) {
            return strictResult;
        }
        Optional<String> jsonObjectResult = generateWithResponseFormat(facts, jsonObjectResponseFormat());
        if (jsonObjectResult.isPresent()) {
            return jsonObjectResult;
        }
        return generateWithResponseFormat(facts, null);
    }

    private Optional<String> generateWithResponseFormat(GroupFeedbackFacts facts, Map<String, Object> responseFormat) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt()),
                    Map.of("role", "user", "content", OBJECT_MAPPER.writeValueAsString(facts))
            ));
            if (responseFormat != null) {
                body.put("response_format", responseFormat);
            }
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    apiUrl("chat/completions"),
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
            Object comment = parsed.get("comment");
            return comment != null ? Optional.of(String.valueOf(comment)) : Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String systemPrompt() {
        return """
                너는 Z세대 투자 입문자를 위한 투자 학습 서비스 UniPort의 매매 코치다.
                아래 JSON fact만 사용해 그룹 모의투자의 거래내역, 종목, 매매 이유에 대한 피드백을 작성한다.
                규칙:
                - 2문장 이내로 작성한다.
                - 220자 이내로 작성한다.
                - 거래내역, 종목명, 매수/매도 방향, 매매 이유, 수익/손실 중 최소 3가지를 반영한다.
                - 매매 이유가 결과적으로 타당했는지 또는 어떤 점이 부족했는지 판단한다.
                - 좋았던 매매 판단과 아쉬운 매매 판단을 최대한 구체적으로 짚는다.
                - 마지막에는 다음 매매 때 바로 적용할 기준을 1개 제안한다.
                - 단순 수익률 요약이 아니라 다음 매매 판단에 도움이 되는 피드백으로 쓴다.
                - 종목명은 fact에 있는 이름 그대로 사용한다.
                - fact에 없는 숫자, 사건, 뉴스, 전망을 만들지 않는다.
                - 향후 매수/매도 추천처럼 보이는 표현을 쓰지 않는다.
                - 특정 팀원을 비난하지 않는다.
                - 금지어: 무조건, 반드시, 추천, 확실히 오른다, 실패했다, 잘못했다, 책임
                출력은 JSON만 반환한다.
                """;
    }

    private Map<String, Object> responseFormat() {
        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "group_investment_feedback",
                        "strict", true,
                        "schema", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "required", List.of("comment"),
                                "properties", Map.of("comment", Map.of("type", "string"))
                        )
                )
        );
    }

    private Map<String, Object> jsonObjectResponseFormat() {
        return Map.of("type", "json_object");
    }

    private String extractOutputText(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object choices = body.get("choices");
        if (choices instanceof List<?> choiceItems) {
            for (Object choiceItem : choiceItems) {
                if (choiceItem instanceof Map<?, ?> choiceMap) {
                    Object message = choiceMap.get("message");
                    if (message instanceof Map<?, ?> messageMap) {
                        Object content = messageMap.get("content");
                        if (content instanceof String value) {
                            return value;
                        }
                    }
                }
            }
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

    private String apiUrl(String path) {
        String normalizedBaseUrl = trimTrailingSlash(baseUrl);
        if (normalizedBaseUrl.endsWith("/v1")) {
            return normalizedBaseUrl + "/" + path;
        }
        return normalizedBaseUrl + "/v1/" + path;
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value != null ? value.trim() : "";
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isBlank() ? DEFAULT_BASE_URL : trimmed;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
