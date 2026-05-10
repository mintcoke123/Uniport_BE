package com.uniport.service.feedback;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupInvestmentFeedbackCalculatorTest {

    private final GroupInvestmentFeedbackCalculator calculator = new GroupInvestmentFeedbackCalculator();

    @Test
    void calculatesFinalEquityProfitAndReturnRate() {
        GroupInvestmentSessionSnapshot session = new GroupInvestmentSessionSnapshot(
                1L,
                1L,
                new BigDecimal("1000000"),
                Instant.parse("2026-01-28T13:00:00Z")
        );
        List<ExecutedTradeSnapshot> trades = List.of(new ExecutedTradeSnapshot(
                1L,
                10L,
                100L,
                "005930",
                "삼성전자",
                TradeSide.BUY,
                10,
                new BigDecimal("70000"),
                BigDecimal.ZERO,
                Instant.parse("2026-01-23T01:35:00Z")
        ));

        GroupInvestmentFeedbackCalculation result = calculator.calculate(
                session,
                trades,
                Map.of("005930", new BigDecimal("80000"))
        );

        assertBigDecimal("300000", result.endingCash());
        assertBigDecimal("800000", result.holdingValue());
        assertBigDecimal("1100000", result.finalEquity());
        assertBigDecimal("100000", result.profitAmount());
        assertBigDecimal("10.0", result.returnRate());
    }

    @Test
    void selectsBestAndWorstTradesByPnlAmount() {
        GroupInvestmentSessionSnapshot session = new GroupInvestmentSessionSnapshot(
                1L,
                1L,
                new BigDecimal("1000000"),
                Instant.parse("2026-01-28T13:00:00Z")
        );
        List<ExecutedTradeSnapshot> trades = List.of(
                buy(1L, 11L, "005930", "삼성전자", 10, "70000"),
                buy(2L, 12L, "000660", "SK하이닉스", 10, "100000"),
                buy(3L, 13L, "005380", "현대차", 10, "50000")
        );

        GroupInvestmentFeedbackCalculation result = calculator.calculate(
                session,
                trades,
                Map.of(
                        "005930", new BigDecimal("82000"),
                        "000660", new BigDecimal("92000"),
                        "005380", new BigDecimal("53000")
                )
        );

        assertEquals("삼성전자", result.bestTrade().orElseThrow().stockName());
        assertBigDecimal("120000", result.bestTrade().orElseThrow().pnlAmount());
        assertEquals("SK하이닉스", result.worstTrade().orElseThrow().stockName());
        assertBigDecimal("-80000", result.worstTrade().orElseThrow().pnlAmount());
    }

    @Test
    void publishesNeutralCalculationWhenThereAreNoTrades() {
        GroupInvestmentSessionSnapshot session = new GroupInvestmentSessionSnapshot(
                1L,
                1L,
                new BigDecimal("1000000"),
                Instant.parse("2026-01-28T13:00:00Z")
        );

        GroupInvestmentFeedbackCalculation result = calculator.calculate(session, List.of(), Map.of());

        assertBigDecimal("1000000", result.finalEquity());
        assertBigDecimal("0", result.profitAmount());
        assertBigDecimal("0.0", result.returnRate());
        assertEquals(true, result.bestTrade().isEmpty());
        assertEquals(true, result.worstTrade().isEmpty());
    }

    private static ExecutedTradeSnapshot buy(Long tradeId,
                                             Long decisionId,
                                             String stockCode,
                                             String stockName,
                                             int quantity,
                                             String executedPrice) {
        return new ExecutedTradeSnapshot(
                tradeId,
                decisionId,
                100L,
                stockCode,
                stockName,
                TradeSide.BUY,
                quantity,
                new BigDecimal(executedPrice),
                BigDecimal.ZERO,
                Instant.parse("2026-01-23T01:35:00Z").plusSeconds(tradeId)
        );
    }

    private static void assertBigDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
