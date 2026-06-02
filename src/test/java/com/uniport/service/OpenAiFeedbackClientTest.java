package com.uniport.service;

import com.uniport.service.backtest.InsightFacts;
import com.uniport.service.backtest.OpenAiFeedbackClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.mockito.ArgumentCaptor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiFeedbackClientTest {

    @Test
    void applicationConfig_enablesOpenAiFeedbackByDefaultWhenApiKeyExists() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));

        assertEquals("${OPENAI_API_KEY:${AI_PROVIDER_API_KEY:}}",
                yaml.getObject().getProperty("openai.api-key"));
        assertEquals("${OPENAI_BASE_URL:${AI_LLM_ENDPOINT:https://api.openai.com}}",
                yaml.getObject().getProperty("openai.base-url"));
        assertEquals("${OPENAI_FEEDBACK_ENABLED:true}",
                yaml.getObject().getProperty("openai.feedback.enabled"));
    }

    @Test
    void productionConfig_mapsRailwayOpenAiProxyVariables() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-prod.yml"));

        assertEquals("${OPENAI_API_KEY:${AI_PROVIDER_API_KEY:}}",
                yaml.getObject().getProperty("openai.api-key"));
        assertEquals("${OPENAI_BASE_URL:${AI_LLM_ENDPOINT:https://api.openai.com}}",
                yaml.getObject().getProperty("openai.base-url"));
        assertEquals("${OPENAI_FEEDBACK_ENABLED:true}",
                yaml.getObject().getProperty("openai.feedback.enabled"));
    }

    @Test
    void generate_returnsEmptyWhenApiKeyIsMissing() {
        OpenAiFeedbackClient client = new OpenAiFeedbackClient(
                new RestTemplate(),
                "",
                "https://api.openai.com",
                "gpt-4.1-mini",
                false
        );

        assertEquals(Optional.empty(), client.generate(baseFacts()));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void generate_requestsActionablePortfolioFeedbackFormat() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(
                eq("https://api.openai.com/v1/chat/completions"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(chatCompletionResponse());
        OpenAiFeedbackClient client = new OpenAiFeedbackClient(
                restTemplate,
                "test-key",
                "https://api.openai.com",
                "gpt-4.1-mini",
                true
        );
        ArgumentCaptor<HttpEntity> captor = forClass(HttpEntity.class);

        client.generate(baseFacts());

        verify(restTemplate).exchange(
                eq("https://api.openai.com/v1/chat/completions"),
                eq(HttpMethod.POST),
                captor.capture(),
                any(ParameterizedTypeReference.class)
        );
        Map<?, ?> body = (Map<?, ?>) captor.getValue().getBody();
        List<?> messages = (List<?>) body.get("messages");
        Map<?, ?> systemMessage = (Map<?, ?>) messages.get(0);
        String prompt = String.valueOf(systemMessage.get("content"));
        Assertions.assertTrue(prompt.contains("한 줄 결론:"));
        Assertions.assertTrue(prompt.contains("핵심 원인:"));
        Assertions.assertTrue(prompt.contains("가장 큰 리스크:"));
        Assertions.assertTrue(prompt.contains("조정 방향:"));
        Assertions.assertTrue(prompt.contains("actual holding names and weights"));
        Assertions.assertTrue(prompt.contains("portfolio's character"));
        Assertions.assertTrue(prompt.contains("Do not replay metrics the user can already see"));
        Assertions.assertTrue(prompt.contains("Make a clear judgment"));
        Assertions.assertTrue(prompt.contains("plain, subjective, intuitive Korean"));
        Assertions.assertTrue(prompt.contains("portfolio-fit judgment"));
        Assertions.assertTrue(prompt.contains("rebalancing direction"));
        Assertions.assertTrue(prompt.contains("conditions to check before adding"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void generate_sendsAnalysisPacketShapeInsteadOfRawInsightFacts() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(
                eq("https://api.openai.com/v1/chat/completions"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(chatCompletionResponse());
        OpenAiFeedbackClient client = new OpenAiFeedbackClient(
                restTemplate,
                "test-key",
                "https://api.openai.com",
                "gpt-4.1-mini",
                true
        );
        ArgumentCaptor<HttpEntity> captor = forClass(HttpEntity.class);

        client.generate(baseFacts());

        verify(restTemplate).exchange(
                eq("https://api.openai.com/v1/chat/completions"),
                eq(HttpMethod.POST),
                captor.capture(),
                any(ParameterizedTypeReference.class)
        );
        Map<?, ?> body = (Map<?, ?>) captor.getValue().getBody();
        List<?> messages = (List<?>) body.get("messages");
        Map<?, ?> userMessage = (Map<?, ?>) messages.get(1);
        String payload = String.valueOf(userMessage.get("content"));
        Assertions.assertTrue(payload.contains("\"analysis_packet\""));
        Assertions.assertTrue(payload.contains("\"portfolio_summary\""));
        Assertions.assertTrue(payload.contains("\"summary_metrics\""));
        Assertions.assertTrue(payload.contains("\"latest_holdings\""));
        Assertions.assertFalse(payload.contains("\"totalReturnPercent\""));
        Assertions.assertFalse(payload.contains("\"positiveFacts\""));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void generate_usesVersionedOpenAiCompatibleEndpointWithoutDuplicatingV1() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(
                eq("https://openai-oauth-production.up.railway.app/v1/chat/completions"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(chatCompletionResponse());
        OpenAiFeedbackClient client = new OpenAiFeedbackClient(
                restTemplate,
                "proxy-key",
                "https://openai-oauth-production.up.railway.app/v1",
                "gpt-4.1-mini",
                true
        );

        Optional<?> feedback = client.generate(baseFacts());

        Assertions.assertTrue(feedback.isPresent());
        verify(restTemplate).exchange(
                eq("https://openai-oauth-production.up.railway.app/v1/chat/completions"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        );
    }

    private ResponseEntity<Map<String, Object>> chatCompletionResponse() {
        return ResponseEntity.ok(Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of(
                                "content", """
                                        {"title":"AI 리스크 진단","summary":"한 줄 결론: 테스트 ETF입니다.","tone":"BALANCED","bullets":[]}
                                        """
                        )
                ))
        ));
    }

    private InsightFacts baseFacts() {
        return InsightFacts.builder()
                .portfolioLabel("테스트 ETF")
                .periodLabel("1년")
                .principalAmountKrw(BigDecimal.valueOf(100_000_000))
                .totalReturnPercent(BigDecimal.valueOf(8.4))
                .expectedProfitAmountKrw(BigDecimal.valueOf(8_400_000))
                .volatilityPercent(BigDecimal.valueOf(14.2))
                .maxDrawdownPercent(BigDecimal.valueOf(-8.5))
                .benchmarkName("S&P 500")
                .benchmarkReturnPercent(BigDecimal.valueOf(7.1))
                .excessReturnPercent(BigDecimal.valueOf(1.3))
                .riskGrade("LOW")
                .riskGradeLabel("낮음")
                .topHoldingName("Apple")
                .topHoldingWeightPercent(BigDecimal.valueOf(30.0))
                .top3WeightPercent(BigDecimal.valueOf(70.0))
                .positiveFacts(List.of("벤치마크 대비 소폭 우위를 보였습니다."))
                .riskFacts(List.of())
                .disclaimer("과거 데이터 기반 백테스트이며 미래 수익을 보장하지 않습니다.")
                .build();
    }
}
