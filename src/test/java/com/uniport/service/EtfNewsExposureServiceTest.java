package com.uniport.service;

import com.uniport.entity.ManagedNewsArticle;
import com.uniport.repository.ManagedNewsArticleRepository;
import com.uniport.service.backtest.BacktestHolding;
import com.uniport.service.backtest.EtfNewsExposure;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EtfNewsExposureServiceTest {

    private final ManagedNewsArticleRepository repository = mock(ManagedNewsArticleRepository.class);
    private final NewsSentimentAnalyzer analyzer = input -> switch (input.newsId()) {
        case "SAMSUNG_GOOD" -> NewsSentimentAnalysis.positive(0.90, "실적 기대가 커지고 있어요.");
        case "TESLA_BAD" -> NewsSentimentAnalysis.negative(0.80, "수요 둔화 우려가 커지고 있어요.");
        default -> NewsSentimentAnalysis.positive(0.50, "중립에 가까운 흐름이에요.");
    };
    private final EtfNewsExposureService service = new EtfNewsExposureService(repository, analyzer);

    @Test
    void summarizeExposure_weightsLatestStockNewsByEtfHoldingWeight() {
        when(repository.searchByStock("005930", "삼성전자")).thenReturn(List.of(article(
                "SAMSUNG_GOOD",
                "삼성전자 실적 기대 확대",
                "반도체 업황 회복 기대가 이어지고 있어요.",
                "005930",
                "삼성전자"
        )));
        when(repository.searchByStock("TSLA", "Tesla")).thenReturn(List.of(article(
                "TESLA_BAD",
                "Tesla 수요 둔화 우려",
                "전기차 가격 경쟁이 마진 부담으로 이어지고 있어요.",
                "TSLA",
                "Tesla"
        )));

        EtfNewsExposure exposure = service.summarize(List.of(
                new BacktestHolding("KRX_005930", "삼성전자", BigDecimal.valueOf(40), "반도체"),
                new BacktestHolding("US_TSLA", "Tesla", BigDecimal.valueOf(60), "전기차")
        ));

        assertEquals(BigDecimal.valueOf(40.0).setScale(1), exposure.positiveExposurePercent());
        assertEquals(BigDecimal.valueOf(60.0).setScale(1), exposure.negativeExposurePercent());
        assertEquals(2, exposure.matchedNewsCount());
        assertEquals("US_TSLA", exposure.keyContributors().get(0).securityId());
        assertEquals("NEGATIVE", exposure.keyContributors().get(0).sentiment());
        assertTrue(exposure.riskPoints().get(0).contains("Tesla"));
        assertTrue(exposure.riskPoints().get(0).contains("60.0%"));
        assertEquals("REDUCE_WATCH", exposure.rebalanceCandidates().get(0).action());
        assertEquals("HOLD_WATCH", exposure.rebalanceCandidates().get(1).action());
    }

    @Test
    void summarizeExposure_marksLowWeightPositiveHoldingAsIncreaseWatchCandidate() {
        when(repository.searchByStock("NVDA", "NVIDIA")).thenReturn(List.of(article(
                "SAMSUNG_GOOD",
                "NVIDIA AI 수요 확대",
                "AI 서버 수요가 이어지고 있어요.",
                "NVDA",
                "NVIDIA"
        )));

        EtfNewsExposure exposure = service.summarize(List.of(
                new BacktestHolding("US_NVDA", "NVIDIA", BigDecimal.valueOf(12), "반도체")
        ));

        assertEquals(1, exposure.rebalanceCandidates().size());
        assertEquals("INCREASE_WATCH", exposure.rebalanceCandidates().get(0).action());
        assertTrue(exposure.rebalanceCandidates().get(0).reason().contains("비중 확대 점검 후보"));
    }

    @Test
    void summarizeExposure_returnsEmptyExposureWhenNoNewsMatches() {
        when(repository.searchByStock("NVDA", "NVIDIA Corp.")).thenReturn(List.of());

        EtfNewsExposure exposure = service.summarize(List.of(
                new BacktestHolding("US_NVDA", "NVIDIA Corp.", BigDecimal.valueOf(100), "반도체")
        ));

        assertEquals(BigDecimal.ZERO.setScale(1), exposure.positiveExposurePercent());
        assertEquals(BigDecimal.ZERO.setScale(1), exposure.negativeExposurePercent());
        assertEquals(0, exposure.matchedNewsCount());
        assertTrue(exposure.keyContributors().isEmpty());
        assertTrue(exposure.riskPoints().isEmpty());
    }

    @Test
    void summarizeExposure_reusesCachedSentimentForSameNewsArticle() {
        ManagedNewsArticle article = article(
                "SAMSUNG_GOOD",
                "삼성전자 실적 기대 확대",
                "반도체 업황 회복 기대가 이어지고 있어요.",
                "005930",
                "삼성전자"
        );
        ManagedNewsArticleRepository cachedRepository = mock(ManagedNewsArticleRepository.class);
        when(cachedRepository.searchByStock("005930", "삼성전자")).thenReturn(List.of(article));
        AtomicInteger analyzeCount = new AtomicInteger();
        NewsSentimentAnalyzer countingAnalyzer = input -> {
            analyzeCount.incrementAndGet();
            return NewsSentimentAnalysis.positive(0.90, "실적 기대가 커지고 있어요.");
        };
        EtfNewsExposureService cachedService = new EtfNewsExposureService(cachedRepository, countingAnalyzer);

        List<BacktestHolding> holdings = List.of(
                new BacktestHolding("KRX_005930", "삼성전자", BigDecimal.valueOf(40), "반도체")
        );
        cachedService.summarize(holdings);
        cachedService.summarize(holdings);

        assertEquals(1, analyzeCount.get());
    }

    private ManagedNewsArticle article(String key,
                                       String title,
                                       String summary,
                                       String stockCode,
                                       String stockName) {
        return ManagedNewsArticle.builder()
                .newsKey(key)
                .title(title)
                .summary(summary)
                .content(summary)
                .stockCode(stockCode)
                .stockName(stockName)
                .sourceLabel("테스트 뉴스")
                .publishedAt(LocalDateTime.of(2026, 5, 12, 9, 0))
                .build();
    }
}
