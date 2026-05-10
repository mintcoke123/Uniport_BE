package com.uniport.service.feedback;

import java.math.BigDecimal;
import java.time.Instant;

public interface GroupInvestmentEndPriceProvider {

    BigDecimal resolveEndPrice(String stockCode, Instant endedAt, BigDecimal fallbackPrice);
}
