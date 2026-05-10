package com.uniport.service.feedback;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public record GroupInvestmentFeedbackCalculation(
        Long sessionId,
        Long roomId,
        BigDecimal initialCapital,
        BigDecimal endingCash,
        BigDecimal holdingValue,
        BigDecimal finalEquity,
        BigDecimal profitAmount,
        BigDecimal returnRate,
        List<TradePnlSnapshot> tradePnls,
        Optional<TradePnlSnapshot> bestTrade,
        Optional<TradePnlSnapshot> worstTrade
) {
}
