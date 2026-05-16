package com.uniport.service.feedback;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void sendsFullTradeHistoryToLlmForTradingFeedback() {
        AtomicReference<GroupFeedbackFacts> capturedFacts = new AtomicReference<>();
        FeedbackCommentGenerator generator = new FeedbackCommentGenerator(facts -> {
            capturedFacts.set(facts);
            return Optional.of("삼성전자 매수는 보유 유지가 좋았고, SK하이닉스는 손실 확대 전 대응이 필요했어요.");
        });
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

        assertEquals("LLM", comment.source());
        assertEquals(2, capturedFacts.get().tradeHistory().size());
        assertEquals("삼성전자", capturedFacts.get().tradeHistory().get(0).stockName());
        assertEquals("SK하이닉스", capturedFacts.get().tradeHistory().get(1).stockName());
        assertEquals("실적 개선 기대", capturedFacts.get().tradeHistory().get(0).reason());
        assertTrue(capturedFacts.get().tone().contains("매매"));
    }

    @Test
    void acceptsTradingFeedbackThatUsesNumbersFromTradeHistory() {
        FeedbackCommentGenerator generator = new FeedbackCommentGenerator(
                facts -> Optional.of("삼성전자 10주 매수는 수익 전환까지 끌고 간 판단이 좋았어요.")
        );
        GroupInvestmentFeedbackCalculation calculation = new GroupInvestmentFeedbackCalculator().calculate(
                new GroupInvestmentSessionSnapshot(1L, 1L, new BigDecimal("1000000"), Instant.parse("2026-01-28T13:00:00Z")),
                List.of(buy(1L, 11L, "005930", "삼성전자", "70000")),
                Map.of("005930", new BigDecimal("82000"))
        );

        GeneratedFeedbackComment comment = generator.generate(calculation);

        assertEquals("LLM", comment.source());
    }

    @Test
    void acceptsDetailedTradingFeedbackWithReasonAndNextAction() {
        FeedbackCommentGenerator generator = new FeedbackCommentGenerator(
                facts -> Optional.of("삼성전자 매수는 70,000원 진입 후 수익을 지킨 점이 좋았어요. SK하이닉스는 -8.0% 손실 구간에서 재평가가 늦었으니, 다음에는 투표 전에 손절 기준을 먼저 정해보세요.")
        );
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

        assertEquals("LLM", comment.source());
        assertTrue(comment.comment().contains("좋았어요"));
        assertTrue(comment.comment().contains("다음에는"));
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
                tradeId == 1L ? "실적 개선 기대" : "반도체 업황 반등 기대",
                BigDecimal.ZERO,
                Instant.parse("2026-01-23T01:35:00Z").plusSeconds(tradeId)
        );
    }
}
