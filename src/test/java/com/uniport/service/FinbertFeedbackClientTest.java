package com.uniport.service;

import com.uniport.service.backtest.BacktestHolding;
import com.uniport.service.backtest.FinbertFeedbackClient;
import com.uniport.service.backtest.InsightFacts;
import com.uniport.service.backtest.OpenAiFeedbackClient;
import com.uniport.service.backtest.RuleBasedFeedback;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
        assertTrue(!feedback.get().summary().contains("FinBERT"));
        assertTrue(feedback.get().summary().startsWith("한 줄 결론:"));
        assertTrue(feedback.get().summary().contains("공격형"));
        assertEquals(3, feedback.get().bullets().size());
        assertTrue(feedback.get().bullets().get(0).message().startsWith("핵심 원인:"));
        assertTrue(feedback.get().bullets().get(0).message().contains("Tesla 45.0%"));
        assertTrue(feedback.get().bullets().get(1).message().startsWith("가장 큰 리스크:"));
        assertTrue(feedback.get().bullets().get(1).message().contains("Tesla"));
        assertTrue(feedback.get().bullets().get(2).message().startsWith("조정 방향:"));
        assertTrue(feedback.get().bullets().get(2).message().contains("인도량"));
        verify(restTemplate).postForObject(eq("http://localhost:8011/analyze"), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void openAiFeedbackClientRunsBeforeFinbertClassifierFallback() {
        int openAiOrder = OpenAiFeedbackClient.class.getAnnotation(Order.class).value();
        int finbertOrder = FinbertFeedbackClient.class.getAnnotation(Order.class).value();

        assertTrue(openAiOrder < finbertOrder);
    }

    @Test
    void generate_usesPortfolioCompositionInFinbertRequestId() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForObject(eq("http://localhost:8011/analyze"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("label", "POSITIVE", "score", 0.88));
        FinbertFeedbackClient client = new FinbertFeedbackClient(restTemplate, true, "http://localhost:8011");

        client.generate(baseFacts(List.of(
                new BacktestHolding("US_NVDA", "NVIDIA", BigDecimal.valueOf(70.0), "반도체"),
                new BacktestHolding("US_AAPL", "Apple", BigDecimal.valueOf(30.0), "빅테크")
        )));
        client.generate(baseFacts(List.of(
                new BacktestHolding("US_JPM", "JPMorgan", BigDecimal.valueOf(50.0), "금융"),
                new BacktestHolding("US_PFE", "Pfizer", BigDecimal.valueOf(50.0), "헬스케어")
        )));
        ArgumentCaptor<HttpEntity> captor = forClass(HttpEntity.class);

        verify(restTemplate, times(2)).postForObject(eq("http://localhost:8011/analyze"), captor.capture(), eq(Map.class));
        String firstNewsId = String.valueOf(((Map<?, ?>) captor.getAllValues().get(0).getBody()).get("newsId"));
        String secondNewsId = String.valueOf(((Map<?, ?>) captor.getAllValues().get(1).getBody()).get("newsId"));

        assertNotEquals(firstNewsId, secondNewsId);
    }

    private InsightFacts baseFacts(List<BacktestHolding> holdings) {
        return InsightFacts.builder()
                .portfolioLabel("나만의 주식 ETF")
                .periodLabel("1년")
                .totalReturnPercent(BigDecimal.valueOf(4.2))
                .volatilityPercent(BigDecimal.valueOf(22.3))
                .maxDrawdownPercent(BigDecimal.valueOf(-18.4))
                .benchmarkName("S&P 500")
                .topHoldingName(holdings.get(0).name())
                .topHoldingWeightPercent(holdings.get(0).weightPercent())
                .dominantSector(holdings.get(0).sector())
                .dominantSectorWeightPercent(holdings.get(0).weightPercent())
                .holdings(holdings)
                .positiveFacts(List.of())
                .riskFacts(List.of())
                .disclaimer("과거 데이터 기반 백테스트이며 미래 수익을 보장하지 않습니다.")
                .build();
    }
}
