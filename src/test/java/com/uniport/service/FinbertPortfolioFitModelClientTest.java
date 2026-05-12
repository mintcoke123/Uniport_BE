package com.uniport.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinbertPortfolioFitModelClientTest {

    @Test
    void score_mapsFinbertPositiveLabelToPortfolioFitScore() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForObject(eq("http://localhost:8011/analyze"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("label", "POSITIVE", "score", 0.88));
        FinbertPortfolioFitModelClient client = new FinbertPortfolioFitModelClient(restTemplate, true, "http://localhost:8011");

        Optional<PortfolioFitModelScore> score = client.score(new PortfolioFitModelInput(
                "플랫폼 ETF",
                List.of("플랫폼"),
                "Google",
                "GOOGL",
                "NASDAQ",
                List.of("Google은 플랫폼 포트폴리오 보완 후보")
        ));

        assertTrue(score.isPresent());
        assertEquals(true, score.get().positive());
        assertEquals(0.88, score.get().confidence());
        verify(restTemplate).postForObject(eq("http://localhost:8011/analyze"), any(HttpEntity.class), eq(Map.class));
    }
}
