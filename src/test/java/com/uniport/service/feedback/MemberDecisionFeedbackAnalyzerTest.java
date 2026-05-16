package com.uniport.service.feedback;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemberDecisionFeedbackAnalyzerTest {

    private final MemberDecisionFeedbackAnalyzer analyzer = new MemberDecisionFeedbackAnalyzer();

    @Test
    void splitsDecisionImpactBetweenProposerAndApproveVoters() {
        TradePnlSnapshot tradePnl = tradePnl(1L, 10L, 100L, "삼성전자", TradeSide.BUY, "100000");
        DecisionVoteSnapshot decision = new DecisionVoteSnapshot(10L, 100L, TradeSide.BUY, "삼성전자", Set.of(100L, 200L, 300L), Set.of(100L, 200L, 300L));

        List<MemberDecisionFeedback> result = analyzer.analyze(
                new BigDecimal("1000000"),
                List.of(tradePnl),
                Map.of(10L, decision),
                List.of(
                        new MemberSnapshot(100L, "A", null),
                        new MemberSnapshot(200L, "B", null),
                        new MemberSnapshot(300L, "C", null)
                )
        );

        assertBigDecimal("70000", contributionOf(result, 100L));
        assertBigDecimal("15000", contributionOf(result, 200L));
        assertBigDecimal("15000", contributionOf(result, 300L));
        assertEquals("HIGH", result.stream().filter(item -> item.memberId().equals(100L)).findFirst().orElseThrow().level());
        assertBigDecimal("100.0", result.stream().filter(item -> item.memberId().equals(200L)).findFirst().orElseThrow().participationRate());
    }

    @Test
    void assignsFullImpactToProposerWhenThereAreNoOtherApproveVoters() {
        TradePnlSnapshot tradePnl = tradePnl(1L, 10L, 100L, "SK하이닉스", TradeSide.SELL, "-50000");
        DecisionVoteSnapshot decision = new DecisionVoteSnapshot(10L, 100L, TradeSide.SELL, "SK하이닉스", Set.of(100L), Set.of(100L));

        List<MemberDecisionFeedback> result = analyzer.analyze(
                new BigDecimal("1000000"),
                List.of(tradePnl),
                Map.of(10L, decision),
                List.of(new MemberSnapshot(100L, "A", null))
        );

        assertBigDecimal("-50000", contributionOf(result, 100L));
        assertEquals("LOW", result.get(0).level());
        assertEquals("매도 타이밍 지연", result.get(0).representativeDecision());
    }

    @Test
    void calculatesParticipationRateFromExecutedDecisionParticipation() {
        TradePnlSnapshot first = tradePnl(1L, 10L, 100L, "삼성전자", TradeSide.BUY, "100000");
        TradePnlSnapshot second = tradePnl(2L, 11L, 200L, "현대차", TradeSide.BUY, "20000");
        DecisionVoteSnapshot firstDecision = new DecisionVoteSnapshot(10L, 100L, TradeSide.BUY, "삼성전자", Set.of(100L, 200L), Set.of(100L, 200L));
        DecisionVoteSnapshot secondDecision = new DecisionVoteSnapshot(11L, 200L, TradeSide.BUY, "현대차", Set.of(200L), Set.of(200L));

        List<MemberDecisionFeedback> result = analyzer.analyze(
                new BigDecimal("1000000"),
                List.of(first, second),
                Map.of(10L, firstDecision, 11L, secondDecision),
                List.of(
                        new MemberSnapshot(100L, "A", null),
                        new MemberSnapshot(200L, "B", null)
                )
        );

        MemberDecisionFeedback member100 = result.stream().filter(item -> item.memberId().equals(100L)).findFirst().orElseThrow();
        MemberDecisionFeedback member200 = result.stream().filter(item -> item.memberId().equals(200L)).findFirst().orElseThrow();

        assertEquals(1, member100.participatedDecisionCount());
        assertEquals(2, member100.totalDecisionCount());
        assertBigDecimal("50.0", member100.participationRate());
        assertEquals(2, member200.participatedDecisionCount());
        assertEquals(2, member200.totalDecisionCount());
        assertBigDecimal("100.0", member200.participationRate());
    }

    private static TradePnlSnapshot tradePnl(Long tradeId,
                                             Long decisionId,
                                             Long proposerId,
                                             String stockName,
                                             TradeSide side,
                                             String pnlAmount) {
        return new TradePnlSnapshot(
                tradeId,
                decisionId,
                proposerId,
                "005930",
                stockName,
                side,
                10,
                new BigDecimal("70000"),
                "실적 개선 기대",
                new BigDecimal(pnlAmount),
                new BigDecimal("10.0"),
                Instant.parse("2026-01-23T01:35:00Z")
        );
    }

    private static BigDecimal contributionOf(List<MemberDecisionFeedback> result, Long memberId) {
        return result.stream()
                .filter(item -> item.memberId().equals(memberId))
                .findFirst()
                .orElseThrow()
                .contributionAmount();
    }

    private static void assertBigDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
