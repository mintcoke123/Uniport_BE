package com.uniport.service;

import com.uniport.dto.StockPriceDTO;
import com.uniport.dto.StockSearchItemDTO;
import com.uniport.dto.StockVisualDTO;
import com.uniport.service.kisws.PriceSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
public class VirtualStockService {

    public static final String CODE = "999999";
    public static final String NAME = "웨이브테크";
    public static final String MARKET = "VIRTUAL";

    private static final BigDecimal BASE_PRICE = new BigDecimal("1000000");
    private static final BigDecimal AMPLITUDE = new BigDecimal("200000");
    private static final long PERIOD_MILLIS = 5 * 60_000L;

    public boolean isVirtualStockCode(String stockCode) {
        return stockCode != null && CODE.equals(normalizeCode(stockCode));
    }

    public boolean matchesKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return false;
        }
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        return CODE.contains(normalized)
                || NAME.toLowerCase(Locale.ROOT).contains(normalized)
                || "wavetech".contains(normalized)
                || normalized.contains("wave");
    }

    public List<String> codes() {
        return List.of(CODE);
    }

    public BigDecimal priceAtMillis(long millis) {
        double radians = 2.0d * Math.PI * (Math.floorMod(millis, PERIOD_MILLIS) / (double) PERIOD_MILLIS);
        BigDecimal offset = BigDecimal.valueOf(Math.sin(radians))
                .multiply(AMPLITUDE)
                .setScale(0, RoundingMode.HALF_UP);
        return BASE_PRICE.add(offset);
    }

    public PriceSnapshot currentSnapshot() {
        long now = System.currentTimeMillis();
        BigDecimal currentPrice = priceAtMillis(now);
        BigDecimal change = currentPrice.subtract(BASE_PRICE);
        BigDecimal changeRate = change
                .multiply(new BigDecimal("100"))
                .divide(BASE_PRICE, 2, RoundingMode.HALF_UP);
        long phaseVolume = Math.floorMod(now, PERIOD_MILLIS) / 1000L;
        return new PriceSnapshot(currentPrice, change, changeRate, 1_000_000L + phaseVolume * 1_000L, now);
    }

    public StockPriceDTO currentPriceDto() {
        PriceSnapshot snapshot = currentSnapshot();
        return StockPriceDTO.builder()
                .stockCode(CODE)
                .stockName(NAME)
                .market(MARKET)
                .visual(visual())
                .currentPrice(snapshot.getCurrentPrice())
                .openPrice(BASE_PRICE)
                .closePrice(BASE_PRICE)
                .lowPrice(BASE_PRICE.subtract(AMPLITUDE))
                .highPrice(BASE_PRICE.add(AMPLITUDE))
                .changeAmount(snapshot.getChange())
                .changeRate(snapshot.getChangeRate())
                .volume(snapshot.getVolume())
                .build();
    }

    public StockSearchItemDTO searchItem() {
        return StockSearchItemDTO.builder()
                .stockId("KRX_" + CODE)
                .name(NAME)
                .symbol(CODE)
                .market(MARKET)
                .visual(visual())
                .build();
    }

    private StockVisualDTO visual() {
        return StockVisualDTO.builder()
                .type("FALLBACK_SYMBOL")
                .text("WAV")
                .bgColor("#E6F7F2")
                .textColor("#047857")
                .build();
    }

    private static String normalizeCode(String code) {
        String trimmed = code.trim();
        return trimmed.length() >= 6 ? trimmed : String.format("%6s", trimmed).replace(' ', '0');
    }
}
