package com.uniport.service.kisws;

import java.math.BigDecimal;

/**
 * 시세 캐시 한 건: 현재가, 등락, 등락률, 거래량, 갱신 시각.
 */
public class PriceSnapshot {

    private final BigDecimal currentPrice;
    private final BigDecimal change;
    private final BigDecimal changeRate;
    private final Long volume;
    private final long updatedAtMillis;

    public PriceSnapshot(BigDecimal currentPrice, BigDecimal change, BigDecimal changeRate,
                         Long volume, long updatedAtMillis) {
        this.currentPrice = currentPrice;
        this.change = change;
        this.changeRate = changeRate;
        this.volume = volume;
        this.updatedAtMillis = updatedAtMillis;
    }

    public BigDecimal getCurrentPrice() { return currentPrice; }
    public BigDecimal getChange() { return change; }
    public BigDecimal getChangeRate() { return changeRate; }
    public Long getVolume() { return volume; }
    public long getUpdatedAtMillis() { return updatedAtMillis; }
}
