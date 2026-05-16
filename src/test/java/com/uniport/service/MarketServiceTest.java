package com.uniport.service;

import com.uniport.dto.MarketStockItemDTO;
import com.uniport.dto.StockPriceDTO;
import com.uniport.dto.StockVisualDTO;
import com.uniport.service.kisws.KisWsSubscriptionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketServiceTest {

    private KisApiService kisApiService;
    private StockVisualAssetResolver stockVisualAssetResolver;
    private MarketService marketService;

    @BeforeEach
    void setUp() {
        kisApiService = mock(KisApiService.class);
        stockVisualAssetResolver = mock(StockVisualAssetResolver.class);
        marketService = new MarketService(
                kisApiService,
                mock(KisWsSubscriptionManager.class),
                stockVisualAssetResolver,
                new StockSymbolLogoUrlResolver("https://uniportbe-production.up.railway.app")
        );
    }

    @Test
    void getStocksForApi_includesStockVisual() {
        StockPriceDTO price = StockPriceDTO.builder()
                .stockCode("005930")
                .stockName("삼성전자")
                .currentPrice(new BigDecimal("70000"))
                .changeAmount(new BigDecimal("1000"))
                .changeRate(new BigDecimal("1.45"))
                .volume(1000L)
                .build();
        when(kisApiService.getVolumeRank()).thenReturn(List.of(price));
        when(stockVisualAssetResolver.resolve("KRX", "005930", "삼성전자", null)).thenReturn(visual("삼성"));

        List<MarketStockItemDTO> response = marketService.getStocksForApi("volume");

        assertEquals("KRX", response.get(0).getMarket());
        assertEquals("삼성", response.get(0).getVisual().getText());
    }

    private StockVisualDTO visual(String text) {
        return StockVisualDTO.builder()
                .type("FALLBACK_SYMBOL")
                .text(text)
                .bgColor("#EEF2FF")
                .textColor("#4F46E5")
                .build();
    }
}
