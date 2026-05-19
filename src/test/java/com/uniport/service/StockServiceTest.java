package com.uniport.service;

import com.uniport.dto.FinancialDataItemDTO;
import com.uniport.dto.InvestorSentimentDTO;
import com.uniport.dto.StockDetailDTO;
import com.uniport.dto.StockPriceDTO;
import com.uniport.dto.StockVisualDTO;
import com.uniport.entity.ManagedNewsArticle;
import com.uniport.entity.StockMaster;
import com.uniport.entity.TeamAccount;
import com.uniport.entity.TeamHolding;
import com.uniport.entity.User;
import com.uniport.repository.HoldingRepository;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.StockMasterRepository;
import com.uniport.repository.TeamAccountRepository;
import com.uniport.repository.TeamHoldingRepository;
import com.uniport.service.kisws.KisWsSubscriptionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockServiceTest {

    private KisApiService kisApiService;
    private StockMasterRepository stockMasterRepository;
    private TeamAccountRepository teamAccountRepository;
    private TeamHoldingRepository teamHoldingRepository;
    private MatchingRoomMemberRepository matchingRoomMemberRepository;
    private ManagedStockNewsService managedStockNewsService;
    private CommunityService communityService;
    private StockVisualAssetResolver stockVisualAssetResolver;
    private WiseReportCompanyIntroductionClient companyIntroductionClient;
    private StockService stockService;

    @BeforeEach
    void setUp() {
        kisApiService = mock(KisApiService.class);
        stockMasterRepository = mock(StockMasterRepository.class);
        teamAccountRepository = mock(TeamAccountRepository.class);
        teamHoldingRepository = mock(TeamHoldingRepository.class);
        matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        managedStockNewsService = mock(ManagedStockNewsService.class);
        communityService = mock(CommunityService.class);
        stockVisualAssetResolver = mock(StockVisualAssetResolver.class);
        companyIntroductionClient = mock(WiseReportCompanyIntroductionClient.class);
        when(companyIntroductionClient.fetchCompanyIntroduction(anyString())).thenReturn(Optional.empty());
        when(companyIntroductionClient.fetchFinancialData(anyString())).thenReturn(List.of());
        stockService = new StockService(
                kisApiService,
                mock(HoldingRepository.class),
                teamHoldingRepository,
                teamAccountRepository,
                matchingRoomMemberRepository,
                mock(KisWsSubscriptionManager.class),
                stockMasterRepository,
                managedStockNewsService,
                communityService,
                stockVisualAssetResolver,
                new StockSymbolLogoUrlResolver("https://uniportbe-production.up.railway.app"),
                companyIntroductionClient
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
        when(kisApiService.getStockQuote("005930")).thenReturn(price);
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

    @Test
    void getStockDetail_usesExplicitRoomContextForCashAndHolding() {
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
        when(kisApiService.getStockQuote("005930")).thenReturn(price);
        when(stockMasterRepository.findById("005930")).thenReturn(Optional.of(master));
        when(managedStockNewsService.getNewsForStock("005930", "삼성전자", 3)).thenReturn(List.of());
        when(communityService.getInvestorSentiment("005930")).thenReturn(InvestorSentimentDTO.builder().build());
        when(communityService.getDiscussionCount("005930")).thenReturn(0);
        when(stockVisualAssetResolver.resolve("KOSPI", "005930", "삼성전자", null)).thenReturn(visual("삼성"));
        when(matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(77L, 5L)).thenReturn(true);
        when(teamAccountRepository.findByTeamId(77L)).thenReturn(Optional.of(
                TeamAccount.builder().teamId(77L).cashBalance(new BigDecimal("700000")).build()
        ));
        when(teamHoldingRepository.findByTeamIdAndStockCode(77L, "005930")).thenReturn(Optional.of(
                TeamHolding.builder()
                        .teamId(77L)
                        .stockCode("005930")
                        .quantity(3)
                        .averagePurchasePrice(new BigDecimal("65000"))
                        .build()
        ));

        StockDetailDTO response = stockService.getStockDetail(
                5930L,
                User.builder().id(5L).teamId("team-88").build(),
                "room-77"
        );

        assertEquals(new BigDecimal("700000"), response.getBuyableCash());
        assertEquals(10, response.getBuyableQuantity());
        assertEquals(3, response.getMyHolding().getQuantity());
    }

    @Test
    void getStockDetail_usesKisQuotePricesForDistinctMarketData() {
        StockPriceDTO price = StockPriceDTO.builder()
                .stockCode("005930")
                .stockName("삼성전자")
                .currentPrice(new BigDecimal("70000"))
                .openPrice(new BigDecimal("69000"))
                .closePrice(new BigDecimal("70000"))
                .lowPrice(new BigDecimal("68500"))
                .highPrice(new BigDecimal("71000"))
                .changeAmount(new BigDecimal("1000"))
                .changeRate(new BigDecimal("1.45"))
                .volume(1000L)
                .build();
        StockMaster master = StockMaster.builder()
                .code("005930")
                .nameKr("삼성전자")
                .market("KOSPI")
                .build();
        when(kisApiService.getStockQuote("005930")).thenReturn(price);
        when(stockMasterRepository.findById("005930")).thenReturn(Optional.of(master));
        when(managedStockNewsService.getNewsForStock("005930", "삼성전자", 3)).thenReturn(List.of());
        when(communityService.getInvestorSentiment("005930")).thenReturn(InvestorSentimentDTO.builder().build());
        when(communityService.getDiscussionCount("005930")).thenReturn(0);
        when(stockVisualAssetResolver.resolve("KOSPI", "005930", "삼성전자", null)).thenReturn(visual("삼성"));

        StockDetailDTO response = stockService.getStockDetail(5930L, null);

        assertEquals(new BigDecimal("69000"), response.getMarketData().getOpenPrice());
        assertEquals(new BigDecimal("70000"), response.getMarketData().getClosePrice());
        assertEquals(new BigDecimal("68500"), response.getMarketData().getLowPrice());
        assertEquals(new BigDecimal("71000"), response.getMarketData().getHighPrice());
    }

    @Test
    void getStockDetail_doesNotRecordMissingOhlcValues() {
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
        when(kisApiService.getStockQuote("005930")).thenReturn(price);
        when(stockMasterRepository.findById("005930")).thenReturn(Optional.of(master));
        when(managedStockNewsService.getNewsForStock("005930", "삼성전자", 3)).thenReturn(List.of());
        when(communityService.getInvestorSentiment("005930")).thenReturn(InvestorSentimentDTO.builder().build());
        when(communityService.getDiscussionCount("005930")).thenReturn(0);
        when(stockVisualAssetResolver.resolve("KOSPI", "005930", "삼성전자", null)).thenReturn(visual("삼성"));

        StockDetailDTO response = stockService.getStockDetail(5930L, null);

        assertNull(response.getMarketData().getOpenPrice());
        assertNull(response.getMarketData().getClosePrice());
        assertNull(response.getMarketData().getLowPrice());
        assertNull(response.getMarketData().getHighPrice());
    }

    @Test
    void getStockDetail_usesFirstAvailableCompanyIntroduction() {
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
        ManagedNewsArticle firstArticle = article(1L, "첫 번째 기사");
        ManagedNewsArticle secondArticle = article(2L, "두 번째 기사");
        when(kisApiService.getStockQuote("005930")).thenReturn(price);
        when(stockMasterRepository.findById("005930")).thenReturn(Optional.of(master));
        when(managedStockNewsService.getNewsForStock("005930", "삼성전자", 3))
                .thenReturn(List.of(firstArticle, secondArticle));
        when(managedStockNewsService.extractFinancialData(firstArticle)).thenReturn(List.of());
        when(managedStockNewsService.extractCompanyDescription(firstArticle)).thenReturn("");
        when(managedStockNewsService.extractCompanyDescription(secondArticle))
                .thenReturn("삼성전자는 반도체와 모바일 기기를 중심으로 글로벌 시장에서 사업을 전개하는 기업입니다.");
        when(communityService.getInvestorSentiment("005930")).thenReturn(InvestorSentimentDTO.builder().build());
        when(communityService.getDiscussionCount("005930")).thenReturn(0);
        when(stockVisualAssetResolver.resolve("KOSPI", "005930", "삼성전자", null)).thenReturn(visual("삼성"));

        StockDetailDTO response = stockService.getStockDetail(5930L, null);

        assertEquals(
                "삼성전자는 반도체와 모바일 기기를 중심으로 글로벌 시장에서 사업을 전개하는 기업입니다.",
                response.getCompanyInfo()
        );
    }

    @Test
    void getStockDetail_doesNotFabricateCompanyIntroductionWhenMissing() {
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
        when(kisApiService.getStockQuote("005930")).thenReturn(price);
        when(stockMasterRepository.findById("005930")).thenReturn(Optional.of(master));
        when(managedStockNewsService.getNewsForStock("005930", "삼성전자", 3)).thenReturn(List.of());
        when(communityService.getInvestorSentiment("005930")).thenReturn(InvestorSentimentDTO.builder().build());
        when(communityService.getDiscussionCount("005930")).thenReturn(0);
        when(stockVisualAssetResolver.resolve("KOSPI", "005930", "삼성전자", null)).thenReturn(visual("삼성"));

        StockDetailDTO response = stockService.getStockDetail(5930L, null);

        assertEquals("", response.getCompanyInfo());
    }

    @Test
    void getStockDetail_usesWiseReportCompanyIntroductionWhenManagedDataMissing() {
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
        String introduction = "동사는 1969년 설립된 글로벌 전자 기업으로 DX, DS 두 부문과 SDC, Harman으로 구성되어 있음.";
        when(kisApiService.getStockQuote("005930")).thenReturn(price);
        when(stockMasterRepository.findById("005930")).thenReturn(Optional.of(master));
        when(managedStockNewsService.getNewsForStock("005930", "삼성전자", 3)).thenReturn(List.of());
        when(companyIntroductionClient.fetchCompanyIntroduction("005930")).thenReturn(Optional.of(introduction));
        when(communityService.getInvestorSentiment("005930")).thenReturn(InvestorSentimentDTO.builder().build());
        when(communityService.getDiscussionCount("005930")).thenReturn(0);
        when(stockVisualAssetResolver.resolve("KOSPI", "005930", "삼성전자", null)).thenReturn(visual("삼성"));

        StockDetailDTO response = stockService.getStockDetail(5930L, null);

        assertEquals(introduction, response.getCompanyInfo());
    }

    @Test
    void getStockDetail_usesWiseReportFinancialDataWhenManagedNewsHasNoFinancialData() {
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
        FinancialDataItemDTO wiseReportFinancialData = FinancialDataItemDTO.builder()
                .quarter("2025/12")
                .revenue(new BigDecimal("3336059"))
                .operatingProfit(new BigDecimal("436011"))
                .value("매출 3,336,059억원 · 영업이익 436,011억원")
                .build();
        when(kisApiService.getStockQuote("005930")).thenReturn(price);
        when(stockMasterRepository.findById("005930")).thenReturn(Optional.of(master));
        when(managedStockNewsService.getNewsForStock("005930", "삼성전자", 3)).thenReturn(List.of());
        when(companyIntroductionClient.fetchFinancialData("005930")).thenReturn(List.of(wiseReportFinancialData));
        when(communityService.getInvestorSentiment("005930")).thenReturn(InvestorSentimentDTO.builder().build());
        when(communityService.getDiscussionCount("005930")).thenReturn(0);
        when(stockVisualAssetResolver.resolve("KOSPI", "005930", "삼성전자", null)).thenReturn(visual("삼성"));

        StockDetailDTO response = stockService.getStockDetail(5930L, null);

        assertEquals(1, response.getFinancialData().size());
        assertEquals("2025/12", response.getFinancialData().get(0).getQuarter());
        assertEquals("매출 3,336,059억원 · 영업이익 436,011억원", response.getFinancialData().get(0).getValue());
    }

    @Test
    void getStockDetail_includesTeamBuyableCashAndQuantityForTeamUser() {
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
        User user = User.builder().id(7L).teamId("team-123").build();
        when(kisApiService.getStockQuote("005930")).thenReturn(price);
        when(stockMasterRepository.findById("005930")).thenReturn(Optional.of(master));
        when(teamAccountRepository.findByTeamId(123L)).thenReturn(Optional.of(TeamAccount.builder()
                .teamId(123L)
                .cashBalance(new BigDecimal("210000"))
                .build()));
        when(managedStockNewsService.getNewsForStock("005930", "삼성전자", 3)).thenReturn(List.of());
        when(communityService.getInvestorSentiment("005930")).thenReturn(InvestorSentimentDTO.builder().build());
        when(communityService.getDiscussionCount("005930")).thenReturn(0);
        when(stockVisualAssetResolver.resolve("KOSPI", "005930", "삼성전자", null)).thenReturn(visual("삼성"));

        StockDetailDTO response = stockService.getStockDetail(5930L, user);

        assertEquals(new BigDecimal("210000"), response.getBuyableCash());
        assertEquals(3, response.getBuyableQuantity());
    }

    private StockVisualDTO visual(String text) {
        return StockVisualDTO.builder()
                .type("FALLBACK_SYMBOL")
                .text(text)
                .bgColor("#EEF2FF")
                .textColor("#4F46E5")
                .build();
    }

    private ManagedNewsArticle article(Long id, String title) {
        return ManagedNewsArticle.builder()
                .id(id)
                .newsKey("news_" + id)
                .title(title)
                .sourceLabel("Uniport")
                .summary("요약")
                .publishedAt(LocalDateTime.of(2026, 5, 16, 9, 0))
                .build();
    }
}
