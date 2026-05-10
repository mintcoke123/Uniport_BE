package com.uniport.service.feedback;

import java.math.BigDecimal;

public record GroupFeedbackFacts(
        BigDecimal returnRate,
        BigDecimal finalEquity,
        TradePnlSnapshot bestTrade,
        TradePnlSnapshot worstTrade,
        String tone,
        int maxLength
) {
}
