package com.uniport.service.openai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OpenAiChatCompletionClient {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final String DEFAULT_MODEL = "gpt-4.1";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatCompletionTransport transport;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final boolean enabled;
    private final ThreadLocal<String> lastStatus = ThreadLocal.withInitial(() -> "not_started");

    @Autowired
    public OpenAiChatCompletionClient(RestTemplate restTemplate,
                                      @Value("${openai.api-key:}") String apiKey,
                                      @Value("${openai.base-url:}") String baseUrl,
                                      @Value("${openai.model:}") String model,
                                      @Value("${openai.feedback.enabled:true}") boolean enabled) {
        this(new JavaNetHttpChatCompletionTransport(), apiKey, baseUrl, model, enabled);
    }

    public OpenAiChatCompletionClient(ChatCompletionTransport transport,
                                      String apiKey,
                                      String baseUrl,
                                      String model,
                                      boolean enabled) {
        this.transport = transport;
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
            lastStatus.set("not_configured");
            return Optional.empty();
        }
        List<String> failures = new ArrayList<>();
        Optional<T> strictResult = generateWithResponseFormat(
                "json_schema",
                systemPrompt,
                userContent,
                strictResponseFormat,
                parser,
                failures
        );
        if (strictResult.isPresent()) {
            lastStatus.set("success:json_schema");
            return strictResult;
        }
        Optional<T> jsonObjectResult = generateWithResponseFormat(
                "json_object",
                systemPrompt,
                userContent,
                jsonObjectResponseFormat(),
                parser,
                failures
        );
        if (jsonObjectResult.isPresent()) {
            lastStatus.set("success:json_object");
            return jsonObjectResult;
        }
        Optional<T> plainResult = generateWithResponseFormat(
                "plain_json",
                systemPrompt,
                userContent,
                null,
                parser,
                failures
        );
        if (plainResult.isPresent()) {
            lastStatus.set("success:plain_json");
            return plainResult;
        }
        lastStatus.set("failed:" + String.join(" | ", failures));
        return Optional.empty();
    }

    public boolean isConfigured() {
        return enabled && !apiKey.isBlank();
    }

    public String modelName() {
        return isConfigured() ? model : "none";
    }

    public String lastStatus() {
        return lastStatus.get();
    }

    private <T> Optional<T> generateWithResponseFormat(
            String attemptName,
            String systemPrompt,
            String userContent,
            Map<String, Object> responseFormat,
            OutputParser<T> parser,
            List<String> failures
    ) {
        try {
            String outputText = complete(systemPrompt, userContent, responseFormat);
            if (outputText == null || outputText.isBlank()) {
                failures.add(attemptName + ":empty_response");
                return Optional.empty();
            }
            T parsed = parser.parse(outputText);
            if (parsed == null) {
                failures.add(attemptName + ":parser_returned_null");
                return Optional.empty();
            }
            return Optional.of(parsed);
        } catch (Exception exception) {
            failures.add(attemptName + ":" + failureMessage(exception));
            return Optional.empty();
        }
    }

    private String complete(String systemPrompt, String userContent, Map<String, Object> responseFormat) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)
        ));
        if (responseFormat != null) {
            body.put("response_format", responseFormat);
        }
        Map<String, Object> response = transport.postChatCompletion(apiUrl("chat/completions"), body, apiKey);
        return extractOutputText(response);
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

    private String failureMessage(Exception exception) {
        String message;
        if (exception instanceof HttpStatusCodeException httpException) {
            message = httpException.getStatusCode() + " " + httpException.getResponseBodyAsString();
        } else {
            message = exception.getMessage();
        }
        String normalizedMessage = message != null ? message.replaceAll("\\s+", " ").trim() : "";
        if (normalizedMessage.length() > 260) {
            normalizedMessage = normalizedMessage.substring(0, 260);
        }
        return exception.getClass().getSimpleName() + (normalizedMessage.isBlank() ? "" : ":" + normalizedMessage);
    }

    @FunctionalInterface
    public interface OutputParser<T> {
        T parse(String outputText) throws Exception;
    }

    @FunctionalInterface
    public interface ChatCompletionTransport {
        Map<String, Object> postChatCompletion(String url, Map<String, Object> body, String apiKey) throws Exception;
    }

    private static class JavaNetHttpChatCompletionTransport implements ChatCompletionTransport {

        private final HttpClient httpClient = HttpClient.newHttpClient();

        @Override
        public Map<String, Object> postChatCompletion(String url, Map<String, Object> body, String apiKey) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OpenAiHttpException(response.statusCode(), response.body());
            }
            return OBJECT_MAPPER.readValue(response.body(), new TypeReference<>() {
            });
        }
    }

    private static class OpenAiHttpException extends RuntimeException {

        private final int statusCode;
        private final String responseBody;

        OpenAiHttpException(int statusCode, String responseBody) {
            super(statusCode + " " + responseBody);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }
    }
}
