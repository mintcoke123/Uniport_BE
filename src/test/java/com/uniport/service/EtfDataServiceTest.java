package com.uniport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.dto.CustomEtfDetailResponseDTO;
import com.uniport.dto.CustomEtfAssetSearchResponseDTO;
import com.uniport.dto.CustomEtfCreateRequestDTO;
import com.uniport.dto.CustomEtfItemRequestDTO;
import com.uniport.dto.CustomEtfMutationResponseDTO;
import com.uniport.dto.EtfPortfolioFitRecommendationRequestDTO;
import com.uniport.dto.EtfPortfolioFitRecommendationResponseDTO;
import com.uniport.dto.EtfAnalysisReportResponseDTO;
import com.uniport.dto.EtfAnalysisRequestDTO;
import com.uniport.dto.EtfAnalysisStartResponseDTO;
import com.uniport.dto.EtfDiscoveryDetailResponseDTO;
import com.uniport.dto.EtfDiscoveryResponseDTO;
import com.uniport.dto.StockVisualDTO;
import com.uniport.entity.AssetMaster;
import com.uniport.entity.ManagedEtf;
import com.uniport.entity.ManagedEtfAnalysisReport;
import com.uniport.entity.StockMaster;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.AssetMasterRepository;
import com.uniport.repository.AssetAliasRepository;
import com.uniport.repository.AssetPriceDailyRepository;
import com.uniport.repository.ManagedEtfAnalysisReportRepository;
import com.uniport.repository.ManagedEtfFavoriteRepository;
import com.uniport.repository.ManagedEtfRepository;
import com.uniport.repository.StockMasterRepository;
import com.uniport.service.backtest.BacktestRequest;
import com.uniport.service.backtest.BacktestPricePoint;
import com.uniport.service.backtest.BacktestResult;
import com.uniport.service.backtest.EtfAiFeedbackService;
import com.uniport.service.backtest.EtfBacktestEngine;
import com.uniport.service.backtest.EtfNewsExposure;
import com.uniport.service.backtest.HistoricalPriceProvider;
import com.uniport.service.backtest.InsightFacts;
import com.uniport.service.backtest.RuleBasedFeedback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EtfDataServiceTest {

    private ManagedEtfRepository managedEtfRepository;
    private ManagedEtfAnalysisReportRepository managedEtfAnalysisReportRepository;
    private ManagedEtfFavoriteRepository managedEtfFavoriteRepository;
    private StockMasterRepository stockMasterRepository;
    private AssetMasterRepository assetMasterRepository;
    private AssetAliasRepository assetAliasRepository;
    private AssetPriceDailyRepository assetPriceDailyRepository;
    private HistoricalPriceProvider historicalPriceProvider;
    private EtfBacktestEngine etfBacktestEngine;
    private EtfAiFeedbackService etfAiFeedbackService;
    private EtfNewsExposureService etfNewsExposureService;
    private StockVisualAssetResolver stockVisualAssetResolver;
    private YahooAssetSearchClient yahooAssetSearchClient;
    private PortfolioFitModelClient portfolioFitModelClient;
    private StockSymbolLogoUrlResolver stockSymbolLogoUrlResolver;
    private EtfDataService etfDataService;

    @BeforeEach
    void setUp() {
        managedEtfRepository = mock(ManagedEtfRepository.class);
        managedEtfAnalysisReportRepository = mock(ManagedEtfAnalysisReportRepository.class);
        managedEtfFavoriteRepository = mock(ManagedEtfFavoriteRepository.class);
        stockMasterRepository = mock(StockMasterRepository.class);
        assetMasterRepository = mock(AssetMasterRepository.class);
        assetAliasRepository = mock(AssetAliasRepository.class);
        assetPriceDailyRepository = mock(AssetPriceDailyRepository.class);
        historicalPriceProvider = mock(HistoricalPriceProvider.class);
        etfBacktestEngine = mock(EtfBacktestEngine.class);
        etfAiFeedbackService = mock(EtfAiFeedbackService.class);
        etfNewsExposureService = mock(EtfNewsExposureService.class);
        stockVisualAssetResolver = mock(StockVisualAssetResolver.class);
        yahooAssetSearchClient = mock(YahooAssetSearchClient.class);
        portfolioFitModelClient = mock(PortfolioFitModelClient.class);
        stockSymbolLogoUrlResolver = new StockSymbolLogoUrlResolver("https://uniportbe-production.up.railway.app");
        when(historicalPriceProvider.getSecurityPriceSeries(any(), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(this::fullCoverageSeries);
        when(historicalPriceProvider.getSecurityPriceSeriesForEligibility(any(), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(this::fullCoverageSeries);
        when(etfNewsExposureService.summarize(any())).thenReturn(EtfNewsExposure.empty());
        etfDataService = new EtfDataService(
                managedEtfRepository,
                managedEtfAnalysisReportRepository,
                managedEtfFavoriteRepository,
                stockMasterRepository,
                assetMasterRepository,
                assetAliasRepository,
                assetPriceDailyRepository,
                historicalPriceProvider,
                etfBacktestEngine,
                etfAiFeedbackService,
                etfNewsExposureService,
                stockVisualAssetResolver,
                yahooAssetSearchClient,
                portfolioFitModelClient,
                stockSymbolLogoUrlResolver
        );
    }

    @Test
    void getCustomEtf_includesHoldingVisual() {
        User user = User.builder().id(1L).build();
        ManagedEtf etf = ManagedEtf.builder()
                .etfCode("ETF_CUSTOM")
                .ownerUserId(1L)
                .sourceType("CUSTOM")
                .title("나만의 반도체 ETF")
                .holdingsJson("[{\"stockId\":\"KRX_005930\",\"weight\":100}]")
                .build();
        StockMaster samsung = stock("005930", "삼성전자", "KOSPI");
        when(managedEtfRepository.findByEtfCode("ETF_CUSTOM")).thenReturn(Optional.of(etf));
        when(stockMasterRepository.findById("005930")).thenReturn(Optional.of(samsung));
        when(stockVisualAssetResolver.resolve("KOSPI", "005930", "삼성전자", null)).thenReturn(visual("삼성"));

        CustomEtfDetailResponseDTO response = etfDataService.getCustomEtf(user, "ETF_CUSTOM");

        assertEquals("삼성전자", response.getItems().get(0).getName());
        assertEquals("005930", response.getItems().get(0).getSymbol());
        assertEquals("삼성", response.getItems().get(0).getVisual().getText());
    }

    @Test
    void getDiscoveryDetail_includesHoldingVisual() {
        ManagedEtf etf = ManagedEtf.builder()
                .etfCode("ETF_DISCOVERY")
                .sourceType("DISCOVERY")
                .title("2차전지 ETF")
                .holdingsJson("[{\"stockId\":\"KRX_373220\",\"weight\":100}]")
                .build();
        StockMaster lges = stock("373220", "LG에너지솔루션", "KOSPI");
        when(managedEtfRepository.findByEtfCode("ETF_DISCOVERY")).thenReturn(Optional.of(etf));
        when(stockMasterRepository.findById("373220")).thenReturn(Optional.of(lges));
        when(managedEtfFavoriteRepository.countByEtfCode("ETF_DISCOVERY")).thenReturn(0L);
        when(stockVisualAssetResolver.resolve("KOSPI", "373220", "LG에너지솔루션", null)).thenReturn(visual("LG"));

        EtfDiscoveryDetailResponseDTO response = etfDataService.getDiscoveryDetail("ETF_DISCOVERY", "1Y", null);

        assertEquals("LG에너지솔루션", response.getHoldings().get(0).getName());
        assertEquals("373220", response.getHoldings().get(0).getSymbol());
        assertEquals("LG", response.getHoldings().get(0).getVisual().getText());
    }

    @Test
    void getPopularEtfs_exposesFigmaPopularListMetadata() {
        ManagedEtf aiTech = discoveryEtf("ETF_AI_TECH", "AI 테크", "기술", "매우 낮음", "24.8", 9999,
                "[{\"tag\":\"반도체\"},{\"tag\":\"빅테크\"},{\"badge\":\"인기\"},{\"subtitle\":\"성장 중심\"},{\"description\":\"전 세계 AI 혁명을 주도하는 포트폴리오입니다.\"}]",
                "[{\"stockId\":\"US_AAPL\",\"weight\":40,\"changeRate\":39.0}]");
        ManagedEtf dividend = discoveryEtf("ETF_DIVIDEND", "배당 귀족", "배당", "낮음", "8.5", 3200,
                "[{\"tag\":\"배당\"},{\"badge\":\"안정\"},{\"subtitle\":\"현금 흐름\"},{\"description\":\"꾸준한 배당주 중심 포트폴리오입니다.\"}]",
                "[{\"stockId\":\"US_KO\",\"weight\":100,\"changeRate\":4.2}]");
        when(managedEtfRepository.findAll()).thenReturn(List.of(aiTech, dividend));
        when(managedEtfFavoriteRepository.countByEtfCode("ETF_AI_TECH")).thenReturn(0L);
        when(managedEtfFavoriteRepository.countByEtfCode("ETF_DIVIDEND")).thenReturn(0L);

        EtfDiscoveryResponseDTO response = etfDataService.getPopularEtfs("LATEST", null, null, 0, 10, null);

        assertEquals(2, response.getTotalCount());
        assertEquals(List.of("기술", "배당"), response.getThemes());
        assertEquals("인기", response.getItems().get(0).getBadgeLabel());
        assertEquals("성장 중심", response.getItems().get(0).getSubtitle());
        assertEquals(9999, response.getItems().get(0).getFollowerCount());
        assertEquals(24.8, response.getItems().get(0).getDailyExpectedReturnRate());
    }

    @Test
    void getDiscoveryDetail_exposesFigmaDetailMetadataAndHoldingChangeRates() {
        ManagedEtf etf = discoveryEtf("ETF_AI_TECH", "AI 테크", "기술", "매우 낮음", "24.8", 9999,
                "[{\"tag\":\"반도체\"},{\"tag\":\"빅테크\"},{\"tag\":\"LLM\"},{\"tag\":\"성장주\"},{\"badge\":\"인기\"},{\"subtitle\":\"성장 중심\"},{\"description\":\"전 세계 AI 혁명을 주도하는 반도체 및 소프트웨어 핵심 기업 7곳에 집중 투자하는 포트폴리오입니다.\"}]",
                "[{\"stockId\":\"US_NVDA\",\"weight\":35,\"changeRate\":39.0}]");
        AssetMaster nvidia = asset("US_NVDA", "STOCK", "NVIDIA Corp.", "NVDA", "NASDAQ", "USD");
        when(managedEtfRepository.findByEtfCode("ETF_AI_TECH")).thenReturn(Optional.of(etf));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("US_NVDA")).thenReturn(Optional.of(nvidia));
        when(managedEtfFavoriteRepository.countByEtfCode("ETF_AI_TECH")).thenReturn(0L);
        when(stockVisualAssetResolver.resolve("NASDAQ", "NVDA", "NVIDIA Corp.", null)).thenReturn(visual("NVDA"));

        EtfDiscoveryDetailResponseDTO response = etfDataService.getDiscoveryDetail("ETF_AI_TECH", "1Y", null);

        assertEquals("인기", response.getBadgeLabel());
        assertEquals("성장 중심", response.getSubtitle());
        assertEquals("전 세계 AI 혁명을 주도하는 반도체 및 소프트웨어 핵심 기업 7곳에 집중 투자하는 포트폴리오입니다.",
                response.getDescription());
        assertEquals("매우 낮음", response.getRiskLevel());
        assertEquals(List.of("반도체", "빅테크", "LLM", "성장주"), response.getTags());
        assertEquals(9999, response.getFavoriteCount());
        assertEquals(39.0, response.getHoldings().get(0).getChangeRate());
    }

    @Test
    void applyDiscoveryEtf_createsCustomEtfForUser() {
        User user = User.builder().id(1L).build();
        ManagedEtf discovery = discoveryEtf("ETF_AI_TECH", "AI 테크", "기술", "매우 낮음", "24.8", 9999,
                "[{\"tag\":\"반도체\"},{\"badge\":\"인기\"},{\"subtitle\":\"성장 중심\"}]",
                "[{\"stockId\":\"US_AAPL\",\"weight\":60,\"changeRate\":18.0},{\"stockId\":\"US_NVDA\",\"weight\":40,\"changeRate\":39.0}]");
        AssetMaster apple = asset("US_AAPL", "STOCK", "Apple Inc.", "AAPL", "NASDAQ", "USD");
        AssetMaster nvidia = asset("US_NVDA", "STOCK", "NVIDIA Corp.", "NVDA", "NASDAQ", "USD");
        when(managedEtfRepository.findByEtfCode("ETF_AI_TECH")).thenReturn(Optional.of(discovery));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("US_AAPL")).thenReturn(Optional.of(apple));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("US_NVDA")).thenReturn(Optional.of(nvidia));
        when(managedEtfRepository.save(any())).thenAnswer(invocation -> {
            ManagedEtf saved = invocation.getArgument(0);
            saved.setCreatedAt(java.time.LocalDateTime.parse("2026-05-10T12:00:00"));
            saved.setUpdatedAt(java.time.LocalDateTime.parse("2026-05-10T12:00:00"));
            return saved;
        });

        CustomEtfMutationResponseDTO response = etfDataService.applyDiscoveryEtf(user, "ETF_AI_TECH");

        assertEquals("AI 테크", response.getTitle());
        assertEquals(100, response.getTotalWeight());
        assertEquals("2026-05-10T12:00Z", response.getCreatedAt());
        assertEquals("2026-05-10T12:00Z", response.getUpdatedAt());
    }

    @Test
    void searchAssets_supportsDomesticAndUsStocksOnly() {
        StockMaster samsung = stock("005930", "삼성전자", "KOSPI");
        AssetMaster samsungAsset = asset("KRX_005930", "STOCK", "삼성전자", "005930", "KOSPI", "KRW");
        AssetMaster apple = asset("US_AAPL", "STOCK", "Apple Inc.", "AAPL", "NASDAQ", "USD");
        when(stockMasterRepository.searchForEtfAssetCandidates(eq("삼성"), any()))
                .thenReturn(List.of(samsung));
        when(stockMasterRepository.searchForEtfAssetCandidates(eq("apple"), any()))
                .thenReturn(List.of());
        when(assetMasterRepository.searchActive(eq("삼성"), eq("STOCK"), eq(null), any(Pageable.class)))
                .thenReturn(List.of());
        when(assetMasterRepository.searchActive(eq("apple"), eq("STOCK"), eq(null), any(Pageable.class)))
                .thenReturn(List.of(apple));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("KRX_005930")).thenReturn(Optional.of(samsungAsset));
        when(stockVisualAssetResolver.resolve("KOSPI", "005930", "삼성전자", null)).thenReturn(visual("삼성"));
        when(stockVisualAssetResolver.resolve("NASDAQ", "AAPL", "Apple Inc.", null)).thenReturn(visual("AAPL"));

        CustomEtfAssetSearchResponseDTO domestic = etfDataService.searchAssets("삼성", "STOCK", null, 0, 10);
        CustomEtfAssetSearchResponseDTO us = etfDataService.searchAssets("apple", "STOCK", null, 0, 10);

        assertEquals("KRX_005930", domestic.getItems().get(0).getAssetId());
        assertEquals("STOCK", domestic.getItems().get(0).getAssetType());
        assertEquals("KRW", domestic.getItems().get(0).getCurrency());
        assertEquals("US_AAPL", us.getItems().get(0).getAssetId());
        assertEquals("NASDAQ", us.getItems().get(0).getMarket());
    }

    @Test
    void searchAssets_rejectsBondOrCashAssetType() {
        ApiException bond = assertThrows(ApiException.class,
                () -> etfDataService.searchAssets("채권", "BOND", null, 0, 10));
        ApiException cash = assertThrows(ApiException.class,
                () -> etfDataService.searchAssets("현금", "CASH", null, 0, 10));

        assertEquals("assetType must be STOCK, ETF, LEVERAGED_ETF, or INVERSE_ETF", bond.getMessage());
        assertEquals("assetType must be STOCK, ETF, LEVERAGED_ETF, or INVERSE_ETF", cash.getMessage());
    }

    @Test
    void searchAssets_reclassifiesEtfLikeUsAssetsFromStockMasterData() {
        AssetMaster spy = asset("US_SPY", "STOCK", "State Street SPDR S&P 500 ETF Trust", "SPY", "NYSE", "USD");
        AssetMaster tqqq = asset("US_TQQQ", "STOCK", "ProShares UltraPro QQQ", "TQQQ", "NASDAQ", "USD");
        AssetMaster sqqq = asset("US_SQQQ", "STOCK", "ProShares UltraPro Short QQQ", "SQQQ", "NASDAQ", "USD");
        when(assetMasterRepository.searchActive(eq("SPY"), eq(null), eq(null), any(Pageable.class))).thenReturn(List.of(spy));
        when(assetMasterRepository.searchActive(eq("TQQQ"), eq(null), eq(null), any(Pageable.class))).thenReturn(List.of(tqqq));
        when(assetMasterRepository.searchActive(eq("SQQQ"), eq(null), eq(null), any(Pageable.class))).thenReturn(List.of(sqqq));
        when(stockMasterRepository.searchForEtfAssetCandidates(any(), any(Pageable.class))).thenReturn(List.of());
        when(stockVisualAssetResolver.resolve(any(), any(), any(), any()))
                .thenAnswer(invocation -> visual(invocation.getArgument(1)));

        CustomEtfAssetSearchResponseDTO spyResult = etfDataService.searchAssets("SPY", "ALL", "ALL", 0, 10);
        CustomEtfAssetSearchResponseDTO tqqqResult = etfDataService.searchAssets("TQQQ", "ALL", "ALL", 0, 10);
        CustomEtfAssetSearchResponseDTO sqqqResult = etfDataService.searchAssets("SQQQ", "ALL", "ALL", 0, 10);

        assertEquals("ETF", spyResult.getItems().get(0).getAssetType());
        assertEquals("LEVERAGED_ETF", tqqqResult.getItems().get(0).getAssetType());
        assertEquals("INVERSE_ETF", sqqqResult.getItems().get(0).getAssetType());
    }

    @Test
    void searchAssets_includesDomesticStockPendingRealPriceVerification() {
        StockMaster samsung = stock("005930", "삼성전자", "KOSPI");
        when(stockMasterRepository.searchForEtfAssetCandidates(eq("삼성"), any()))
                .thenReturn(List.of(samsung));
        when(assetMasterRepository.searchActive(eq("삼성"), eq("STOCK"), eq(null), any(Pageable.class)))
                .thenReturn(List.of());
        when(assetMasterRepository.findByAssetIdAndActiveTrue("KRX_005930")).thenReturn(Optional.empty());
        when(stockVisualAssetResolver.resolve("KOSPI", "005930", "삼성전자", null)).thenReturn(visual("삼성"));

        CustomEtfAssetSearchResponseDTO response = etfDataService.searchAssets("삼성", "STOCK", null, 0, 10);

        assertEquals(1, response.getItems().size());
        assertEquals("KRX_005930", response.getItems().get(0).getAssetId());
        assertEquals(true, response.getItems().get(0).getBacktestEnabled());
        assertEquals("PENDING_VERIFICATION", response.getItems().get(0).getDataStatus());
        assertEquals("분석 시점에 실가격을 확인하며, 가격 데이터가 부족하면 분석이 제한됩니다.",
                response.getItems().get(0).getDataStatusMessage());
        assertEquals(1, response.getTotalCount());
    }

    @Test
    void searchAssets_paginatesAllMatchingDomesticStockNames() {
        List<StockMaster> matches = new ArrayList<>();
        matches.add(stock("016360", "삼성증권", "KOSPI"));
        matches.add(stock("008560", "메리츠증권", "KOSPI"));
        for (int index = 1; index <= 31; index++) {
            matches.add(stock(String.format("%06d", index), "증권 검색 종목 " + index, "KOSPI"));
        }
        when(assetMasterRepository.searchActive(eq("증권"), eq("STOCK"), eq(null), any(Pageable.class)))
                .thenReturn(List.of());
        when(assetAliasRepository.searchActiveAssetMatches(eq("증권"), eq("STOCK"), eq(null), any(Pageable.class)))
                .thenReturn(List.of());
        when(stockMasterRepository.searchForEtfAssetCandidates(eq("증권"), any()))
                .thenReturn(matches);
        when(assetMasterRepository.findByAssetIdAndActiveTrue(any()))
                .thenReturn(Optional.empty());
        when(stockVisualAssetResolver.resolve(any(), any(), any(), any()))
                .thenAnswer(invocation -> visual(invocation.getArgument(1)));

        CustomEtfAssetSearchResponseDTO firstPage = etfDataService.searchAssets("증권", "STOCK", null, 0, 30);
        CustomEtfAssetSearchResponseDTO secondPage = etfDataService.searchAssets("증권", "STOCK", null, 1, 30);

        assertEquals(30, firstPage.getItems().size());
        assertEquals(33, firstPage.getTotalCount());
        assertEquals(true, firstPage.getHasNext());
        assertEquals(3, secondPage.getItems().size());
        assertEquals(false, secondPage.getHasNext());
        assertTrue(firstPage.getItems().stream().anyMatch(item -> "삼성증권".equals(item.getName())));
        assertTrue(firstPage.getItems().stream().anyMatch(item -> "메리츠증권".equals(item.getName())));
    }

    @Test
    void searchAssets_matchesKoreanAliasForUsStock() {
        AssetMaster apple = asset("US_AAPL", "STOCK", "Apple Inc.", "AAPL", "NASDAQ", "USD");
        when(assetMasterRepository.searchActive(eq("애플"), eq("STOCK"), eq(null), any(Pageable.class)))
                .thenReturn(List.of());
        when(assetAliasRepository.searchActiveAssetMatches(eq("애플"), eq("STOCK"), eq(null), any(Pageable.class)))
                .thenReturn(List.of(apple));
        when(stockMasterRepository.searchForEtfAssetCandidates(eq("애플"), any()))
                .thenReturn(List.of());
        when(stockVisualAssetResolver.resolve("NASDAQ", "AAPL", "Apple Inc.", null)).thenReturn(visual("AAPL"));

        CustomEtfAssetSearchResponseDTO response = etfDataService.searchAssets("애플", "STOCK", null, 0, 10);

        assertEquals(1, response.getItems().size());
        assertEquals("US_AAPL", response.getItems().get(0).getAssetId());
        assertEquals("AAPL", response.getItems().get(0).getSymbol());
        assertEquals("USD", response.getItems().get(0).getCurrency());
    }

    @Test
    void searchAssets_allowsVerifiedAssetWithoutCachedPricesForOnDemandAnalysisButReportsPendingCoverage() {
        AssetMaster apple = asset("US_AAPL", "STOCK", "Apple Inc.", "AAPL", "NASDAQ", "USD");
        when(assetMasterRepository.searchActive(eq("apple"), eq("STOCK"), eq("US"), any(Pageable.class)))
                .thenReturn(List.of(apple));
        when(assetAliasRepository.searchActiveAssetMatches(eq("apple"), eq("STOCK"), eq("US"), any(Pageable.class)))
                .thenReturn(List.of());
        when(stockMasterRepository.searchForEtfAssetCandidates(eq("apple"), any()))
                .thenReturn(List.of());
        when(stockVisualAssetResolver.resolve("NASDAQ", "AAPL", "Apple Inc.", null)).thenReturn(visual("AAPL"));

        CustomEtfAssetSearchResponseDTO response = etfDataService.searchAssets("apple", "STOCK", "US", 0, 10);

        assertEquals(1, response.getItems().size());
        assertEquals("US_AAPL", response.getItems().get(0).getAssetId());
        assertEquals(true, response.getItems().get(0).getBacktestEnabled());
        assertEquals("PENDING_VERIFICATION", response.getItems().get(0).getDataStatus());
        assertEquals("PENDING", response.getItems().get(0).getPriceCoverage1Y().getStatus());
        assertEquals("PENDING", response.getItems().get(0).getPriceCoverage3Y().getStatus());
        assertEquals("PENDING", response.getItems().get(0).getPriceCoverage5Y().getStatus());
    }

    @Test
    void searchAssets_reportsVerifiedOnlyWhenCachedPricesCoverOneYear() {
        AssetMaster apple = asset("US_AAPL", "STOCK", "Apple Inc.", "AAPL", "NASDAQ", "USD");
        LocalDate now = LocalDate.now();
        when(assetMasterRepository.searchActive(eq("apple"), eq("STOCK"), eq("US"), any(Pageable.class)))
                .thenReturn(List.of(apple));
        when(assetAliasRepository.searchActiveAssetMatches(eq("apple"), eq("STOCK"), eq("US"), any(Pageable.class)))
                .thenReturn(List.of());
        when(stockMasterRepository.searchForEtfAssetCandidates(eq("apple"), any()))
                .thenReturn(List.of());
        when(assetPriceDailyRepository.findCoverageSummariesByAssetIds(List.of("US_AAPL")))
                .thenReturn(List.of(coverage("US_AAPL", now.minusMonths(13), now.minusDays(1), 243L)));
        when(stockVisualAssetResolver.resolve("NASDAQ", "AAPL", "Apple Inc.", null)).thenReturn(visual("AAPL"));

        CustomEtfAssetSearchResponseDTO response = etfDataService.searchAssets("apple", "STOCK", "US", 0, 10);

        assertEquals("VERIFIED", response.getItems().get(0).getDataStatus());
        assertNull(response.getItems().get(0).getDataStatusMessage());
        assertEquals("READY", response.getItems().get(0).getPriceCoverage1Y().getStatus());
        assertEquals("PARTIAL", response.getItems().get(0).getPriceCoverage3Y().getStatus());
        assertEquals("PARTIAL", response.getItems().get(0).getPriceCoverage5Y().getStatus());
    }

    @Test
    void searchAssets_marksKnownPriceUnavailableStockAsNotSelectable() {
        AssetMaster unsupported = asset("US_FAKE", "STOCK", "Fake Corp.", "FAKE", "NASDAQ", "USD");
        unsupported.setBacktestEnabled(false);
        unsupported.setPriceSourceStatus("PRICE_UNAVAILABLE");
        unsupported.setLastPriceError("No recent KIS price");
        when(assetMasterRepository.searchActive(eq("fake"), eq("STOCK"), eq(null), any(Pageable.class)))
                .thenReturn(List.of(unsupported));
        when(assetAliasRepository.searchActiveAssetMatches(eq("fake"), eq("STOCK"), eq(null), any(Pageable.class)))
                .thenReturn(List.of());
        when(stockMasterRepository.searchForEtfAssetCandidates(eq("fake"), any()))
                .thenReturn(List.of());
        when(stockVisualAssetResolver.resolve("NASDAQ", "FAKE", "Fake Corp.", null)).thenReturn(visual("FAKE"));
        when(historicalPriceProvider.getSecurityPriceSeriesForEligibility(eq("US_FAKE"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(point(LocalDate.now().minusYears(1).toString(), "100"), point(LocalDate.now().toString(), "110")));

        CustomEtfAssetSearchResponseDTO response = etfDataService.searchAssets("fake", "STOCK", null, 0, 10);

        assertEquals(1, response.getItems().size());
        assertEquals(1, response.getTotalCount());
        assertEquals("US_FAKE", response.getItems().get(0).getAssetId());
        assertEquals(false, response.getItems().get(0).getBacktestEnabled());
        assertEquals("PRICE_UNAVAILABLE", response.getItems().get(0).getDataStatus());
        assertEquals("No recent KIS price", response.getItems().get(0).getDataStatusMessage());
        assertEquals("UNAVAILABLE", response.getItems().get(0).getPriceCoverage1Y().getStatus());
    }

    @Test
    void searchAssets_usesYahooSearchFallbackForUsCompanyNameWhenMasterMisses() {
        when(assetMasterRepository.searchActive(eq("apple"), eq("STOCK"), eq("US"), any(Pageable.class)))
                .thenReturn(List.of());
        when(assetAliasRepository.searchActiveAssetMatches(eq("apple"), eq("STOCK"), eq("US"), any(Pageable.class)))
                .thenReturn(List.of());
        when(stockMasterRepository.searchForEtfAssetCandidates(eq("apple"), any()))
                .thenReturn(List.of());
        when(yahooAssetSearchClient.searchUsEquities("apple", 10)).thenReturn(List.of(
                new YahooAssetSearchClient.YahooAssetResult("AAPL", "Apple Inc.", "NASDAQ", "USD")
        ));
        when(stockVisualAssetResolver.resolve("NASDAQ", "AAPL", "Apple Inc.", null)).thenReturn(visual("AAPL"));

        CustomEtfAssetSearchResponseDTO response = etfDataService.searchAssets("apple", "STOCK", "US", 0, 10);

        assertEquals(1, response.getTotalCount());
        assertEquals("US_AAPL", response.getItems().get(0).getAssetId());
        assertEquals("Apple Inc.", response.getItems().get(0).getName());
        assertEquals("AAPL", response.getItems().get(0).getSymbol());
        assertEquals("NASDAQ", response.getItems().get(0).getMarket());
        assertNull(response.getItems().get(0).getLogoUrl());
        assertNotNull(response.getItems().get(0).getVisual());
    }

    @Test
    void searchAssets_usesYahooSearchFallbackForUsTickerWhenMasterMisses() {
        when(assetMasterRepository.searchActive(eq("iren"), eq("STOCK"), eq("US"), any(Pageable.class)))
                .thenReturn(List.of());
        when(assetAliasRepository.searchActiveAssetMatches(eq("iren"), eq("STOCK"), eq("US"), any(Pageable.class)))
                .thenReturn(List.of());
        when(stockMasterRepository.searchForEtfAssetCandidates(eq("iren"), any()))
                .thenReturn(List.of());
        when(yahooAssetSearchClient.searchUsEquities("iren", 10)).thenReturn(List.of(
                new YahooAssetSearchClient.YahooAssetResult("IREN", "IREN Limited", "NASDAQ", "USD")
        ));
        when(stockVisualAssetResolver.resolve("NASDAQ", "IREN", "IREN Limited", null)).thenReturn(visual("IREN"));

        CustomEtfAssetSearchResponseDTO response = etfDataService.searchAssets("iren", "STOCK", "US", 0, 10);

        assertEquals(1, response.getTotalCount());
        assertEquals("US_IREN", response.getItems().get(0).getAssetId());
        assertEquals("IREN Limited", response.getItems().get(0).getName());
        assertEquals("IREN", response.getItems().get(0).getSymbol());
        assertEquals("NASDAQ", response.getItems().get(0).getMarket());
    }

    @Test
    void searchAssets_mergesYahooSearchResultsWhenLocalCatalogHasPartialUsMatches() {
        AssetMaster local = asset("US_TECH", "STOCK", "Tech Local Inc.", "TECH", "NASDAQ", "USD");
        when(assetMasterRepository.searchActive(eq("tech"), eq("STOCK"), eq("US"), any(Pageable.class)))
                .thenReturn(List.of(local));
        when(assetAliasRepository.searchActiveAssetMatches(eq("tech"), eq("STOCK"), eq("US"), any(Pageable.class)))
                .thenReturn(List.of());
        when(stockMasterRepository.searchForEtfAssetCandidates(eq("tech"), any()))
                .thenReturn(List.of());
        when(yahooAssetSearchClient.searchUsEquities("tech", 10)).thenReturn(List.of(
                new YahooAssetSearchClient.YahooAssetResult("AAPL", "Apple Inc.", "NASDAQ", "USD")
        ));
        when(stockVisualAssetResolver.resolve("NASDAQ", "TECH", "Tech Local Inc.", null)).thenReturn(visual("TECH"));
        when(stockVisualAssetResolver.resolve("NASDAQ", "AAPL", "Apple Inc.", null)).thenReturn(visual("AAPL"));

        CustomEtfAssetSearchResponseDTO response = etfDataService.searchAssets("tech", "STOCK", "US", 0, 10);

        assertEquals(2, response.getTotalCount());
        assertEquals(List.of("US_TECH", "US_AAPL"),
                response.getItems().stream().map(item -> item.getAssetId()).toList());
    }

    @Test
    void searchAssets_allowsAddingOneHundredDistinctUsSearchResults() {
        List<AssetFlowCase> cases = broadAssetSearchCases();
        Set<String> uniqueAssetIds = new LinkedHashSet<>(cases.stream().map(AssetFlowCase::assetId).toList());
        assertEquals(100, cases.size());
        assertEquals(100, uniqueAssetIds.size());
        Map<String, List<YahooAssetSearchClient.YahooAssetResult>> yahooResultsByQuery = cases.stream()
                .collect(java.util.stream.Collectors.toMap(
                        AssetFlowCase::query,
                        testCase -> List.of(new YahooAssetSearchClient.YahooAssetResult(
                                testCase.symbol(),
                                testCase.name(),
                                testCase.market(),
                                "USD"
                        ))
                ));
        when(assetMasterRepository.searchActive(any(), any(), any(), any(Pageable.class))).thenReturn(List.of());
        when(assetAliasRepository.searchActiveAssetMatches(any(), any(), any(), any(Pageable.class))).thenReturn(List.of());
        when(stockMasterRepository.searchForEtfAssetCandidates(any(), any(Pageable.class))).thenReturn(List.of());
        when(assetMasterRepository.findByAssetIdAndActiveTrue(any())).thenReturn(Optional.empty());
        when(assetMasterRepository.findFirstBySymbolIgnoreCaseAndAssetTypeAndActiveTrue(any(), eq("STOCK")))
                .thenReturn(Optional.empty());
        when(yahooAssetSearchClient.searchUsEquities(any(), anyInt()))
                .thenAnswer(invocation -> yahooResultsByQuery.getOrDefault(invocation.getArgument(0), List.of()));
        when(stockVisualAssetResolver.resolve(any(), any(), any(), any()))
                .thenAnswer(invocation -> visual(invocation.getArgument(1)));

        for (AssetFlowCase testCase : cases) {
            CustomEtfAssetSearchResponseDTO search = etfDataService.searchAssets(
                    testCase.query(),
                    "ALL",
                    testCase.marketFilter(),
                    0,
                    10
            );
            var found = search.getItems().stream()
                    .filter(item -> testCase.assetId().equals(item.getAssetId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("검색 결과 누락: " + testCase));

            assertEquals(true, found.getBacktestEnabled(), "주식형 fallback 결과는 온디맨드 분석 시도 가능이어야 함: " + testCase);
            assertEquals("PENDING_VERIFICATION", found.getDataStatus(), "검증 전 데이터 상태 누락: " + testCase);
            assertNull(found.getLogoUrl(), "fallback 로고 URL이 생성되면 안 됨: " + testCase);
            assertNotNull(found.getVisual(), "fallback visual 누락: " + testCase);
        }
    }

    @Test
    void searchAssets_includesPendingAssetMasterStockForRealPriceVerification() {
        AssetMaster pending = asset("KRX_373220", "STOCK", "LG에너지솔루션", "373220", "KOSPI", "KRW");
        pending.setBacktestEnabled(false);
        pending.setPriceSourceStatus("PENDING_VERIFICATION");
        pending.setLastPriceError("Price data has not been verified");
        when(assetMasterRepository.searchActive(eq("LG"), eq("STOCK"), eq(null), any(Pageable.class)))
                .thenReturn(List.of(pending));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("KRX_373220"))
                .thenReturn(Optional.of(pending));
        when(assetAliasRepository.searchActiveAssetMatches(eq("LG"), eq("STOCK"), eq(null), any(Pageable.class)))
                .thenReturn(List.of());
        when(stockMasterRepository.searchForEtfAssetCandidates(eq("LG"), any()))
                .thenReturn(List.of());
        when(stockVisualAssetResolver.resolve("KOSPI", "373220", "LG에너지솔루션", null)).thenReturn(visual("LG"));

        CustomEtfAssetSearchResponseDTO response = etfDataService.searchAssets("LG", "STOCK", null, 0, 10);

        assertEquals(1, response.getItems().size());
        assertEquals("KRX_373220", response.getItems().get(0).getAssetId());
        assertEquals(true, response.getItems().get(0).getBacktestEnabled());
        assertEquals("PENDING_VERIFICATION", response.getItems().get(0).getDataStatus());
        assertEquals("분석 시점에 실가격을 확인하며, 가격 데이터가 부족하면 분석이 제한됩니다.",
                response.getItems().get(0).getDataStatusMessage());
        assertEquals(false, pending.getBacktestEnabled());
        assertEquals("PENDING_VERIFICATION", pending.getPriceSourceStatus());
        verify(assetMasterRepository, never()).save(pending);
    }

    @Test
    void searchAssets_includesYahooSymbolSearchResultPendingRealPriceVerification() {
        when(assetMasterRepository.searchActive(eq("new co"), eq("STOCK"), eq("US"), any(Pageable.class)))
                .thenReturn(List.of());
        when(assetAliasRepository.searchActiveAssetMatches(eq("new co"), eq("STOCK"), eq("US"), any(Pageable.class)))
                .thenReturn(List.of());
        when(stockMasterRepository.searchForEtfAssetCandidates(eq("new co"), any()))
                .thenReturn(List.of());
        when(yahooAssetSearchClient.searchUsEquities("new co", 10)).thenReturn(List.of(
                new YahooAssetSearchClient.YahooAssetResult("NEWC", "NewCo Inc.", "NASDAQ", "USD")
        ));
        when(stockVisualAssetResolver.resolve("NASDAQ", "NEWC", "NewCo Inc.", null)).thenReturn(visual("NEWC"));
        when(historicalPriceProvider.getSecurityPriceSeriesForEligibility(eq("US_NEWC"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(point(LocalDate.now().minusMonths(6).toString(), "100"), point(LocalDate.now().toString(), "105")));

        CustomEtfAssetSearchResponseDTO response = etfDataService.searchAssets("new co", "STOCK", "US", 0, 10);

        assertEquals(1, response.getItems().size());
        assertEquals(1, response.getTotalCount());
        assertEquals("US_NEWC", response.getItems().get(0).getAssetId());
        assertEquals(true, response.getItems().get(0).getBacktestEnabled());
        assertEquals("PENDING_VERIFICATION", response.getItems().get(0).getDataStatus());
        verify(historicalPriceProvider, never())
                .getSecurityPriceSeriesForEligibility(eq("US_NEWC"), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void createCustomEtf_rejectsUnknownAssetId() {
        User user = User.builder().id(1L).build();
        CustomEtfCreateRequestDTO request = CustomEtfCreateRequestDTO.builder()
                .title("검증 ETF")
                .items(List.of(CustomEtfItemRequestDTO.builder()
                        .stockId("NOT_REAL_ASSET")
                        .weight(100)
                        .build()))
                .build();
        when(assetMasterRepository.findByAssetIdAndActiveTrue("NOT_REAL_ASSET"))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> etfDataService.createCustomEtf(user, request));

        assertEquals("Unknown ETF assetId: NOT_REAL_ASSET", ex.getMessage());
    }

    @Test
    void createCustomEtf_acceptsPendingStockAssetBeforeBacktestVerification() {
        User user = User.builder().id(1L).build();
        AssetMaster pending = asset("KRX_373220", "STOCK", "LG에너지솔루션", "373220", "KOSPI", "KRW");
        pending.setBacktestEnabled(false);
        pending.setPriceSourceStatus("PENDING_VERIFICATION");
        pending.setLastPriceError("Price data has not been verified");
        CustomEtfCreateRequestDTO request = CustomEtfCreateRequestDTO.builder()
                .title("기본 ETF")
                .items(List.of(CustomEtfItemRequestDTO.builder()
                        .stockId("KRX_373220")
                        .weight(100)
                        .build()))
                .build();
        when(assetMasterRepository.findByAssetIdAndActiveTrue("KRX_373220"))
                .thenReturn(Optional.of(pending));
        when(managedEtfRepository.save(any(ManagedEtf.class))).thenAnswer(invocation -> {
            ManagedEtf saved = invocation.getArgument(0);
            saved.setCreatedAt(java.time.LocalDateTime.parse("2026-05-10T09:00:00"));
            saved.setUpdatedAt(java.time.LocalDateTime.parse("2026-05-10T09:00:00"));
            return saved;
        });

        CustomEtfMutationResponseDTO response = etfDataService.createCustomEtf(user, request);

        assertEquals("기본 ETF", response.getTitle());
        assertEquals(100, response.getTotalWeight());
        assertEquals("2026-05-10T09:00Z", response.getCreatedAt());
        assertEquals("2026-05-10T09:00Z", response.getUpdatedAt());
    }

    @Test
    void createCustomEtf_acceptsPriceUnavailableStockForFallbackAnalysis() {
        User user = User.builder().id(1L).build();
        AssetMaster unsupported = asset("US_FAKE", "STOCK", "Fake Corp.", "FAKE", "NASDAQ", "USD");
        unsupported.setBacktestEnabled(false);
        unsupported.setPriceSourceStatus("PRICE_UNAVAILABLE");
        unsupported.setLastPriceError("No recent KIS price");
        CustomEtfCreateRequestDTO request = CustomEtfCreateRequestDTO.builder()
                .title("검증 ETF")
                .items(List.of(CustomEtfItemRequestDTO.builder()
                        .stockId("US_FAKE")
                        .weight(100)
                        .build()))
                .build();
        when(assetMasterRepository.findByAssetIdAndActiveTrue("US_FAKE"))
                .thenReturn(Optional.of(unsupported));
        when(managedEtfRepository.save(any(ManagedEtf.class))).thenAnswer(invocation -> {
            ManagedEtf saved = invocation.getArgument(0);
            saved.setCreatedAt(java.time.LocalDateTime.parse("2026-05-10T09:00:00"));
            saved.setUpdatedAt(java.time.LocalDateTime.parse("2026-05-10T09:00:00"));
            return saved;
        });

        CustomEtfMutationResponseDTO response = etfDataService.createCustomEtf(user, request);

        assertEquals("검증 ETF", response.getTitle());
        assertEquals(100, response.getTotalWeight());
    }

    @Test
    void createCustomEtf_rejectsBondOrCashAsset() {
        User user = User.builder().id(1L).build();
        AssetMaster bond = asset("BOND_KR_GOV_3Y", "BOND", "국고채 3년", "BOND_KR_GOV_3Y", "BOND", "KRW");
        CustomEtfCreateRequestDTO request = CustomEtfCreateRequestDTO.builder()
                .title("주식 전용 ETF")
                .items(List.of(CustomEtfItemRequestDTO.builder()
                        .stockId("BOND_KR_GOV_3Y")
                        .weight(100)
                        .build()))
                .build();
        when(assetMasterRepository.findByAssetIdAndActiveTrue("BOND_KR_GOV_3Y"))
                .thenReturn(Optional.of(bond));

        ApiException ex = assertThrows(ApiException.class, () -> etfDataService.createCustomEtf(user, request));

        assertEquals("Custom ETF only supports stock and ETF-like assets: BOND_KR_GOV_3Y", ex.getMessage());
    }

    @Test
    void analyze_acceptsPriceUnavailableStockWithFallbackProvider() {
        User user = User.builder().id(1L).build();
        ManagedEtf etf = ManagedEtf.builder()
                .etfCode("ETF_CUSTOM")
                .ownerUserId(1L)
                .sourceType("CUSTOM")
                .title("분석 ETF")
                .holdingsJson("[{\"stockId\":\"US_FAKE\",\"weight\":100}]")
                .build();
        AssetMaster unsupported = asset("US_FAKE", "STOCK", "Fake Corp.", "FAKE", "NASDAQ", "USD");
        unsupported.setBacktestEnabled(false);
        unsupported.setPriceSourceStatus("PRICE_UNAVAILABLE");
        unsupported.setLastPriceError("No recent KIS price");
        when(managedEtfRepository.findByEtfCode("ETF_CUSTOM")).thenReturn(Optional.of(etf));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("US_FAKE")).thenReturn(Optional.of(unsupported));
        when(historicalPriceProvider.getSecurityPriceSeries(eq("US_FAKE"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(point("2025-01-02", "100"), point("2025-01-03", "101")));
        when(historicalPriceProvider.getBenchmarkSeries(eq("SP500"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(point("2025-01-02", "100"), point("2025-01-03", "101")));
        when(assetPriceDailyRepository.findCoverageSummaryByAssetId("US_FAKE"))
                .thenReturn(Optional.of(coverage("US_FAKE", LocalDate.now().minusMonths(13), LocalDate.now().minusDays(1), 243L)));
        BacktestResult result = backtestResult();
        when(etfBacktestEngine.run(any())).thenReturn(result);
        InsightFacts facts = InsightFacts.builder().positiveFacts(List.of()).riskFacts(List.of()).build();
        when(etfAiFeedbackService.buildInsightFacts(eq("분석 ETF"), eq("1년"), eq("S&P 500"), eq(result), any(), any())).thenReturn(facts);
        when(etfAiFeedbackService.buildFeedback(facts)).thenReturn(new RuleBasedFeedback(
                "AI 리스크 진단",
                "백테스트 기준 요약입니다.",
                List.of(),
                "BALANCED",
                "과거 데이터 기반 백테스트이며 미래 수익을 보장하지 않습니다.",
                true
        ));

        EtfAnalysisStartResponseDTO response = etfDataService.analyze(user, "ETF_CUSTOM",
                EtfAnalysisRequestDTO.builder().period("1Y").benchmark("SP500").build());

        assertEquals("COMPLETED", response.getStatus());
        assertEquals(true, unsupported.getBacktestEnabled());
        assertEquals("VERIFIED", unsupported.getPriceSourceStatus());
        assertEquals(null, unsupported.getLastPriceError());
        org.mockito.Mockito.verify(assetMasterRepository).save(unsupported);
    }

    @Test
    void analyze_verifiesPendingStockAssetOnDemandBeforeBacktest() {
        User user = User.builder().id(1L).build();
        ManagedEtf etf = ManagedEtf.builder()
                .etfCode("ETF_CUSTOM")
                .ownerUserId(1L)
                .sourceType("CUSTOM")
                .title("기본 ETF")
                .theme("테크")
                .holdingsJson("[{\"stockId\":\"KRX_373220\",\"weight\":100}]")
                .build();
        AssetMaster pending = asset("KRX_373220", "STOCK", "LG에너지솔루션", "373220", "KOSPI", "KRW");
        pending.setBacktestEnabled(false);
        pending.setPriceSourceStatus("PENDING_VERIFICATION");
        pending.setLastPriceError("Price data has not been verified");
        when(managedEtfRepository.findByEtfCode("ETF_CUSTOM")).thenReturn(Optional.of(etf));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("KRX_373220")).thenReturn(Optional.of(pending));
        when(historicalPriceProvider.getSecurityPriceSeries(eq("KRX_373220"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(point("2025-01-02", "100"), point("2025-01-03", "101")));
        when(historicalPriceProvider.getBenchmarkSeries(eq("SP500"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(point("2025-01-02", "100"), point("2025-01-03", "101")));
        when(assetPriceDailyRepository.findCoverageSummaryByAssetId("KRX_373220"))
                .thenReturn(Optional.of(coverage("KRX_373220", LocalDate.now().minusMonths(13), LocalDate.now().minusDays(1), 243L)));
        BacktestResult result = backtestResult();
        when(etfBacktestEngine.run(any())).thenReturn(result);
        InsightFacts facts = InsightFacts.builder().positiveFacts(List.of()).riskFacts(List.of()).build();
        when(etfAiFeedbackService.buildInsightFacts(eq("기본 ETF"), eq("1년"), eq("S&P 500"), eq(result), any(), any())).thenReturn(facts);
        when(etfAiFeedbackService.buildFeedback(facts)).thenReturn(new RuleBasedFeedback(
                "AI 리스크 진단",
                "백테스트 기준 요약입니다.",
                List.of(),
                "BALANCED",
                "과거 데이터 기반 백테스트이며 미래 수익을 보장하지 않습니다.",
                true
        ));

        EtfAnalysisStartResponseDTO response = etfDataService.analyze(user, "ETF_CUSTOM",
                EtfAnalysisRequestDTO.builder().period("1Y").benchmark("SP500").build());

        assertEquals("ETF_CUSTOM", response.getEtfId());
        assertEquals("COMPLETED", response.getStatus());
        assertEquals(true, pending.getBacktestEnabled());
        assertEquals("VERIFIED", pending.getPriceSourceStatus());
        assertEquals(null, pending.getLastPriceError());
        org.mockito.Mockito.verify(historicalPriceProvider, org.mockito.Mockito.times(1))
                .getSecurityPriceSeries(eq("KRX_373220"), any(LocalDate.class), any(LocalDate.class));
        org.mockito.Mockito.verify(assetMasterRepository).save(pending);
    }

    @Test
    void analyze_acceptsQuarterlyRebalanceAndWritesBacktestAssumptions() throws Exception {
        User user = User.builder().id(1L).build();
        ManagedEtf etf = ManagedEtf.builder()
                .etfCode("ETF_CUSTOM")
                .ownerUserId(1L)
                .sourceType("CUSTOM")
                .title("분석 ETF")
                .theme("테크")
                .holdingsJson("[{\"stockId\":\"US_AAPL\",\"weight\":100}]")
                .build();
        AssetMaster apple = asset("US_AAPL", "STOCK", "Apple Inc.", "AAPL", "NASDAQ", "USD");
        when(managedEtfRepository.findByEtfCode("ETF_CUSTOM")).thenReturn(Optional.of(etf));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("US_AAPL")).thenReturn(Optional.of(apple));
        when(historicalPriceProvider.getSecurityPriceSeries(eq("US_AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(point("2025-01-02", "100"), point("2025-01-03", "101")));
        when(historicalPriceProvider.getBenchmarkSeries(eq("SP500"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(point("2025-01-02", "100"), point("2025-01-03", "101")));
        BacktestResult result = backtestResult();
        when(etfBacktestEngine.run(any())).thenReturn(result);
        InsightFacts facts = InsightFacts.builder()
                .positiveFacts(List.of("분산이 양호합니다."))
                .riskFacts(List.of("과거 데이터 한계가 있습니다."))
                .build();
        when(etfAiFeedbackService.buildInsightFacts(eq("분석 ETF"), eq("1년"), eq("S&P 500"), eq(result), any(), any())).thenReturn(facts);
        when(etfAiFeedbackService.buildFeedback(facts)).thenReturn(new RuleBasedFeedback(
                "AI 리스크 진단",
                "백테스트 기준 요약입니다.",
                List.of(),
                "BALANCED",
                "과거 데이터 기반 백테스트이며 미래 수익을 보장하지 않습니다.",
                true
        ));
        ArgumentCaptor<ManagedEtfAnalysisReport> captor = ArgumentCaptor.forClass(ManagedEtfAnalysisReport.class);

        etfDataService.analyze(user, "ETF_CUSTOM", EtfAnalysisRequestDTO.builder()
                .period("1Y")
                .benchmark("SP500")
                .rebalancePolicy("QUARTERLY")
                .build());

        org.mockito.Mockito.verify(managedEtfAnalysisReportRepository).save(captor.capture());
        EtfAnalysisReportResponseDTO report = new ObjectMapper()
                .readValue(captor.getValue().getReportJson(), EtfAnalysisReportResponseDTO.class);
        assertEquals("QUARTERLY", report.getMetadata().getRebalancePolicy());
        assertEquals("asset_price_daily", report.getMetadata().getPriceCachePolicy());
        assertEquals("Yahoo Finance chart API, KIS chart API, or cached real prices", report.getMetadata().getPriceSource());
        assertEquals("fx_rate_daily", report.getMetadata().getFxCachePolicy());
        assertEquals(true, report.getMetadata().getAssumptions().stream()
                .anyMatch(value -> value.contains("정수 주식 수") && value.contains("현금")));
        assertEquals(true, report.getMetadata().getLimitations().stream()
                .anyMatch(value -> value.contains("dividend") && value.contains("intramonth")));
    }

    @Test
    void analyze_usesSelectedNasdaqBenchmarkForBacktestAndSavedReport() throws Exception {
        User user = User.builder().id(1L).build();
        ManagedEtf etf = ManagedEtf.builder()
                .etfCode("ETF_CUSTOM")
                .ownerUserId(1L)
                .sourceType("CUSTOM")
                .title("분석 ETF")
                .theme("테크")
                .holdingsJson("[{\"stockId\":\"US_AAPL\",\"weight\":100}]")
                .build();
        AssetMaster apple = asset("US_AAPL", "STOCK", "Apple Inc.", "AAPL", "NASDAQ", "USD");
        List<BacktestPricePoint> nasdaqSeries = List.of(
                point("2025-01-02", "100"),
                point("2025-01-03", "108")
        );
        when(managedEtfRepository.findByEtfCode("ETF_CUSTOM")).thenReturn(Optional.of(etf));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("US_AAPL")).thenReturn(Optional.of(apple));
        when(historicalPriceProvider.getSecurityPriceSeries(eq("US_AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(point("2025-01-02", "100"), point("2025-01-03", "112")));
        when(historicalPriceProvider.getBenchmarkSeries(eq("NASDAQ"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(nasdaqSeries);
        BacktestResult result = backtestResult();
        when(etfBacktestEngine.run(any())).thenReturn(result);
        InsightFacts facts = InsightFacts.builder().positiveFacts(List.of()).riskFacts(List.of()).build();
        when(etfAiFeedbackService.buildInsightFacts(eq("분석 ETF"), eq("1년"), eq("NASDAQ"), eq(result), any(), any()))
                .thenReturn(facts);
        when(etfAiFeedbackService.buildFeedback(facts)).thenReturn(new RuleBasedFeedback(
                "AI 리스크 진단",
                "NASDAQ 기준 백테스트 요약입니다.",
                List.of(),
                "BALANCED",
                "과거 데이터 기반 백테스트이며 미래 수익을 보장하지 않습니다.",
                true
        ));
        ArgumentCaptor<BacktestRequest> backtestRequestCaptor = ArgumentCaptor.forClass(BacktestRequest.class);
        ArgumentCaptor<ManagedEtfAnalysisReport> reportCaptor = ArgumentCaptor.forClass(ManagedEtfAnalysisReport.class);

        etfDataService.analyze(user, "ETF_CUSTOM",
                EtfAnalysisRequestDTO.builder().period("1Y").benchmark("NASDAQ").build());

        verify(historicalPriceProvider).getBenchmarkSeries(eq("NASDAQ"), any(LocalDate.class), any(LocalDate.class));
        verify(etfBacktestEngine).run(backtestRequestCaptor.capture());
        BacktestRequest backtestRequest = backtestRequestCaptor.getValue();
        assertEquals("NASDAQ", backtestRequest.getBenchmarkName());
        assertEquals(nasdaqSeries, backtestRequest.getBenchmarkSeries());
        verify(managedEtfAnalysisReportRepository).save(reportCaptor.capture());
        EtfAnalysisReportResponseDTO report = new ObjectMapper()
                .readValue(reportCaptor.getValue().getReportJson(), EtfAnalysisReportResponseDTO.class);
        assertEquals("NASDAQ", report.getBenchmark());
        assertEquals(7.0, report.getHighlights().getBenchmarkReturn());
        assertEquals(3.0, report.getHighlights().getBenchmarkExcessReturn());
    }

    @Test
    void analyze_passesNewsExposureIntoAiInsightFacts() {
        User user = User.builder().id(1L).build();
        ManagedEtf etf = ManagedEtf.builder()
                .etfCode("ETF_CUSTOM")
                .ownerUserId(1L)
                .sourceType("CUSTOM")
                .title("분석 ETF")
                .theme("테크")
                .holdingsJson("[{\"stockId\":\"US_TSLA\",\"weight\":60},{\"stockId\":\"KRX_005930\",\"weight\":40}]")
                .build();
        AssetMaster tesla = asset("US_TSLA", "STOCK", "Tesla", "TSLA", "NASDAQ", "USD");
        AssetMaster samsung = asset("KRX_005930", "STOCK", "삼성전자", "005930", "KOSPI", "KRW");
        when(managedEtfRepository.findByEtfCode("ETF_CUSTOM")).thenReturn(Optional.of(etf));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("US_TSLA")).thenReturn(Optional.of(tesla));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("KRX_005930")).thenReturn(Optional.of(samsung));
        when(historicalPriceProvider.getSecurityPriceSeries(any(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(point("2025-01-02", "100"), point("2025-01-03", "101")));
        when(historicalPriceProvider.getBenchmarkSeries(eq("SP500"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(point("2025-01-02", "100"), point("2025-01-03", "101")));
        BacktestResult result = backtestResult();
        when(etfBacktestEngine.run(any())).thenReturn(result);
        EtfNewsExposure exposure = new EtfNewsExposure(
                BigDecimal.valueOf(40.0).setScale(1),
                BigDecimal.valueOf(60.0).setScale(1),
                2,
                List.of(),
                List.of(),
                List.of("Tesla 60.0%에 악재 뉴스가 우세해요.")
        );
        when(etfNewsExposureService.summarize(any())).thenReturn(exposure);
        InsightFacts facts = InsightFacts.builder().positiveFacts(List.of()).riskFacts(List.of()).build();
        when(etfAiFeedbackService.buildInsightFacts(eq("분석 ETF"), eq("1년"), eq("S&P 500"), eq(result), any(), eq(exposure))).thenReturn(facts);
        when(etfAiFeedbackService.buildFeedback(facts)).thenReturn(new RuleBasedFeedback(
                "AI 리스크 진단",
                "뉴스 흐름을 반영한 백테스트 요약입니다.",
                List.of(),
                "CAUTION",
                "과거 데이터 기반 백테스트이며 미래 수익을 보장하지 않습니다.",
                true
        ));

        EtfAnalysisStartResponseDTO response = etfDataService.analyze(user, "ETF_CUSTOM",
                EtfAnalysisRequestDTO.builder().period("1Y").benchmark("SP500").build());

        assertEquals("COMPLETED", response.getStatus());
        org.mockito.Mockito.verify(etfNewsExposureService).summarize(any());
        org.mockito.Mockito.verify(etfAiFeedbackService)
                .buildInsightFacts(eq("분석 ETF"), eq("1년"), eq("S&P 500"), eq(result), any(), eq(exposure));
    }

    @Test
    void analyze_fetchesHoldingAndBenchmarkPricesConcurrently() {
        User user = User.builder().id(1L).build();
        ManagedEtf etf = ManagedEtf.builder()
                .etfCode("ETF_CUSTOM")
                .ownerUserId(1L)
                .sourceType("CUSTOM")
                .title("분석 ETF")
                .theme("테크")
                .holdingsJson("""
                        [{"stockId":"KRX_373220","weight":40},{"stockId":"KRX_005930","weight":35},{"stockId":"KRX_000660","weight":25}]
                        """)
                .build();
        AssetMaster lgEnergy = asset("KRX_373220", "STOCK", "LG에너지솔루션", "373220", "KOSPI", "KRW");
        AssetMaster samsung = asset("KRX_005930", "STOCK", "삼성전자", "005930", "KOSPI", "KRW");
        AssetMaster hynix = asset("KRX_000660", "STOCK", "SK하이닉스", "000660", "KOSPI", "KRW");
        when(managedEtfRepository.findByEtfCode("ETF_CUSTOM")).thenReturn(Optional.of(etf));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("KRX_373220")).thenReturn(Optional.of(lgEnergy));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("KRX_005930")).thenReturn(Optional.of(samsung));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("KRX_000660")).thenReturn(Optional.of(hynix));

        CyclicBarrier barrier = new CyclicBarrier(2);
        when(historicalPriceProvider.getSecurityPriceSeries(any(), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(invocation -> {
                    awaitConcurrentPriceFetch(barrier);
                    return List.of(point("2025-01-02", "100"), point("2025-01-03", "101"));
                });
        when(historicalPriceProvider.getBenchmarkSeries(eq("SP500"), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(invocation -> {
                    awaitConcurrentPriceFetch(barrier);
                    return List.of(point("2025-01-02", "100"), point("2025-01-03", "101"));
                });
        BacktestResult result = backtestResult();
        when(etfBacktestEngine.run(any())).thenReturn(result);
        InsightFacts facts = InsightFacts.builder().positiveFacts(List.of()).riskFacts(List.of()).build();
        when(etfAiFeedbackService.buildInsightFacts(eq("분석 ETF"), eq("1년"), eq("S&P 500"), eq(result), any(), any())).thenReturn(facts);
        when(etfAiFeedbackService.buildFeedback(facts)).thenReturn(new RuleBasedFeedback(
                "AI 리스크 진단",
                "백테스트 기준 요약입니다.",
                List.of(),
                "BALANCED",
                "과거 데이터 기반 백테스트이며 미래 수익을 보장하지 않습니다.",
                true
        ));

        EtfAnalysisStartResponseDTO response = etfDataService.analyze(user, "ETF_CUSTOM",
                EtfAnalysisRequestDTO.builder().period("1Y").benchmark("SP500").build());

        assertEquals("COMPLETED", response.getStatus());
    }

    @Test
    void analyze_defaultsMissingRequestFieldsToOneYearAndSp500() {
        User user = User.builder().id(1L).build();
        ManagedEtf etf = ManagedEtf.builder()
                .etfCode("ETF_CUSTOM")
                .ownerUserId(1L)
                .sourceType("CUSTOM")
                .title("분석 ETF")
                .theme("테크")
                .holdingsJson("[{\"stockId\":\"US_AAPL\",\"weight\":100}]")
                .build();
        AssetMaster apple = asset("US_AAPL", "STOCK", "Apple Inc.", "AAPL", "NASDAQ", "USD");
        when(managedEtfRepository.findByEtfCode("ETF_CUSTOM")).thenReturn(Optional.of(etf));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("US_AAPL")).thenReturn(Optional.of(apple));
        when(historicalPriceProvider.getSecurityPriceSeries(eq("US_AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(point("2025-01-02", "100"), point("2025-01-03", "101")));
        when(historicalPriceProvider.getBenchmarkSeries(eq("SP500"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(point("2025-01-02", "100"), point("2025-01-03", "101")));
        BacktestResult result = backtestResult();
        when(etfBacktestEngine.run(any())).thenReturn(result);
        InsightFacts facts = InsightFacts.builder().positiveFacts(List.of()).riskFacts(List.of()).build();
        when(etfAiFeedbackService.buildInsightFacts(eq("분석 ETF"), eq("1년"), eq("S&P 500"), eq(result), any(), any())).thenReturn(facts);
        when(etfAiFeedbackService.buildFeedback(facts)).thenReturn(new RuleBasedFeedback(
                "AI 리스크 진단",
                "백테스트 기준 요약입니다.",
                List.of(),
                "BALANCED",
                "과거 데이터 기반 백테스트이며 미래 수익을 보장하지 않습니다.",
                true
        ));

        etfDataService.analyze(user, "ETF_CUSTOM", EtfAnalysisRequestDTO.builder().build());

        org.mockito.Mockito.verify(historicalPriceProvider)
                .getBenchmarkSeries(eq("SP500"), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void analyze_downgradesFiveYearRequestToThreeYearsWhenOnlyThreeYearsAreAvailable() throws Exception {
        User user = User.builder().id(1L).build();
        ManagedEtf etf = ManagedEtf.builder()
                .etfCode("ETF_CUSTOM")
                .ownerUserId(1L)
                .sourceType("CUSTOM")
                .title("분석 ETF")
                .theme("테크")
                .holdingsJson("[{\"stockId\":\"US_AAPL\",\"weight\":100}]")
                .build();
        AssetMaster apple = asset("US_AAPL", "STOCK", "Apple Inc.", "AAPL", "NASDAQ", "USD");
        LocalDate endDate = LocalDate.now();
        LocalDate threeYearStart = endDate.minusYears(3).plusDays(3);
        when(managedEtfRepository.findByEtfCode("ETF_CUSTOM")).thenReturn(Optional.of(etf));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("US_AAPL")).thenReturn(Optional.of(apple));
        when(historicalPriceProvider.getSecurityPriceSeries(eq("US_AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        point(threeYearStart.toString(), "100"),
                        point(endDate.minusDays(1).toString(), "120")
                ));
        when(historicalPriceProvider.getBenchmarkSeries(eq("SP500"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        point(threeYearStart.toString(), "100"),
                        point(endDate.minusDays(1).toString(), "115")
                ));
        BacktestResult result = backtestResult();
        when(etfBacktestEngine.run(any())).thenReturn(result);
        InsightFacts facts = InsightFacts.builder().positiveFacts(List.of()).riskFacts(List.of()).build();
        when(etfAiFeedbackService.buildInsightFacts(eq("분석 ETF"), eq("3년"), eq("S&P 500"), eq(result), any(), any()))
                .thenReturn(facts);
        when(etfAiFeedbackService.buildFeedback(facts)).thenReturn(new RuleBasedFeedback(
                "AI 리스크 진단",
                "3년 기준 백테스트 요약입니다.",
                List.of(),
                "BALANCED",
                "과거 데이터 기반 백테스트이며 미래 수익을 보장하지 않습니다.",
                true
        ));
        ArgumentCaptor<BacktestRequest> backtestRequestCaptor = ArgumentCaptor.forClass(BacktestRequest.class);
        ArgumentCaptor<ManagedEtfAnalysisReport> reportCaptor = ArgumentCaptor.forClass(ManagedEtfAnalysisReport.class);

        etfDataService.analyze(user, "ETF_CUSTOM",
                EtfAnalysisRequestDTO.builder().period("5Y").benchmark("SP500").build());

        verify(etfBacktestEngine).run(backtestRequestCaptor.capture());
        assertEquals("3년", backtestRequestCaptor.getValue().getPeriodLabel());
        verify(managedEtfAnalysisReportRepository).save(reportCaptor.capture());
        EtfAnalysisReportResponseDTO report = new ObjectMapper()
                .readValue(reportCaptor.getValue().getReportJson(), EtfAnalysisReportResponseDTO.class);
        assertEquals("3Y", report.getPeriod());
        assertEquals("5Y", report.getMetadata().getRequestedPeriod());
        assertEquals("3Y", report.getMetadata().getActualPeriod());
        assertEquals(true, report.getMetadata().getPeriodDowngraded());
        assertEquals(1, report.getMetadata().getDataWarnings().size());
    }

    @Test
    void analyze_downgradesThreeYearRequestToOneYearWhenOnlyOneYearIsAvailable() throws Exception {
        User user = User.builder().id(1L).build();
        ManagedEtf etf = ManagedEtf.builder()
                .etfCode("ETF_CUSTOM")
                .ownerUserId(1L)
                .sourceType("CUSTOM")
                .title("분석 ETF")
                .theme("테크")
                .holdingsJson("[{\"stockId\":\"US_AAPL\",\"weight\":100}]")
                .build();
        AssetMaster apple = asset("US_AAPL", "STOCK", "Apple Inc.", "AAPL", "NASDAQ", "USD");
        LocalDate endDate = LocalDate.now();
        LocalDate oneYearStart = endDate.minusYears(1).plusDays(2);
        when(managedEtfRepository.findByEtfCode("ETF_CUSTOM")).thenReturn(Optional.of(etf));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("US_AAPL")).thenReturn(Optional.of(apple));
        when(historicalPriceProvider.getSecurityPriceSeries(eq("US_AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        point(oneYearStart.toString(), "100"),
                        point(endDate.minusDays(1).toString(), "112")
                ));
        when(historicalPriceProvider.getBenchmarkSeries(eq("SP500"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        point(oneYearStart.toString(), "100"),
                        point(endDate.minusDays(1).toString(), "108")
                ));
        BacktestResult result = backtestResult();
        when(etfBacktestEngine.run(any())).thenReturn(result);
        InsightFacts facts = InsightFacts.builder().positiveFacts(List.of()).riskFacts(List.of()).build();
        when(etfAiFeedbackService.buildInsightFacts(eq("분석 ETF"), eq("1년"), eq("S&P 500"), eq(result), any(), any()))
                .thenReturn(facts);
        when(etfAiFeedbackService.buildFeedback(facts)).thenReturn(new RuleBasedFeedback(
                "AI 리스크 진단",
                "1년 기준 백테스트 요약입니다.",
                List.of(),
                "BALANCED",
                "과거 데이터 기반 백테스트이며 미래 수익을 보장하지 않습니다.",
                true
        ));
        ArgumentCaptor<BacktestRequest> backtestRequestCaptor = ArgumentCaptor.forClass(BacktestRequest.class);
        ArgumentCaptor<ManagedEtfAnalysisReport> reportCaptor = ArgumentCaptor.forClass(ManagedEtfAnalysisReport.class);

        etfDataService.analyze(user, "ETF_CUSTOM",
                EtfAnalysisRequestDTO.builder().period("3Y").benchmark("SP500").build());

        verify(etfBacktestEngine).run(backtestRequestCaptor.capture());
        assertEquals("1년", backtestRequestCaptor.getValue().getPeriodLabel());
        verify(managedEtfAnalysisReportRepository).save(reportCaptor.capture());
        EtfAnalysisReportResponseDTO report = new ObjectMapper()
                .readValue(reportCaptor.getValue().getReportJson(), EtfAnalysisReportResponseDTO.class);
        assertEquals("1Y", report.getPeriod());
        assertEquals("3Y", report.getMetadata().getRequestedPeriod());
        assertEquals("1Y", report.getMetadata().getActualPeriod());
        assertEquals(true, report.getMetadata().getPeriodDowngraded());
    }

    @Test
    void analyze_rejectsWhenRequestedMultiYearPeriodHasLessThanOneYearCoverage() {
        User user = User.builder().id(1L).build();
        ManagedEtf etf = ManagedEtf.builder()
                .etfCode("ETF_CUSTOM")
                .ownerUserId(1L)
                .sourceType("CUSTOM")
                .title("분석 ETF")
                .theme("테크")
                .holdingsJson("[{\"stockId\":\"US_AAPL\",\"weight\":100}]")
                .build();
        AssetMaster apple = asset("US_AAPL", "STOCK", "Apple Inc.", "AAPL", "NASDAQ", "USD");
        LocalDate endDate = LocalDate.now();
        when(managedEtfRepository.findByEtfCode("ETF_CUSTOM")).thenReturn(Optional.of(etf));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("US_AAPL")).thenReturn(Optional.of(apple));
        when(historicalPriceProvider.getSecurityPriceSeries(eq("US_AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        point(endDate.minusMonths(4).toString(), "100"),
                        point(endDate.minusDays(1).toString(), "104")
                ));
        when(historicalPriceProvider.getBenchmarkSeries(eq("SP500"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        point(endDate.minusMonths(4).toString(), "100"),
                        point(endDate.minusDays(1).toString(), "102")
                ));

        ApiException ex = assertThrows(ApiException.class, () -> etfDataService.analyze(user, "ETF_CUSTOM",
                EtfAnalysisRequestDTO.builder().period("3Y").benchmark("SP500").build()));

        assertEquals(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("ETF_PRICE_DATA_UNAVAILABLE", ex.getErrorCode());
    }

    @Test
    void analyze_rejectsWhenHoldingPriceDataIsUnavailable() {
        User user = User.builder().id(1L).build();
        ManagedEtf etf = ManagedEtf.builder()
                .etfCode("ETF_CUSTOM")
                .ownerUserId(1L)
                .sourceType("CUSTOM")
                .title("분석 ETF")
                .theme("테크")
                .holdingsJson("[{\"stockId\":\"US_AAPL\",\"weight\":100}]")
                .build();
        AssetMaster apple = asset("US_AAPL", "STOCK", "Apple Inc.", "AAPL", "NASDAQ", "USD");
        when(managedEtfRepository.findByEtfCode("ETF_CUSTOM")).thenReturn(Optional.of(etf));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("US_AAPL")).thenReturn(Optional.of(apple));
        when(historicalPriceProvider.getSecurityPriceSeries(eq("US_AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(historicalPriceProvider.getBenchmarkSeries(eq("SP500"), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(this::fullCoverageSeries);

        ApiException ex = assertThrows(ApiException.class, () -> etfDataService.analyze(user, "ETF_CUSTOM",
                EtfAnalysisRequestDTO.builder().period("1Y").benchmark("SP500").build()));

        assertEquals(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("ETF_PRICE_DATA_UNAVAILABLE", ex.getErrorCode());
        assertEquals(false, apple.getBacktestEnabled());
        assertEquals("PRICE_UNAVAILABLE", apple.getPriceSourceStatus());
        assertEquals("ETF asset has insufficient price data for backtest: US_AAPL", apple.getLastPriceError());
        org.mockito.Mockito.verify(assetMasterRepository).save(apple);
    }

    @Test
    void analyze_rejectsWhenBenchmarkPriceDataIsUnavailable() {
        User user = User.builder().id(1L).build();
        ManagedEtf etf = ManagedEtf.builder()
                .etfCode("ETF_CUSTOM")
                .ownerUserId(1L)
                .sourceType("CUSTOM")
                .title("분석 ETF")
                .theme("테크")
                .holdingsJson("[{\"stockId\":\"US_AAPL\",\"weight\":100}]")
                .build();
        AssetMaster apple = asset("US_AAPL", "STOCK", "Apple Inc.", "AAPL", "NASDAQ", "USD");
        when(managedEtfRepository.findByEtfCode("ETF_CUSTOM")).thenReturn(Optional.of(etf));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("US_AAPL")).thenReturn(Optional.of(apple));
        when(historicalPriceProvider.getSecurityPriceSeries(eq("US_AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(this::fullCoverageSeries);
        when(historicalPriceProvider.getBenchmarkSeries(eq("SP500"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        ApiException ex = assertThrows(ApiException.class, () -> etfDataService.analyze(user, "ETF_CUSTOM",
                EtfAnalysisRequestDTO.builder().period("1Y").benchmark("SP500").build()));

        assertEquals(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("ETF_BENCHMARK_PRICE_DATA_UNAVAILABLE", ex.getErrorCode());
    }

    @Test
    void analyze_keepsSelectedSp500BenchmarkForDomesticPortfolio() throws Exception {
        User user = User.builder().id(1L).build();
        ManagedEtf etf = ManagedEtf.builder()
                .etfCode("ETF_CUSTOM")
                .ownerUserId(1L)
                .sourceType("CUSTOM")
                .title("국내 기본 ETF")
                .theme("국내 주식")
                .holdingsJson("""
                        [{"stockId":"KRX_005930","weight":40},{"stockId":"KRX_000660","weight":35},{"stockId":"KRX_373220","weight":25}]
                        """)
                .build();
        AssetMaster samsung = asset("KRX_005930", "STOCK", "삼성전자", "005930", "KOSPI", "KRW");
        AssetMaster hynix = asset("KRX_000660", "STOCK", "SK하이닉스", "000660", "KOSPI", "KRW");
        AssetMaster lgEnergy = asset("KRX_373220", "STOCK", "LG에너지솔루션", "373220", "KOSPI", "KRW");
        when(managedEtfRepository.findByEtfCode("ETF_CUSTOM")).thenReturn(Optional.of(etf));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("KRX_005930")).thenReturn(Optional.of(samsung));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("KRX_000660")).thenReturn(Optional.of(hynix));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("KRX_373220")).thenReturn(Optional.of(lgEnergy));
        when(historicalPriceProvider.getSecurityPriceSeries(any(), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(this::fullCoverageSeries);
        when(historicalPriceProvider.getBenchmarkSeries(eq("SP500"), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(this::fullCoverageSeries);
        BacktestResult result = backtestResult();
        when(etfBacktestEngine.run(any())).thenReturn(result);
        InsightFacts facts = InsightFacts.builder().positiveFacts(List.of()).riskFacts(List.of()).build();
        when(etfAiFeedbackService.buildInsightFacts(eq("국내 기본 ETF"), eq("1년"), eq("S&P 500"), eq(result), any(), any()))
                .thenReturn(facts);
        when(etfAiFeedbackService.buildFeedback(facts)).thenReturn(new RuleBasedFeedback(
                "AI 리스크 진단",
                "S&P 500 기준 백테스트 요약입니다.",
                List.of(),
                "BALANCED",
                "과거 데이터 기반 백테스트이며 미래 수익을 보장하지 않습니다.",
                true
        ));
        ArgumentCaptor<BacktestRequest> backtestRequestCaptor = ArgumentCaptor.forClass(BacktestRequest.class);
        ArgumentCaptor<ManagedEtfAnalysisReport> reportCaptor = ArgumentCaptor.forClass(ManagedEtfAnalysisReport.class);

        etfDataService.analyze(user, "ETF_CUSTOM",
                EtfAnalysisRequestDTO.builder().period("1Y").benchmark("SP500").build());

        verify(historicalPriceProvider).getBenchmarkSeries(eq("SP500"), any(LocalDate.class), any(LocalDate.class));
        verify(historicalPriceProvider, never()).getBenchmarkSeries(eq("KOSPI"), any(LocalDate.class), any(LocalDate.class));
        verify(etfBacktestEngine).run(backtestRequestCaptor.capture());
        assertEquals("S&P 500", backtestRequestCaptor.getValue().getBenchmarkName());
        verify(managedEtfAnalysisReportRepository).save(reportCaptor.capture());
        EtfAnalysisReportResponseDTO report = new ObjectMapper()
                .readValue(reportCaptor.getValue().getReportJson(), EtfAnalysisReportResponseDTO.class);
        assertEquals("SP500", reportCaptor.getValue().getBenchmark());
        assertEquals("SP500", report.getBenchmark());
    }

    @Test
    void recommendPortfolioFitStocks_excludesCurrentHoldingsAndRanksByPortfolioFit() {
        User user = User.builder().id(1L).build();
        ManagedEtf etf = ManagedEtf.builder()
                .etfCode("ETF_CUSTOM")
                .ownerUserId(1L)
                .sourceType("CUSTOM")
                .title("AI 반도체 ETF")
                .theme("반도체")
                .holdingsJson("[{\"stockId\":\"KRX_005930\",\"weight\":60},{\"stockId\":\"KRX_000660\",\"weight\":40}]")
                .build();
        AssetMaster samsung = asset("KRX_005930", "STOCK", "삼성전자", "005930", "KOSPI", "KRW");
        AssetMaster hynix = asset("KRX_000660", "STOCK", "SK하이닉스", "000660", "KOSPI", "KRW");
        AssetMaster naver = asset("KRX_035420", "STOCK", "NAVER", "035420", "KOSPI", "KRW");
        AssetMaster tesla = asset("US_TSLA", "STOCK", "Tesla", "TSLA", "NASDAQ", "USD");
        when(managedEtfRepository.findByEtfCode("ETF_CUSTOM")).thenReturn(Optional.of(etf));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("KRX_005930")).thenReturn(Optional.of(samsung));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("KRX_000660")).thenReturn(Optional.of(hynix));
        when(assetMasterRepository.searchActive(eq(""), eq("STOCK"), eq(null), any(Pageable.class)))
                .thenReturn(List.of(tesla, samsung, naver, hynix));
        when(stockVisualAssetResolver.resolve("KOSPI", "035420", "NAVER", null)).thenReturn(visual("NAVER"));
        when(stockVisualAssetResolver.resolve("NASDAQ", "TSLA", "Tesla", null)).thenReturn(visual("TSLA"));

        EtfPortfolioFitRecommendationResponseDTO response = etfDataService.recommendPortfolioFitStocks(
                user,
                EtfPortfolioFitRecommendationRequestDTO.builder()
                        .customEtfId("ETF_CUSTOM")
                        .limit(3)
                        .market("ALL")
                        .build()
        );

        assertEquals(List.of("KRX_035420", "US_TSLA"),
                response.getItems().stream().map(item -> item.getStockId()).toList());
        assertEquals("FIT_KRX_035420", response.getItems().get(0).getRecommendationId());
        assertEquals("NAVER", response.getItems().get(0).getName());
        assertEquals("035420", response.getItems().get(0).getSymbol());
        assertEquals(true, response.getItems().get(0).getFitScore() > response.getItems().get(1).getFitScore());
        assertEquals(true, response.getItems().get(0).getReason().contains("포트폴리오"));
        assertEquals(true, response.getItems().get(0).getReason().contains("NAVER"));
        assertEquals(true, response.getItems().get(1).getReason().contains("Tesla"));
        assertEquals(true, response.getItems().get(0).getTags().size() <= 3);
        assertEquals(true, response.getItems().stream().noneMatch(item -> item.getStockId().equals("KRX_005930")));
        assertEquals(true, response.getItems().stream().noneMatch(item -> item.getStockId().equals("KRX_000660")));
    }

    @Test
    void recommendPortfolioFitStocks_prioritizesThemeFitOverSameMarketOnly() {
        User user = User.builder().id(1L).build();
        ManagedEtf etf = ManagedEtf.builder()
                .etfCode("ETF_PLATFORM")
                .ownerUserId(1L)
                .sourceType("CUSTOM")
                .title("플랫폼 ETF")
                .theme("플랫폼")
                .holdingsJson("[{\"stockId\":\"KRX_035420\",\"weight\":55},{\"stockId\":\"KRX_035720\",\"weight\":45}]")
                .build();
        AssetMaster naver = asset("KRX_035420", "STOCK", "NAVER", "035420", "KOSPI", "KRW");
        AssetMaster kakao = asset("KRX_035720", "STOCK", "카카오", "035720", "KOSPI", "KRW");
        AssetMaster hanmi = asset("KRX_042700", "STOCK", "한미반도체", "042700", "KOSPI", "KRW");
        AssetMaster google = asset("US_GOOGL", "STOCK", "Google", "GOOGL", "NASDAQ", "USD");
        AssetMaster meta = asset("US_META", "STOCK", "Meta", "META", "NASDAQ", "USD");
        when(managedEtfRepository.findByEtfCode("ETF_PLATFORM")).thenReturn(Optional.of(etf));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("KRX_035420")).thenReturn(Optional.of(naver));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("KRX_035720")).thenReturn(Optional.of(kakao));
        when(assetMasterRepository.searchActive(eq(""), eq("STOCK"), eq(null), any(Pageable.class)))
                .thenReturn(List.of(hanmi, google, meta, naver, kakao));
        when(stockVisualAssetResolver.resolve("KOSPI", "042700", "한미반도체", null)).thenReturn(visual("042700"));
        when(stockVisualAssetResolver.resolve("NASDAQ", "GOOGL", "Google", null)).thenReturn(visual("GOOGL"));
        when(stockVisualAssetResolver.resolve("NASDAQ", "META", "Meta", null)).thenReturn(visual("META"));

        EtfPortfolioFitRecommendationResponseDTO response = etfDataService.recommendPortfolioFitStocks(
                user,
                EtfPortfolioFitRecommendationRequestDTO.builder()
                        .customEtfId("ETF_PLATFORM")
                        .limit(2)
                        .market("ALL")
                        .build()
        );

        assertEquals(List.of("US_GOOGL", "US_META"),
                response.getItems().stream().map(item -> item.getStockId()).sorted().toList());
        assertEquals(true, response.getItems().get(0).getFitScore() > response.getItems().get(1).getFitScore());
        assertEquals(true, response.getItems().get(0).getReason().contains("플랫폼"));
        assertEquals(false, response.getItems().get(0).getFitScore().equals(response.getItems().get(1).getFitScore()));
    }

    @Test
    void recommendPortfolioFitStocks_usesBertFitModelScoreWhenAvailable() {
        User user = User.builder().id(1L).build();
        ManagedEtf etf = ManagedEtf.builder()
                .etfCode("ETF_PLATFORM_BERT")
                .ownerUserId(1L)
                .sourceType("CUSTOM")
                .title("플랫폼 ETF")
                .theme("플랫폼")
                .holdingsJson("[{\"stockId\":\"KRX_035420\",\"weight\":100}]")
                .build();
        AssetMaster naver = asset("KRX_035420", "STOCK", "NAVER", "035420", "KOSPI", "KRW");
        AssetMaster hanmi = asset("KRX_042700", "STOCK", "한미반도체", "042700", "KOSPI", "KRW");
        AssetMaster google = asset("US_GOOGL", "STOCK", "Google", "GOOGL", "NASDAQ", "USD");
        when(managedEtfRepository.findByEtfCode("ETF_PLATFORM_BERT")).thenReturn(Optional.of(etf));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("KRX_035420")).thenReturn(Optional.of(naver));
        when(assetMasterRepository.searchActive(eq(""), eq("STOCK"), eq(null), any(Pageable.class)))
                .thenReturn(List.of(hanmi, google, naver));
        when(stockVisualAssetResolver.resolve("KOSPI", "042700", "한미반도체", null)).thenReturn(visual("042700"));
        when(stockVisualAssetResolver.resolve("NASDAQ", "GOOGL", "Google", null)).thenReturn(visual("GOOGL"));
        when(portfolioFitModelClient.score(any())).thenAnswer(invocation -> {
            PortfolioFitModelInput input = invocation.getArgument(0);
            if ("Google".equals(input.candidateName())) {
                return Optional.of(new PortfolioFitModelScore(true, 0.94, "FinBERT positive"));
            }
            return Optional.of(new PortfolioFitModelScore(false, 0.94, "FinBERT negative"));
        });

        EtfPortfolioFitRecommendationResponseDTO response = etfDataService.recommendPortfolioFitStocks(
                user,
                EtfPortfolioFitRecommendationRequestDTO.builder()
                        .customEtfId("ETF_PLATFORM_BERT")
                        .limit(2)
                        .market("ALL")
                        .build()
        );

        assertEquals("US_GOOGL", response.getItems().get(0).getStockId());
        assertTrue(response.getItems().get(0).getTags().contains("적합 신호"));
        assertTrue(response.getItems().get(0).getReason().contains("역할:"));
        assertTrue(response.getItems().get(0).getReason().contains("주의:"));
        assertTrue(response.getItems().get(0).getReason().contains("확인:"));
        assertTrue(response.getItems().get(0).getReason().contains("현재 포트폴리오와 방향성이 맞고"));
        assertTrue(!response.getItems().get(0).getReason().contains("BERT"));
    }

    @Test
    void recommendPortfolioFitStocks_diversifiesSimilarThemeCandidates() {
        User user = User.builder().id(1L).build();
        ManagedEtf etf = ManagedEtf.builder()
                .etfCode("ETF_AI_SEMICON")
                .ownerUserId(1L)
                .sourceType("CUSTOM")
                .title("AI 반도체 ETF")
                .theme("반도체")
                .holdingsJson("[{\"stockId\":\"KRX_005930\",\"weight\":60},{\"stockId\":\"KRX_000660\",\"weight\":40}]")
                .build();
        AssetMaster samsung = asset("KRX_005930", "STOCK", "삼성전자", "005930", "KOSPI", "KRW");
        AssetMaster hynix = asset("KRX_000660", "STOCK", "SK하이닉스", "000660", "KOSPI", "KRW");
        AssetMaster hanmi = asset("KRX_042700", "STOCK", "한미반도체", "042700", "KOSPI", "KRW");
        AssetMaster nvidia = asset("US_NVDA", "STOCK", "NVIDIA Corp.", "NVDA", "NASDAQ", "USD");
        AssetMaster amd = asset("US_AMD", "STOCK", "Advanced Micro Devices", "AMD", "NASDAQ", "USD");
        AssetMaster intel = asset("US_INTC", "STOCK", "Intel Corp.", "INTC", "NASDAQ", "USD");
        AssetMaster naver = asset("KRX_035420", "STOCK", "NAVER", "035420", "KOSPI", "KRW");
        AssetMaster jpmorgan = asset("US_JPM", "STOCK", "JPMorgan Chase", "JPM", "NYSE", "USD");
        when(managedEtfRepository.findByEtfCode("ETF_AI_SEMICON")).thenReturn(Optional.of(etf));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("KRX_005930")).thenReturn(Optional.of(samsung));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("KRX_000660")).thenReturn(Optional.of(hynix));
        when(assetMasterRepository.searchActive(eq(""), eq("STOCK"), eq(null), any(Pageable.class)))
                .thenReturn(List.of(hanmi, nvidia, amd, intel, naver, jpmorgan, samsung, hynix));
        when(stockVisualAssetResolver.resolve(any(), any(), any(), any())).thenReturn(visual("ETF"));

        EtfPortfolioFitRecommendationResponseDTO response = etfDataService.recommendPortfolioFitStocks(
                user,
                EtfPortfolioFitRecommendationRequestDTO.builder()
                        .customEtfId("ETF_AI_SEMICON")
                        .limit(4)
                        .market("ALL")
                        .build()
        );

        long semiconductorLinkedCount = response.getItems().stream()
                .filter(item -> item.getTags().contains("반도체 연계"))
                .count();
        var firstThemeCandidate = response.getItems().stream()
                .filter(item -> item.getTags().contains("반도체 연계"))
                .findFirst()
                .orElseThrow();
        var naverCandidate = response.getItems().stream()
                .filter(item -> item.getStockId().equals("KRX_035420"))
                .findFirst()
                .orElseThrow();
        assertEquals(4, response.getItems().size());
        assertTrue(semiconductorLinkedCount <= 2);
        assertTrue(response.getItems().stream().anyMatch(item -> item.getStockId().equals("KRX_035420")));
        assertTrue(firstThemeCandidate.getTags().contains("추가 검토"));
        assertTrue(firstThemeCandidate.getReason().contains("추가 검토 가능"));
        assertTrue(response.getItems().stream()
                .filter(item -> item.getStockId().equals("KRX_035420"))
                .findFirst()
                .orElseThrow()
                .getTags()
                .contains("섹터 분산"));
        assertTrue(naverCandidate.getTags().contains("분산용 검토"));
        assertTrue(naverCandidate.getReason().contains("분산 목적이라면 검토"));
        assertTrue(response.getItems().stream().noneMatch(item -> item.getStockId().equals("KRX_005930")));
        assertTrue(response.getItems().stream().noneMatch(item -> item.getStockId().equals("KRX_000660")));
    }

    @Test
    void analyze_usesRequestedPeriodForBacktestWindow() {
        User user = User.builder().id(1L).build();
        ManagedEtf etf = ManagedEtf.builder()
                .etfCode("ETF_CUSTOM")
                .ownerUserId(1L)
                .sourceType("CUSTOM")
                .title("분석 ETF")
                .theme("테크")
                .holdingsJson("[{\"stockId\":\"US_AAPL\",\"weight\":100}]")
                .build();
        AssetMaster apple = asset("US_AAPL", "STOCK", "Apple Inc.", "AAPL", "NASDAQ", "USD");
        when(managedEtfRepository.findByEtfCode("ETF_CUSTOM")).thenReturn(Optional.of(etf));
        when(assetMasterRepository.findByAssetIdAndActiveTrue("US_AAPL")).thenReturn(Optional.of(apple));
        when(historicalPriceProvider.getSecurityPriceSeries(eq("US_AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(point("2021-01-02", "100"), point("2026-01-03", "150")));
        when(historicalPriceProvider.getBenchmarkSeries(eq("SP500"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(point("2021-01-02", "100"), point("2026-01-03", "150")));
        BacktestResult result = backtestResult();
        when(etfBacktestEngine.run(any())).thenReturn(result);
        InsightFacts facts = InsightFacts.builder().positiveFacts(List.of()).riskFacts(List.of()).build();
        when(etfAiFeedbackService.buildInsightFacts(eq("분석 ETF"), eq("5년"), eq("S&P 500"), eq(result), any(), any())).thenReturn(facts);
        when(etfAiFeedbackService.buildFeedback(facts)).thenReturn(new RuleBasedFeedback(
                "AI 리스크 진단",
                "백테스트 기준 요약입니다.",
                List.of(),
                "BALANCED",
                "과거 데이터 기반 백테스트이며 미래 수익을 보장하지 않습니다.",
                true
        ));
        ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);

        etfDataService.analyze(user, "ETF_CUSTOM",
                EtfAnalysisRequestDTO.builder().period("5Y").benchmark("SP500").build());

        org.mockito.Mockito.verify(historicalPriceProvider)
                .getSecurityPriceSeries(eq("US_AAPL"), startCaptor.capture(), endCaptor.capture());
        assertEquals(LocalDate.now().minusYears(5), startCaptor.getValue());
        assertEquals(LocalDate.now(), endCaptor.getValue());
    }

    private StockMaster stock(String code, String name, String market) {
        return StockMaster.builder()
                .code(code)
                .nameKr(name)
                .market(market)
                .build();
    }

    private AssetMaster asset(String assetId, String assetType, String name, String symbol, String market, String currency) {
        return AssetMaster.builder()
                .assetId(assetId)
                .assetType(assetType)
                .name(name)
                .symbol(symbol)
                .market(market)
                .currency(currency)
                .active(true)
                .backtestEnabled(true)
                .priceSourceStatus("VERIFIED")
                .build();
    }

    private void awaitConcurrentPriceFetch(CyclicBarrier barrier) {
        try {
            barrier.await(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("price fetches should overlap", e);
        }
    }

    private ManagedEtf discoveryEtf(String etfCode,
                                    String title,
                                    String theme,
                                    String riskLevel,
                                    String returnRate,
                                    int favoriteCount,
                                    String analysisSummaryJson,
                                    String holdingsJson) {
        ManagedEtf etf = ManagedEtf.builder()
                .etfCode(etfCode)
                .sourceType("DISCOVERY")
                .title(title)
                .theme(theme)
                .riskLevel(riskLevel)
                .returnRate(new BigDecimal(returnRate))
                .favoriteCount(favoriteCount)
                .analysisSummaryJson(analysisSummaryJson)
                .holdingsJson(holdingsJson)
                .publishedAt(java.time.LocalDateTime.parse("2026-05-10T09:00:00"))
                .build();
        etf.setCreatedAt(java.time.LocalDateTime.parse("2026-05-10T09:00:00"));
        etf.setUpdatedAt(java.time.LocalDateTime.parse("2026-05-10T09:00:00"));
        return etf;
    }

    private BacktestPricePoint point(String date, String adjustedCloseKrw) {
        return new BacktestPricePoint(LocalDate.parse(date), new BigDecimal(adjustedCloseKrw));
    }

    private AssetPriceDailyRepository.AssetPriceCoverageSummary coverage(String assetId,
                                                                         LocalDate firstTradeDate,
                                                                         LocalDate lastTradeDate,
                                                                         long priceCount) {
        return new AssetPriceDailyRepository.AssetPriceCoverageSummary() {
            @Override
            public String getAssetId() {
                return assetId;
            }

            @Override
            public LocalDate getFirstTradeDate() {
                return firstTradeDate;
            }

            @Override
            public LocalDate getLastTradeDate() {
                return lastTradeDate;
            }

            @Override
            public Long getPriceCount() {
                return priceCount;
            }
        };
    }

    private List<BacktestPricePoint> fullCoverageSeries(org.mockito.invocation.InvocationOnMock invocation) {
        LocalDate startDate = invocation.getArgument(1);
        LocalDate endDate = invocation.getArgument(2);
        return List.of(
                point(startDate.plusDays(1).toString(), "100"),
                point(endDate.minusDays(1).toString(), "120")
        );
    }

    private BacktestResult backtestResult() {
        return new BacktestResult(
                BigDecimal.valueOf(100_000_000L),
                BigDecimal.valueOf(110_000_000L),
                BigDecimal.valueOf(10_000_000L),
                BigDecimal.valueOf(10.0),
                BigDecimal.valueOf(10.0),
                BigDecimal.valueOf(12.0),
                BigDecimal.valueOf(-8.0),
                BigDecimal.valueOf(7.0),
                BigDecimal.valueOf(3.0),
                BigDecimal.valueOf(0.82),
                BigDecimal.valueOf(1.0),
                "Apple Inc.",
                BigDecimal.valueOf(100.0),
                BigDecimal.valueOf(100.0),
                "테크",
                BigDecimal.valueOf(100.0),
                45,
                "MEDIUM",
                "보통",
                2,
                List.of(
                        new com.uniport.service.backtest.BacktestNavPoint(LocalDate.parse("2025-01-02"), BigDecimal.valueOf(100_000_000L)),
                        new com.uniport.service.backtest.BacktestNavPoint(LocalDate.parse("2025-01-03"), BigDecimal.valueOf(110_000_000L))
                )
        );
    }

    private List<AssetFlowCase> broadAssetSearchCases() {
        List<String> symbols = List.of(
                "AAPL", "MSFT", "NVDA", "TSLA", "AMZN", "GOOGL", "META", "NFLX", "AMD", "INTC",
                "AVGO", "QCOM", "ORCL", "CRM", "ADBE", "NOW", "SNOW", "PLTR", "IBM", "CSCO",
                "JPM", "BAC", "GS", "MS", "C", "WFC", "V", "MA", "PYPL", "SQ",
                "KO", "PEP", "MCD", "SBUX", "NKE", "DIS", "WMT", "COST", "TGT", "HD",
                "UNH", "JNJ", "PFE", "MRK", "ABBV", "LLY", "TMO", "ABT", "ISRG", "GILD",
                "XOM", "CVX", "COP", "SLB", "ENPH", "FSLR", "NEE", "GE", "CAT", "BA",
                "LMT", "RTX", "DE", "MMM", "SPY", "QQQ", "DIA", "IWM", "VTI", "VXUS",
                "SOXX", "SMH", "XLK", "XLF", "XLE", "XLV", "XLY", "XLP", "XLI", "XLU",
                "ARKK", "ARKG", "ARKW", "TQQQ", "SQQQ", "GLD", "SLV", "USO", "VNQ", "HYG",
                "LQD", "TLT", "SHY", "IEI", "BND", "AGG", "SCHD", "VOO", "IVV", "IREN"
        );
        List<AssetFlowCase> cases = new ArrayList<>();
        for (int i = 0; i < symbols.size(); i++) {
            String symbol = symbols.get(i);
            String market = switch (i % 3) {
                case 0 -> "NASDAQ";
                case 1 -> "NYSE";
                default -> "AMEX";
            };
            String marketFilter = switch (i % 5) {
                case 0 -> "US";
                case 1 -> "ALL";
                case 2 -> market;
                case 3 -> "";
                default -> "US";
            };
            String query = switch (i % 4) {
                case 0 -> symbol.toLowerCase(Locale.ROOT);
                case 1 -> symbol;
                case 2 -> "search " + symbol.toLowerCase(Locale.ROOT);
                default -> "asset " + symbol.toLowerCase(Locale.ROOT);
            };
            String suffix = i >= 64 ? " ETF" : " Inc.";
            cases.add(new AssetFlowCase(query, marketFilter, "US_" + symbol, symbol, "Verified " + symbol + suffix, market));
        }
        return cases;
    }

    private StockVisualDTO visual(String text) {
        return StockVisualDTO.builder()
                .type("FALLBACK_SYMBOL")
                .text(text)
                .bgColor("#EEF2FF")
                .textColor("#4F46E5")
                .build();
    }

    private record AssetFlowCase(String query,
                                 String marketFilter,
                                 String assetId,
                                 String symbol,
                                 String name,
                                 String market) {
    }
}
