package com.uniport.service.feedback;

import java.math.BigDecimal;
import java.time.Instant;

public record TradePnlSnapshot(
        Long tradeId,
        Long decisionId,
        Long proposerId,
        String stockCode,
        String stockName,
        TradeSide side,
        int quantity,
        BigDecimal executedPrice,
        BigDecimal pnlAmount,
        BigDecimal pnlRate,
        Instant executedAt
) {
}
