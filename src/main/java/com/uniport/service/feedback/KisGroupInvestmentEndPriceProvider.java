package com.uniport.service.feedback;

import com.uniport.service.KisApiService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class KisGroupInvestmentEndPriceProvider implements GroupInvestmentEndPriceProvider {

    private final KisApiService kisApiService;

    public KisGroupInvestmentEndPriceProvider(KisApiService kisApiService) {
        this.kisApiService = kisApiService;
    }

    @Override
    public BigDecimal resolveEndPrice(String stockCode, Instant endedAt, BigDecimal fallbackPrice) {
        try {
            var price = kisApiService.getStockPrice(stockCode);
            if (price != null && price.getCurrentPrice() != null && price.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {
                return price.getCurrentPrice();
            }
        } catch (Exception ignored) {
        }
        return fallbackPrice != null && fallbackPrice.compareTo(BigDecimal.ZERO) > 0 ? fallbackPrice : BigDecimal.ONE;
    }
}
