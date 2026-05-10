package com.uniport.service.feedback;

import java.math.BigDecimal;
import java.time.Instant;

public record GroupInvestmentSessionSnapshot(
        Long sessionId,
        Long roomId,
        BigDecimal initialCapital,
        Instant endedAt
) {
}
