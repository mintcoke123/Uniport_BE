package com.uniport.service.openai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OpenAiChatCompletionClient {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final String DEFAULT_MODEL = "gpt-4.1";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final boolean enabled;

    public OpenAiChatCompletionClient(RestTemplate restTemplate,
                                      @Value("${openai.api-key:}") String apiKey,
                                      @Value("${openai.base-url:}") String baseUrl,
                                      @Value("${openai.model:}") String model,
                                      @Value("${openai.feedback.enabled:true}") boolean enabled) {
        this.restTemplate = restTemplate;
        this.apiKey = firstNonBlank(apiKey, System.getenv("OPENAI_API_KEY"), System.getenv("AI_PROVIDER_API_KEY"));
        this.baseUrl = firstNonBlank(
                baseUrl,
                System.getenv("OPENAI_BASE_URL"),
                System.getenv("AI_LLM_ENDPOINT"),
                DEFAULT_BASE_URL
        );
        this.model = firstNonBlank(model, System.getenv("OPENAI_MODEL"), DEFAULT_MODEL);
        this.enabled = enabled;
    }

    public <T> Optional<T> generateJson(
            String systemPrompt,
            String userContent,
            Map<String, Object> strictResponseFormat,
            OutputParser<T> parser
    ) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        Optional<T> strictResult = generateWithResponseFormat(systemPrompt, userContent, strictResponseFormat, parser);
        if (strictResult.isPresent()) {
            return strictResult;
        }
        Optional<T> jsonObjectResult = generateWithResponseFormat(systemPrompt, userContent, jsonObjectResponseFormat(), parser);
        if (jsonObjectResult.isPresent()) {
            return jsonObjectResult;
        }
        return generateWithResponseFormat(systemPrompt, userContent, null, parser);
    }

    public boolean isConfigured() {
        return enabled && !apiKey.isBlank();
    }

    public String modelName() {
        return isConfigured() ? model : "none";
    }

    private <T> Optional<T> generateWithResponseFormat(
            String systemPrompt,
            String userContent,
            Map<String, Object> responseFormat,
            OutputParser<T> parser
    ) {
        try {
            String outputText = complete(systemPrompt, userContent, responseFormat);
            if (outputText == null || outputText.isBlank()) {
                return Optional.empty();
            }
            return Optional.ofNullable(parser.parse(outputText));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String complete(String systemPrompt, String userContent, Map<String, Object> responseFormat) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)
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
        return extractOutputText(response.getBody());
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

    @FunctionalInterface
    public interface OutputParser<T> {
        T parse(String outputText) throws Exception;
    }
}
