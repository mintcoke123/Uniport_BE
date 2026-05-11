package com.uniport.service;

import com.uniport.entity.ManagedNewsArticle;
import com.uniport.repository.ManagedNewsArticleRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradeNewsContextServiceTest {

    private final ManagedNewsArticleRepository repository = mock(ManagedNewsArticleRepository.class);
    private final NewsSentimentAnalyzer analyzer = input -> switch (input.newsId()) {
        case "BEFORE_BAD" -> NewsSentimentAnalysis.negative(0.82, "실적 둔화 우려가 컸어요.");
        case "AFTER_GOOD" -> NewsSentimentAnalysis.positive(0.91, "반등 기대가 커졌어요.");
        default -> NewsSentimentAnalysis.positive(0.55, "중립에 가까워요.");
    };
    private final TradeNewsContextService service = new TradeNewsContextService(repository, analyzer);

    @Test
    void summarizeTradeContextSeparatesBeforeAndAfterNewsAroundExecutionTime() {
        Instant executedAt = Instant.parse("2026-05-12T03:00:00Z");
        when(repository.searchByStock("005930", "삼성전자")).thenReturn(List.of(
                article("BEFORE_BAD", "삼성전자 실적 둔화 우려", LocalDateTime.of(2026, 5, 12, 10, 30)),
                article("AFTER_GOOD", "삼성전자 반등 기대 확대", LocalDateTime.of(2026, 5, 12, 13, 30)),
                article("OLD", "범위 밖 뉴스", LocalDateTime.of(2026, 5, 9, 9, 0))
        ));

        TradeNewsContext context = service.summarize("005930", "삼성전자", executedAt);

        assertEquals(1, context.beforeNewsCount());
        assertEquals(1, context.afterNewsCount());
        assertEquals("NEGATIVE", context.beforeSentiment());
        assertEquals("POSITIVE", context.afterSentiment());
        assertTrue(context.feedbackHint().contains("매수 당시 관련 뉴스는 악재가 우세"));
    }

    @Test
    void summarizeTradeContextReturnsEmptyContextWhenExecutionTimeIsMissing() {
        TradeNewsContext context = service.summarize("005930", "삼성전자", null);

        assertEquals(0, context.beforeNewsCount());
        assertEquals(0, context.afterNewsCount());
        assertEquals("UNKNOWN", context.beforeSentiment());
        assertEquals("UNKNOWN", context.afterSentiment());
    }

    private ManagedNewsArticle article(String key, String title, LocalDateTime publishedAt) {
        return ManagedNewsArticle.builder()
                .newsKey(key)
                .title(title)
                .summary(title)
                .content(title)
                .stockCode("005930")
                .stockName("삼성전자")
                .sourceLabel("테스트 뉴스")
                .publishedAt(publishedAt)
                .build();
    }
}
