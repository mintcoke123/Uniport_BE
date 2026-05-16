package com.uniport.service.feedback;

import java.math.BigDecimal;
import java.util.List;

public record GroupFeedbackFacts(
        BigDecimal returnRate,
        BigDecimal finalEquity,
        List<TradePnlSnapshot> tradeHistory,
        TradePnlSnapshot bestTrade,
        TradePnlSnapshot worstTrade,
        String tone,
        int maxLength
) {
}
