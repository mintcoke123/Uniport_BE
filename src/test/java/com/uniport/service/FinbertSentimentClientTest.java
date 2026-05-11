package com.uniport.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinbertSentimentClientTest {

    @Test
    void analyze_mapsFinbertPositiveLabelToNewsSentiment() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForObject(
                eq("http://localhost:8011/analyze"),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(Map.of(
                "label", "positive",
                "score", 0.94
        ));
        FinbertSentimentClient client = new FinbertSentimentClient(restTemplate, true, "http://localhost:8011/");

        Optional<NewsSentimentAnalysis> result = client.analyze(new NewsSentimentInput(
                "news_1",
                "삼성전자 실적 기대",
                "영업이익 개선 기대가 커지고 있어요.",
                "",
                "네이버 뉴스"
        ));

        assertTrue(result.isPresent());
        assertEquals(NewsSentimentType.POSITIVE, result.get().type());
        assertEquals(0.94, result.get().score());
        assertTrue(result.get().reason().contains("긍정"));
    }

    @Test
    void analyze_returnsEmptyWhenClientIsDisabled() {
        FinbertSentimentClient client = new FinbertSentimentClient(mock(RestTemplate.class), false, "http://localhost:8011");

        Optional<NewsSentimentAnalysis> result = client.analyze(new NewsSentimentInput(
                "news_1",
                "삼성전자 실적 기대",
                "영업이익 개선 기대가 커지고 있어요.",
                "",
                "네이버 뉴스"
        ));

        assertTrue(result.isEmpty());
    }
}
