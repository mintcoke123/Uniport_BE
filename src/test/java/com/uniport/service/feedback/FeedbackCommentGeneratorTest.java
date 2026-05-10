package com.uniport.service.feedback;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackCommentGeneratorTest {

    @Test
    void fallsBackToTemplateWhenAiReturnsEmpty() {
        FeedbackCommentGenerator generator = new FeedbackCommentGenerator(facts -> Optional.empty());
        GroupInvestmentFeedbackCalculation calculation = new GroupInvestmentFeedbackCalculator().calculate(
                new GroupInvestmentSessionSnapshot(1L, 1L, new BigDecimal("1000000"), Instant.parse("2026-01-28T13:00:00Z")),
                List.of(
                        buy(1L, 11L, "005930", "삼성전자", "70000"),
                        buy(2L, 12L, "000660", "SK하이닉스", "100000")
                ),
                Map.of(
                        "005930", new BigDecimal("82000"),
                        "000660", new BigDecimal("92000")
                )
        );

        GeneratedFeedbackComment comment = generator.generate(calculation);

        assertEquals("TEMPLATE", comment.source());
        assertTrue(comment.comment().contains("삼성전자"));
        assertTrue(comment.comment().contains("SK하이닉스"));
    }

    @Test
    void rejectsAiCommentWithProhibitedWordsAndUsesTemplate() {
        FeedbackCommentGenerator generator = new FeedbackCommentGenerator(facts -> Optional.of("삼성전자는 무조건 추천이에요."));
        GroupInvestmentFeedbackCalculation calculation = new GroupInvestmentFeedbackCalculator().calculate(
                new GroupInvestmentSessionSnapshot(1L, 1L, new BigDecimal("1000000"), Instant.parse("2026-01-28T13:00:00Z")),
                List.of(buy(1L, 11L, "005930", "삼성전자", "70000")),
                Map.of("005930", new BigDecimal("82000"))
        );

        GeneratedFeedbackComment comment = generator.generate(calculation);

        assertEquals("TEMPLATE", comment.source());
        assertTrue(comment.comment().contains("삼성전자"));
    }

    private static ExecutedTradeSnapshot buy(Long tradeId, Long decisionId, String stockCode, String stockName, String executedPrice) {
        return new ExecutedTradeSnapshot(
                tradeId,
                decisionId,
                100L,
                stockCode,
                stockName,
                TradeSide.BUY,
                10,
                new BigDecimal(executedPrice),
                BigDecimal.ZERO,
                Instant.parse("2026-01-23T01:35:00Z").plusSeconds(tradeId)
        );
    }
}
