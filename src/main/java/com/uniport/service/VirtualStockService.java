package com.uniport.service;

import com.uniport.dto.StockPriceDTO;
import com.uniport.dto.StockSearchItemDTO;
import com.uniport.dto.StockVisualDTO;
import com.uniport.service.kisws.PriceSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class VirtualStockService {

    public static final String CODE = "999999";
    public static final String NAME = "웨이브테크";
    public static final String MARKET = "VIRTUAL";

    private static final Map<String, VirtualStockDefinition> STOCKS = buildStocks();

    public boolean isVirtualStockCode(String stockCode) {
        return stockCode != null && STOCKS.containsKey(normalizeCode(stockCode));
    }

    public boolean matchesKeyword(String keyword) {
        return !searchItemsMatching(keyword).isEmpty();
    }

    public List<StockSearchItemDTO> searchItemsMatching(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        boolean genericVirtual = normalized.contains("가상") || normalized.contains("virtual");
        return STOCKS.values().stream()
                .filter(stock -> genericVirtual || stock.matches(normalized))
                .map(this::searchItem)
                .toList();
    }

    public List<String> codes() {
        return List.copyOf(STOCKS.keySet());
    }

    public BigDecimal priceAtMillis(long millis) {
        return priceAtMillis(CODE, millis);
    }

    public BigDecimal priceAtMillis(String stockCode, long millis) {
        VirtualStockDefinition stock = definitionFor(stockCode);
        double radians = 2.0d * Math.PI * (Math.floorMod(millis, stock.periodMillis) / (double) stock.periodMillis);
        BigDecimal offset = BigDecimal.valueOf(Math.sin(radians))
                .multiply(stock.amplitude)
                .setScale(0, RoundingMode.HALF_UP);
        return stock.basePrice.add(offset);
    }

    public PriceSnapshot currentSnapshot() {
        return currentSnapshot(CODE);
    }

    public PriceSnapshot currentSnapshot(String stockCode) {
        VirtualStockDefinition stock = definitionFor(stockCode);
        long now = System.currentTimeMillis();
        BigDecimal currentPrice = priceAtMillis(stock.code, now);
        BigDecimal change = currentPrice.subtract(stock.basePrice);
        BigDecimal changeRate = change
                .multiply(new BigDecimal("100"))
                .divide(stock.basePrice, 2, RoundingMode.HALF_UP);
        long phaseVolume = Math.floorMod(now, stock.periodMillis) / 1000L;
        return new PriceSnapshot(currentPrice, change, changeRate, 1_000_000L + phaseVolume * 1_000L, now);
    }

    public StockPriceDTO currentPriceDto() {
        return currentPriceDto(CODE);
    }

    public StockPriceDTO currentPriceDto(String stockCode) {
        VirtualStockDefinition stock = definitionFor(stockCode);
        PriceSnapshot snapshot = currentSnapshot(stock.code);
        return StockPriceDTO.builder()
                .stockCode(stock.code)
                .stockName(stock.name)
                .market(MARKET)
                .visual(visual(stock))
                .currentPrice(snapshot.getCurrentPrice())
                .openPrice(stock.basePrice)
                .closePrice(stock.basePrice)
                .lowPrice(stock.basePrice.subtract(stock.amplitude))
                .highPrice(stock.basePrice.add(stock.amplitude))
                .changeAmount(snapshot.getChange())
                .changeRate(snapshot.getChangeRate())
                .volume(snapshot.getVolume())
                .build();
    }

    public StockSearchItemDTO searchItem() {
        return searchItem(definitionFor(CODE));
    }

    private StockSearchItemDTO searchItem(VirtualStockDefinition stock) {
        return StockSearchItemDTO.builder()
                .stockId("KRX_" + stock.code)
                .name(stock.name)
                .symbol(stock.code)
                .market(MARKET)
                .visual(visual(stock))
                .build();
    }

    private StockVisualDTO visual(VirtualStockDefinition stock) {
        return StockVisualDTO.builder()
                .type("FALLBACK_SYMBOL")
                .text(stock.visualText)
                .bgColor(stock.bgColor)
                .textColor(stock.textColor)
                .build();
    }

    private VirtualStockDefinition definitionFor(String stockCode) {
        VirtualStockDefinition stock = STOCKS.get(normalizeCode(stockCode));
        if (stock == null) {
            throw new IllegalArgumentException("Unknown virtual stock code: " + stockCode);
        }
        return stock;
    }

    private static Map<String, VirtualStockDefinition> buildStocks() {
        Map<String, VirtualStockDefinition> stocks = new LinkedHashMap<>();
        put(stocks, new VirtualStockDefinition(
                CODE,
                NAME,
                "wavetech wave",
                "WAV",
                "#E6F7F2",
                "#047857",
                new BigDecimal("1000000"),
                new BigDecimal("200000"),
                5 * 60_000L
        ));
        put(stocks, new VirtualStockDefinition(
                "999998",
                "뉴로펄스",
                "neuropulse neuro pulse",
                "NEU",
                "#EEF2FF",
                "#4338CA",
                new BigDecimal("500000"),
                new BigDecimal("75000"),
                3 * 60_000L
        ));
        put(stocks, new VirtualStockDefinition(
                "999997",
                "솔라리온",
                "solarion solar",
                "SOL",
                "#FEF3C7",
                "#B45309",
                new BigDecimal("1200000"),
                new BigDecimal("300000"),
                7 * 60_000L
        ));
        put(stocks, new VirtualStockDefinition(
                "999996",
                "퀀텀브릿지",
                "quantumbridge quantum bridge",
                "QBR",
                "#FCE7F3",
                "#BE185D",
                new BigDecimal("250000"),
                new BigDecimal("50000"),
                2 * 60_000L
        ));
        put(stocks, new VirtualStockDefinition(
                "999995",
                "루미나칩",
                "luminachip lumina chip",
                "LUM",
                "#E0F2FE",
                "#0369A1",
                new BigDecimal("750000"),
                new BigDecimal("150000"),
                10 * 60_000L
        ));
        put(stocks, new VirtualStockDefinition(
                "999994",
                "에코스핀",
                "ecospin eco spin",
                "ECO",
                "#DCFCE7",
                "#15803D",
                new BigDecimal("350000"),
                new BigDecimal("100000"),
                4 * 60_000L
        ));
        return Collections.unmodifiableMap(stocks);
    }

    private static void put(Map<String, VirtualStockDefinition> stocks, VirtualStockDefinition stock) {
        stocks.put(stock.code, stock);
    }

    private static String normalizeCode(String code) {
        String trimmed = code.trim();
        return trimmed.length() >= 6 ? trimmed : String.format("%6s", trimmed).replace(' ', '0');
    }

    private static final class VirtualStockDefinition {
        private final String code;
        private final String name;
        private final String aliases;
        private final String visualText;
        private final String bgColor;
        private final String textColor;
        private final BigDecimal basePrice;
        private final BigDecimal amplitude;
        private final long periodMillis;

        private VirtualStockDefinition(String code,
                                       String name,
                                       String aliases,
                                       String visualText,
                                       String bgColor,
                                       String textColor,
                                       BigDecimal basePrice,
                                       BigDecimal amplitude,
                                       long periodMillis) {
            this.code = code;
            this.name = name;
            this.aliases = aliases;
            this.visualText = visualText;
            this.bgColor = bgColor;
            this.textColor = textColor;
            this.basePrice = basePrice;
            this.amplitude = amplitude;
            this.periodMillis = periodMillis;
        }

        private boolean matches(String normalizedKeyword) {
            return code.contains(normalizedKeyword)
                    || name.toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                    || aliases.contains(normalizedKeyword);
        }
    }
}
