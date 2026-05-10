package com.uniport.service;

import com.uniport.dto.InvestorSentimentDTO;
import com.uniport.dto.StockDetailDTO;
import com.uniport.dto.StockPriceDTO;
import com.uniport.dto.StockVisualDTO;
import com.uniport.entity.StockMaster;
import com.uniport.repository.HoldingRepository;
import com.uniport.repository.StockMasterRepository;
import com.uniport.repository.TeamHoldingRepository;
import com.uniport.service.kisws.KisWsSubscriptionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockServiceTest {

    private KisApiService kisApiService;
    private StockMasterRepository stockMasterRepository;
    private ManagedStockNewsService managedStockNewsService;
    private CommunityService communityService;
    private StockVisualAssetResolver stockVisualAssetResolver;
    private StockService stockService;

    @BeforeEach
    void setUp() {
        kisApiService = mock(KisApiService.class);
        stockMasterRepository = mock(StockMasterRepository.class);
        managedStockNewsService = mock(ManagedStockNewsService.class);
        communityService = mock(CommunityService.class);
        stockVisualAssetResolver = mock(StockVisualAssetResolver.class);
        stockService = new StockService(
                kisApiService,
                mock(HoldingRepository.class),
                mock(TeamHoldingRepository.class),
                mock(KisWsSubscriptionManager.class),
                stockMasterRepository,
                managedStockNewsService,
                communityService,
                stockVisualAssetResolver
        );
    }

    @Test
    void getStockDetail_includesStockVisual() {
        StockPriceDTO price = StockPriceDTO.builder()
                .stockCode("005930")
                .stockName("삼성전자")
                .currentPrice(new BigDecimal("70000"))
                .changeAmount(new BigDecimal("1000"))
                .changeRate(new BigDecimal("1.45"))
                .volume(1000L)
                .build();
        StockMaster master = StockMaster.builder()
                .code("005930")
                .nameKr("삼성전자")
                .market("KOSPI")
                .build();
        when(kisApiService.getStockPrice("005930")).thenReturn(price);
        when(stockMasterRepository.findById("005930")).thenReturn(Optional.of(master));
        when(managedStockNewsService.getNewsForStock("005930", "삼성전자", 3)).thenReturn(List.of());
        when(communityService.getInvestorSentiment("005930")).thenReturn(InvestorSentimentDTO.builder().build());
        when(communityService.getDiscussionCount("005930")).thenReturn(0);
        when(stockVisualAssetResolver.resolve("KOSPI", "005930", "삼성전자", null)).thenReturn(visual("삼성"));

        StockDetailDTO response = stockService.getStockDetail(5930L, null);

        assertEquals("KOSPI", response.getMarket());
        assertEquals("삼성", response.getVisual().getText());
        assertEquals("FALLBACK_SYMBOL", response.getVisual().getType());
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
