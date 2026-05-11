package com.uniport.service.backtest;

import java.math.BigDecimal;

public record EtfRebalanceCandidate(
        String securityId,
        String name,
        BigDecimal weightPercent,
        String sentiment,
        String action,
        String reason
) {
}
