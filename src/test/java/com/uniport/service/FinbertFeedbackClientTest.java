package com.uniport.service;

import com.uniport.service.backtest.BacktestHolding;
import com.uniport.service.backtest.FinbertFeedbackClient;
import com.uniport.service.backtest.InsightFacts;
import com.uniport.service.backtest.RuleBasedFeedback;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
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

class FinbertFeedbackClientTest {

    @Test
    void generate_usesFinbertClassificationToBuildPortfolioSpecificFeedback() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForObject(eq("http://localhost:8011/analyze"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("label", "NEGATIVE", "score", 0.91));
        FinbertFeedbackClient client = new FinbertFeedbackClient(restTemplate, true, "http://localhost:8011");

        Optional<RuleBasedFeedback> feedback = client.generate(InsightFacts.builder()
                .portfolioLabel("전기차 ETF")
                .periodLabel("1년")
                .totalReturnPercent(BigDecimal.valueOf(4.2))
                .volatilityPercent(BigDecimal.valueOf(22.3))
                .maxDrawdownPercent(BigDecimal.valueOf(-18.4))
                .benchmarkName("S&P 500")
                .topHoldingName("Tesla")
                .topHoldingWeightPercent(BigDecimal.valueOf(45.0))
                .dominantSector("전기차")
                .dominantSectorWeightPercent(BigDecimal.valueOf(70.0))
                .holdings(List.of(new BacktestHolding("US_TSLA", "Tesla", BigDecimal.valueOf(45.0), "전기차")))
                .positiveFacts(List.of())
                .riskFacts(List.of("전기차 비중이 70.0%로 한쪽에 집중되어 있습니다."))
                .disclaimer("과거 데이터 기반 백테스트이며 미래 수익을 보장하지 않습니다.")
                .build());

        assertTrue(feedback.isPresent());
        assertEquals(false, feedback.get().usedFallback());
        assertTrue(feedback.get().summary().contains("FinBERT"));
        assertTrue(feedback.get().summary().contains("Tesla"));
        assertTrue(feedback.get().summary().contains("전기차"));
        verify(restTemplate).postForObject(eq("http://localhost:8011/analyze"), any(HttpEntity.class), eq(Map.class));
    }
}
