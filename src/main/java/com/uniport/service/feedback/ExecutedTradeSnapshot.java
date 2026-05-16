package com.uniport.service.feedback;

import java.math.BigDecimal;
import java.time.Instant;

public record ExecutedTradeSnapshot(
        Long tradeId,
        Long decisionId,
        Long proposerId,
        String stockCode,
        String stockName,
        TradeSide side,
        int quantity,
        BigDecimal executedPrice,
        String reason,
        BigDecimal feeAmount,
        Instant executedAt
) {
}
