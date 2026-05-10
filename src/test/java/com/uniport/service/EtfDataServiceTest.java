package com.uniport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.dto.CustomEtfDetailResponseDTO;
import com.uniport.dto.CustomEtfAssetSearchResponseDTO;
import com.uniport.dto.CustomEtfCreateRequestDTO;
import com.uniport.dto.CustomEtfItemRequestDTO;
import com.uniport.dto.CustomEtfMutationResponseDTO;
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
import com.uniport.repository.ManagedEtfAnalysisReportRepository;
import com.uniport.repository.ManagedEtfFavoriteRepository;
import com.uniport.repository.ManagedEtfRepository;
import com.uniport.repository.StockMasterRepository;
import com.uniport.service.backtest.BacktestPricePoint;
import com.uniport.service.backtest.BacktestResult;
import com.uniport.service.backtest.EtfAiFeedbackService;
import com.uniport.service.backtest.EtfBacktestEngine;
import com.uniport.service.backtest.HistoricalPriceProvider;
import com.uniport.service.backtest.InsightFacts;
import com.uniport.service.backtest.RuleBasedFeedback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EtfDataServiceTest {

    private ManagedEtfRepository managedEtfRepository;
    private ManagedEtfAnalysisReportRepository managedEtfAnalysisReportRepository;
    private ManagedEtfFavoriteRepository managedEtfFavoriteRepository;
    private StockMasterRepository stockMasterRepository;
    private AssetMasterRepository assetMasterRepository;
    private AssetAliasRepository assetAliasRepository;
    private HistoricalPriceProvider historicalPriceProvider;
    private EtfBacktestEngine etfBacktestEngine;
    private EtfAiFeedbackService etfAiFeedbackService;
    private StockVisualAssetResolver stockVisualAssetResolver;
    private EtfDataService etfDataService;

    @BeforeEach
    void setUp() {
        managedEtfRepository = mock(ManagedEtfRepository.class);
        managedEtfAnalysisReportRepository = mock(ManagedEtfAnalysisReportRepository.class);
        managedEtfFavoriteRepository = mock(ManagedEtfFavoriteRepository.class);
        stockMasterRepository = mock(StockMasterRepository.class);
        assetMasterRepository = mock(AssetMasterRepository.class);
        assetAliasRepository = mock(AssetAliasRepository.class);
        historicalPriceProvider = mock(HistoricalPriceProvider.class);
        etfBacktestEngine = mock(EtfBacktestEngine.class);
        etfAiFeedbackService = mock(EtfAiFeedbackService.class);
        stockVisualAssetResolver = mock(StockVisualAssetResolver.class);
        etfDataService = new EtfDataService(
                managedEtfRepository,
                managedEtfAnalysisReportRepository,
                managedEtfFavoriteRepository,
                stockMasterRepository,
                assetMasterRepository,
                assetAliasRepository,
                historicalPriceProvider,
                etfBacktestEngine,
                etfAiFeedbackService,
                stockVisualAssetResolver
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

        assertEquals("assetType must be STOCK", bond.getMessage());
        assertEquals("assetType must be STOCK", cash.getMessage());
    }

    @Test
    void searchAssets_includesDomesticStockWithoutVerifiedAssetMasterForOnDemandVerification() {
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
        assertEquals(null, response.getItems().get(0).getDataStatusMessage());
        assertEquals(1, response.getTotalCount());
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
    void searchAssets_filtersUnsupportedAssetsAndExposesDataStatus() {
        AssetMaster apple = asset("US_AAPL", "STOCK", "Apple Inc.", "AAPL", "NASDAQ", "USD");
        AssetMaster unsupported = asset("US_FAKE", "STOCK", "Fake Corp.", "FAKE", "NASDAQ", "USD");
        unsupported.setBacktestEnabled(false);
        unsupported.setPriceSourceStatus("PRICE_UNAVAILABLE");
        unsupported.setLastPriceError("No recent KIS price");
        when(assetMasterRepository.searchActive(eq("apple"), eq("STOCK"), eq(null), any(Pageable.class)))
                .thenReturn(List.of(unsupported, apple));
        when(assetAliasRepository.searchActiveAssetMatches(eq("apple"), eq("STOCK"), eq(null), any(Pageable.class)))
                .thenReturn(List.of());
        when(stockMasterRepository.searchForEtfAssetCandidates(eq("apple"), any()))
                .thenReturn(List.of());
        when(stockVisualAssetResolver.resolve("NASDAQ", "AAPL", "Apple Inc.", null)).thenReturn(visual("AAPL"));

        CustomEtfAssetSearchResponseDTO response = etfDataService.searchAssets("apple", "STOCK", null, 0, 10);

        assertEquals(1, response.getItems().size());
        assertEquals("US_AAPL", response.getItems().get(0).getAssetId());
        assertEquals(true, response.getItems().get(0).getBacktestEnabled());
        assertEquals("VERIFIED", response.getItems().get(0).getDataStatus());
        assertEquals(null, response.getItems().get(0).getDataStatusMessage());
    }

    @Test
    void searchAssets_includesPendingAssetMasterStockForOnDemandVerification() {
        AssetMaster pending = asset("KRX_373220", "STOCK", "LG에너지솔루션", "373220", "KOSPI", "KRW");
        pending.setBacktestEnabled(false);
        pending.setPriceSourceStatus("PENDING_VERIFICATION");
        pending.setLastPriceError("Price data has not been verified");
        when(assetMasterRepository.searchActive(eq("LG"), eq("STOCK"), eq(null), any(Pageable.class)))
                .thenReturn(List.of(pending));
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
        assertEquals(null, response.getItems().get(0).getDataStatusMessage());
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
    void createCustomEtf_rejectsUnsupportedAssetMasterAsset() {
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

        ApiException ex = assertThrows(ApiException.class, () -> etfDataService.createCustomEtf(user, request));

        assertEquals("ETF asset is not backtest-enabled: US_FAKE (No recent KIS price)", ex.getMessage());
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

        assertEquals("Custom ETF only supports STOCK assets: BOND_KR_GOV_3Y", ex.getMessage());
    }

    @Test
    void analyze_rejectsUnsupportedStoredHoldingBeforeBacktest() {
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

        ApiException ex = assertThrows(ApiException.class, () -> etfDataService.analyze(user, "ETF_CUSTOM",
                EtfAnalysisRequestDTO.builder().period("1Y").benchmark("SP500").build()));

        assertEquals("ETF asset is not backtest-enabled: US_FAKE (No recent KIS price)", ex.getMessage());
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
        BacktestResult result = backtestResult();
        when(etfBacktestEngine.run(any())).thenReturn(result);
        InsightFacts facts = InsightFacts.builder().positiveFacts(List.of()).riskFacts(List.of()).build();
        when(etfAiFeedbackService.buildInsightFacts(eq("기본 ETF"), eq("1년"), eq("S&P 500"), eq(result))).thenReturn(facts);
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
        when(etfAiFeedbackService.buildInsightFacts(eq("분석 ETF"), eq("1년"), eq("S&P 500"), eq(result))).thenReturn(facts);
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
        assertEquals("fx_rate_daily", report.getMetadata().getFxCachePolicy());
        assertEquals(true, report.getMetadata().getAssumptions().stream()
                .anyMatch(value -> value.contains("transaction fee") && value.contains("slippage")));
        assertEquals(true, report.getMetadata().getLimitations().stream()
                .anyMatch(value -> value.contains("dividend") && value.contains("split")));
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
        when(etfAiFeedbackService.buildInsightFacts(eq("분석 ETF"), eq("1년"), eq("S&P 500"), eq(result))).thenReturn(facts);
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

    private StockVisualDTO visual(String text) {
        return StockVisualDTO.builder()
                .type("FALLBACK_SYMBOL")
                .text(text)
                .bgColor("#EEF2FF")
                .textColor("#4F46E5")
                .build();
    }
}
