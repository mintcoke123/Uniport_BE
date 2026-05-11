package com.uniport.service.backtest;

import java.math.BigDecimal;

public record HoldingNewsExposure(
        String securityId,
        String name,
        BigDecimal weightPercent,
        String sentiment,
        double sentimentScore,
        int newsCount,
        String reason,
        String latestHeadline
) {
}
