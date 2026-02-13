package com.uniport.service.kisws;

import java.math.BigDecimal;

/**
 * KIS 실시간 체결 캐시용 한 종목 데이터.
 */
public class RealtimeStock {

    private final String stockCode;
    private final BigDecimal currentPrice;
    private final BigDecimal change;
    private final BigDecimal changeRate;
    private final Long volume;
    private final long updatedAtMillis;

    public RealtimeStock(String stockCode, BigDecimal currentPrice, BigDecimal change,
                         BigDecimal changeRate, Long volume, long updatedAtMillis) {
        this.stockCode = stockCode;
        this.currentPrice = currentPrice;
        this.change = change;
        this.changeRate = changeRate;
        this.volume = volume;
        this.updatedAtMillis = updatedAtMillis;
    }

    public String getStockCode() { return stockCode; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public BigDecimal getChange() { return change; }
    public BigDecimal getChangeRate() { return changeRate; }
    public Long getVolume() { return volume; }
    public long getUpdatedAtMillis() { return updatedAtMillis; }
}
