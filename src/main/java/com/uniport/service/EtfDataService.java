package com.uniport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.dto.CustomEtfAssetSearchItemDTO;
import com.uniport.dto.CustomEtfAssetSearchResponseDTO;
import com.uniport.dto.CustomEtfCreateRequestDTO;
import com.uniport.dto.CustomEtfDetailResponseDTO;
import com.uniport.dto.CustomEtfHoldingDTO;
import com.uniport.dto.CustomEtfItemRequestDTO;
import com.uniport.dto.CustomEtfListResponseDTO;
import com.uniport.dto.CustomEtfMutationResponseDTO;
import com.uniport.dto.CustomEtfPriceCoverageDTO;
import com.uniport.dto.CustomEtfSummaryDTO;
import com.uniport.dto.CustomEtfUpdateRequestDTO;
import com.uniport.dto.EtfAnalysisAiFeedbackDTO;
import com.uniport.dto.EtfAnalysisAllocationDTO;
import com.uniport.dto.EtfAnalysisAllocationItemDTO;
import com.uniport.dto.EtfAnalysisApplyRequestDTO;
import com.uniport.dto.EtfAnalysisApplyResponseDTO;
import com.uniport.dto.EtfAnalysisBacktestMetadataDTO;
import com.uniport.dto.EtfAnalysisCumulativeProfitDTO;
import com.uniport.dto.EtfAnalysisFeedbackBulletDTO;
import com.uniport.dto.EtfAnalysisHighlightsDTO;
import com.uniport.dto.EtfAnalysisReportResponseDTO;
import com.uniport.dto.EtfAnalysisRequestDTO;
import com.uniport.dto.EtfAnalysisRiskDiagnosisDTO;
import com.uniport.dto.EtfAnalysisSeriesPointDTO;
import com.uniport.dto.EtfAnalysisStartResponseDTO;
import com.uniport.dto.EtfDiscoveryDetailHoldingDTO;
import com.uniport.dto.EtfDiscoveryDetailResponseDTO;
import com.uniport.dto.EtfDiscoveryItemDTO;
import com.uniport.dto.EtfDiscoveryResponseDTO;
import com.uniport.dto.EtfDiscoveryTrendPointDTO;
import com.uniport.dto.EtfFavoriteResponseDTO;
import com.uniport.dto.EtfPortfolioFitRecommendationItemDTO;
import com.uniport.dto.EtfPortfolioFitRecommendationRequestDTO;
import com.uniport.dto.EtfPortfolioFitRecommendationResponseDTO;
import com.uniport.dto.EtfShareRequestDTO;
import com.uniport.dto.EtfShareResponseDTO;
import com.uniport.dto.StockVisualDTO;
import com.uniport.entity.AssetMaster;
import com.uniport.entity.ManagedEtf;
import com.uniport.entity.ManagedEtfAnalysisReport;
import com.uniport.entity.ManagedEtfFavorite;
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
import com.uniport.service.backtest.BacktestHolding;
import com.uniport.service.backtest.BacktestPricePoint;
import com.uniport.service.backtest.BacktestRequest;
import com.uniport.service.backtest.BacktestResult;
import com.uniport.service.backtest.EtfAiFeedbackService;
import com.uniport.service.backtest.EtfBacktestEngine;
import com.uniport.service.backtest.EtfNewsExposure;
import com.uniport.service.backtest.HistoricalPriceProvider;
import com.uniport.service.backtest.InsightFacts;
import com.uniport.service.backtest.RuleBasedFeedback;
import jakarta.annotation.PreDestroy;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Service
public class EtfDataService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<HoldingPayload>> HOLDING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final BigDecimal DEFAULT_PRINCIPAL_KRW = BigDecimal.valueOf(100_000_000L);
    private static final BigDecimal TRANSACTION_FEE_RATE = BigDecimal.ZERO;
    private static final BigDecimal SLIPPAGE_RATE = BigDecimal.ZERO;
    private static final String DEFAULT_ANALYSIS_PERIOD = "1Y";
    private static final String DEFAULT_ANALYSIS_BENCHMARK = "SP500";
    private static final String DEFAULT_REBALANCE_POLICY = "MONTHLY";
    private static final List<String> SUPPORTED_REBALANCE_POLICIES = List.of("MONTHLY", "QUARTERLY", "SEMI_ANNUAL", "NONE");
    private static final String ANALYSIS_VERSION = "backtest-v2.0.0";
    private static final String MESSAGE_VERSION = "ai-feedback-v2.0.0";
    private static final String PRICE_SOURCE = "Yahoo Finance chart API, KIS chart API, or cached real prices";
    private static final String PRICE_CACHE_POLICY = "asset_price_daily";
    private static final String FX_CACHE_POLICY = "fx_rate_daily";
    private static final int DEFAULT_ASSET_SEARCH_SIZE = 10;
    private static final int MAX_ASSET_SEARCH_SIZE = 30;
    private static final int ASSET_SEARCH_POOL_SIZE = 200;
    private static final int DEFAULT_RECOMMENDATION_LIMIT = 3;
    private static final int MAX_RECOMMENDATION_LIMIT = 10;
    private static final int PRICE_FETCH_POOL_SIZE = 2;
    private static final int PRICE_FETCH_TIMEOUT_SECONDS = 8;
    private static final double MIN_PERIOD_COVERAGE_RATIO = 0.80d;
    private static final String ASSET_TYPE_STOCK = "STOCK";
    private static final String ASSET_TYPE_ETF = "ETF";
    private static final String ASSET_TYPE_LEVERAGED_ETF = "LEVERAGED_ETF";
    private static final String ASSET_TYPE_INVERSE_ETF = "INVERSE_ETF";
    private static final String ASSET_TYPE_BOND = "BOND";
    private static final String ASSET_TYPE_CASH = "CASH";
    private static final String DATA_STATUS_VERIFIED = "VERIFIED";
    private static final String DATA_STATUS_PROXY = "PROXY";
    private static final String DATA_STATUS_PENDING = "PENDING_VERIFICATION";
    private static final String DATA_STATUS_PRICE_UNAVAILABLE = "PRICE_UNAVAILABLE";
    private static final String PRICE_COVERAGE_READY = "READY";
    private static final String PRICE_COVERAGE_PARTIAL = "PARTIAL";
    private static final String PRICE_COVERAGE_PENDING = "PENDING";
    private static final String PRICE_COVERAGE_UNAVAILABLE = "UNAVAILABLE";
    private static final String PENDING_VERIFICATION_MESSAGE = "분석 시점에 실가격을 확인하며, 가격 데이터가 부족하면 분석이 제한됩니다.";
    private static final String ERROR_CODE_PRICE_DATA_UNAVAILABLE = "ETF_PRICE_DATA_UNAVAILABLE";
    private static final String ERROR_CODE_BENCHMARK_PRICE_DATA_UNAVAILABLE = "ETF_BENCHMARK_PRICE_DATA_UNAVAILABLE";
    private static final Set<String> KNOWN_ETF_SYMBOLS = Set.of(
            "DIA", "GLD", "IWM", "IVV", "QQQ", "SLV", "SOXX", "SPY", "TLT", "VGT", "VOO", "VTI", "XLK"
    );
    private static final Set<String> KNOWN_LEVERAGED_ETF_SYMBOLS = Set.of(
            "FNGU", "QLD", "SOXL", "SPXL", "SSO", "TECL", "TQQQ", "UPRO"
    );
    private static final Set<String> KNOWN_INVERSE_ETF_SYMBOLS = Set.of(
            "PSQ", "QID", "SDS", "SH", "SOXS", "SPXS", "SQQQ", "TZA"
    );

    private final ManagedEtfRepository managedEtfRepository;
    private final ManagedEtfAnalysisReportRepository managedEtfAnalysisReportRepository;
    private final ManagedEtfFavoriteRepository managedEtfFavoriteRepository;
    private final StockMasterRepository stockMasterRepository;
    private final AssetMasterRepository assetMasterRepository;
    private final AssetAliasRepository assetAliasRepository;
    private final AssetPriceDailyRepository assetPriceDailyRepository;
    private final HistoricalPriceProvider historicalPriceProvider;
    private final EtfBacktestEngine etfBacktestEngine;
    private final EtfAiFeedbackService etfAiFeedbackService;
    private final EtfNewsExposureService etfNewsExposureService;
    private final StockVisualAssetResolver stockVisualAssetResolver;
    private final YahooAssetSearchClient yahooAssetSearchClient;
    private final PortfolioFitModelClient portfolioFitModelClient;
    private final StockSymbolLogoUrlResolver stockSymbolLogoUrlResolver;
    private final ExecutorService priceFetchExecutor = Executors.newFixedThreadPool(PRICE_FETCH_POOL_SIZE, priceFetchThreadFactory());

    public EtfDataService(ManagedEtfRepository managedEtfRepository,
                          ManagedEtfAnalysisReportRepository managedEtfAnalysisReportRepository,
                          ManagedEtfFavoriteRepository managedEtfFavoriteRepository,
                          StockMasterRepository stockMasterRepository,
                          AssetMasterRepository assetMasterRepository,
                          AssetAliasRepository assetAliasRepository,
                          AssetPriceDailyRepository assetPriceDailyRepository,
                          HistoricalPriceProvider historicalPriceProvider,
                          EtfBacktestEngine etfBacktestEngine,
                          EtfAiFeedbackService etfAiFeedbackService,
                          EtfNewsExposureService etfNewsExposureService,
                          StockVisualAssetResolver stockVisualAssetResolver,
                          YahooAssetSearchClient yahooAssetSearchClient,
                          PortfolioFitModelClient portfolioFitModelClient,
                          StockSymbolLogoUrlResolver stockSymbolLogoUrlResolver) {
        this.managedEtfRepository = managedEtfRepository;
        this.managedEtfAnalysisReportRepository = managedEtfAnalysisReportRepository;
        this.managedEtfFavoriteRepository = managedEtfFavoriteRepository;
        this.stockMasterRepository = stockMasterRepository;
        this.assetMasterRepository = assetMasterRepository;
        this.assetAliasRepository = assetAliasRepository;
        this.assetPriceDailyRepository = assetPriceDailyRepository;
        this.historicalPriceProvider = historicalPriceProvider;
        this.etfBacktestEngine = etfBacktestEngine;
        this.etfAiFeedbackService = etfAiFeedbackService;
        this.etfNewsExposureService = etfNewsExposureService;
        this.stockVisualAssetResolver = stockVisualAssetResolver;
        this.yahooAssetSearchClient = yahooAssetSearchClient;
        this.portfolioFitModelClient = portfolioFitModelClient;
        this.stockSymbolLogoUrlResolver = stockSymbolLogoUrlResolver;
    }

    @PreDestroy
    void shutdownPriceFetchExecutor() {
        priceFetchExecutor.shutdownNow();
    }

    public CustomEtfAssetSearchResponseDTO searchAssets(String keywordParam,
                                                        String assetTypeParam,
                                                        String marketParam,
                                                        Integer pageParam,
                                                        Integer sizeParam) {
        String keyword = keywordParam == null ? "" : keywordParam.trim();
        String assetType = normalizeAssetType(assetTypeParam);
        String market = marketParam == null ? "" : marketParam.trim().toUpperCase(Locale.ROOT);
        int page = pageParam == null || pageParam < 0 ? 0 : pageParam;
        int size = sizeParam == null || sizeParam < 1
                ? DEFAULT_ASSET_SEARCH_SIZE
                : Math.min(sizeParam, MAX_ASSET_SEARCH_SIZE);

        LinkedHashMap<String, EtfAssetCatalogItem> candidates = new LinkedHashMap<>();
        String repositoryAssetType = assetType.isBlank() ? null : assetType;
        String repositoryMarket = market.isBlank() || "ALL".equals(market) ? null : market;
        searchActiveAssets(keyword, repositoryAssetType, repositoryMarket).stream()
                .map(this::toAssetCatalogItem)
                .filter(item -> matchesAssetSearch(item, keyword, assetType, market))
                .filter(this::isSearchVisibleAsset)
                .forEach(item -> candidates.putIfAbsent(item.assetId(), item));
        searchAliasMatchedAssets(keyword, repositoryAssetType, repositoryMarket).stream()
                .map(this::toAssetCatalogItem)
                .filter(item -> matchesAssetSearch(item, "", assetType, market))
                .filter(this::isSearchVisibleAsset)
                .forEach(item -> candidates.putIfAbsent(item.assetId(), item));

        if (assetType.isBlank() || ASSET_TYPE_STOCK.equals(assetType)) {
            List<StockMaster> stocks = keyword.isBlank()
                    ? stockMasterRepository.findAll(PageRequest.of(0, ASSET_SEARCH_POOL_SIZE)).getContent()
                    : stockMasterRepository.searchForEtfAssetCandidates(keyword, PageRequest.of(0, ASSET_SEARCH_POOL_SIZE));
            stocks.stream()
                    .map(this::toAssetCatalogItem)
                    .filter(item -> matchesAssetSearch(item, keyword, assetType, market))
                    .filter(this::isSearchVisibleAsset)
                    .forEach(item -> candidates.putIfAbsent(item.assetId(), item));
        }
        yahooSearchFallback(keyword, assetType, market, size).stream()
                .filter(this::isSearchVisibleAsset)
                .forEach(item -> candidates.putIfAbsent(item.assetId(), item));
        if (candidates.isEmpty()) {
            exactUsTickerFallback(keyword, assetType, market)
                    .filter(this::isSearchVisibleAsset)
                    .ifPresent(item -> candidates.put(item.assetId(), item));
        }

        Map<String, CachedPriceCoverage> coverageByAssetId = cachedPriceCoverageByAssetId(candidates.keySet());
        List<CustomEtfAssetSearchItemDTO> all = candidates.values().stream()
                .map(item -> toAssetSearchItem(item, coverageByAssetId.getOrDefault(item.assetId(), CachedPriceCoverage.empty(item.assetId()))))
                .toList();
        int fromIndex = Math.min(page * size, all.size());
        int toIndex = Math.min(fromIndex + size, all.size());
        return CustomEtfAssetSearchResponseDTO.builder()
                .items(all.subList(fromIndex, toIndex))
                .page(page)
                .size(size)
                .totalCount(all.size())
                .hasNext(toIndex < all.size())
                .build();
    }

    public EtfPortfolioFitRecommendationResponseDTO recommendPortfolioFitStocks(
            User user,
            EtfPortfolioFitRecommendationRequestDTO request) {
        RecommendationContext context = resolveRecommendationContext(user, request);
        if (context.holdings().isEmpty()) {
            throw new ApiException("items or customEtfId is required", HttpStatus.BAD_REQUEST);
        }
        int limit = recommendationLimit(request != null ? request.getLimit() : null);
        String market = recommendationMarket(request != null ? request.getMarket() : null);
        RecommendationProfile profile = buildRecommendationProfile(context);
        List<String> ownedStockIds = context.holdings().stream()
                .map(HoldingPayload::stockId)
                .map(this::canonicalStockId)
                .toList();
        List<PortfolioFitRecommendationCandidate> rankedCandidates = recommendationCandidates(market).stream()
                .filter(item -> ASSET_TYPE_STOCK.equals(item.assetType()))
                .filter(item -> !ownedStockIds.contains(canonicalStockId(item.assetId())))
                .map(item -> toPortfolioFitRecommendationCandidate(item, profile))
                .filter(candidate -> candidate.item().getFitScore() >= 0.50)
                .sorted(Comparator
                        .comparing((PortfolioFitRecommendationCandidate candidate) -> candidate.item().getFitScore()).reversed()
                        .thenComparing(candidate -> candidate.item().getName()))
                .toList();
        List<EtfPortfolioFitRecommendationItemDTO> items = diversifyPortfolioFitRecommendations(rankedCandidates, limit).stream()
                .map(PortfolioFitRecommendationCandidate::item)
                .toList();
        return EtfPortfolioFitRecommendationResponseDTO.builder()
                .items(items)
                .build();
    }

    public CustomEtfListResponseDTO getCustomEtfs(User user) {
        List<CustomEtfSummaryDTO> items = managedEtfRepository.findByOwnerUserIdOrderByUpdatedAtDesc(user.getId()).stream()
                .filter(etf -> isSourceType(etf, "CUSTOM"))
                .map(this::toCustomSummary)
                .toList();
        return CustomEtfListResponseDTO.builder().items(items).build();
    }

    @Transactional
    public CustomEtfMutationResponseDTO createCustomEtf(User user, CustomEtfCreateRequestDTO request) {
        validateItems(request != null ? request.getItems() : null);
        ManagedEtf saved = managedEtfRepository.save(ManagedEtf.builder()
                .etfCode(generateEtfCode())
                .ownerUserId(user.getId())
                .sourceType("CUSTOM")
                .title(blankToDefault(request.getTitle(), "My Custom ETF"))
                .holdingsJson(writeHoldings(request.getItems()))
                .favoriteCount(0)
                .popularityScore(0)
                .publishedAt(java.time.LocalDateTime.now())
                .build());
        return CustomEtfMutationResponseDTO.builder()
                .etfId(saved.getEtfCode())
                .title(saved.getTitle())
                .totalWeight(sumWeights(readHoldings(saved.getHoldingsJson())))
                .createdAt(timestamp(saved.getCreatedAt(), saved.getUpdatedAt()))
                .updatedAt(timestamp(saved.getUpdatedAt(), saved.getCreatedAt()))
                .build();
    }

    public CustomEtfDetailResponseDTO getCustomEtf(User user, String etfId) {
        return toCustomDetail(getRequiredCustomEtf(user, etfId));
    }

    @Transactional
    public CustomEtfMutationResponseDTO updateCustomEtf(User user, String etfId, CustomEtfUpdateRequestDTO request) {
        ManagedEtf etf = getRequiredCustomEtf(user, etfId);
        validateItems(request != null ? request.getItems() : null);
        etf.setTitle(blankToDefault(request.getTitle(), etf.getTitle()));
        etf.setHoldingsJson(writeHoldings(request.getItems()));
        ManagedEtf saved = managedEtfRepository.save(etf);
        return CustomEtfMutationResponseDTO.builder()
                .etfId(saved.getEtfCode())
                .title(saved.getTitle())
                .totalWeight(sumWeights(readHoldings(saved.getHoldingsJson())))
                .createdAt(timestamp(saved.getCreatedAt(), saved.getUpdatedAt()))
                .updatedAt(timestamp(saved.getUpdatedAt(), saved.getCreatedAt()))
                .build();
    }

    @Transactional
    public EtfAnalysisStartResponseDTO analyze(User user, String etfId, EtfAnalysisRequestDTO request) {
        ManagedEtf etf = getRequiredCustomEtf(user, etfId);
        String period = resolveAnalysisPeriod(request != null ? request.getPeriod() : null);
        String benchmark = resolveAnalysisBenchmark(request != null ? request.getBenchmark() : null);
        validatePeriod(period);
        validateBenchmark(benchmark);
        BigDecimal principalAmount = resolvePrincipal(request != null ? request.getPrincipalAmountKrw() : null);
        String rebalancePolicy = resolveRebalancePolicy(request != null ? request.getRebalancePolicy() : null);

        String reportId = "REPORT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        EtfAnalysisReportResponseDTO response = buildReport(reportId, etf, period, benchmark, principalAmount, rebalancePolicy);
        managedEtfAnalysisReportRepository.save(ManagedEtfAnalysisReport.builder()
                .reportId(reportId)
                .ownerUserId(user.getId())
                .etfCode(etf.getEtfCode())
                .period(response.getPeriod())
                .benchmark(benchmark)
                .reportJson(writeValue(response))
                .build());
        etf.setLastReportId(reportId);
        managedEtfRepository.save(etf);
        return EtfAnalysisStartResponseDTO.builder()
                .reportId(reportId)
                .etfId(etf.getEtfCode())
                .status("COMPLETED")
                .createdAt(java.time.OffsetDateTime.now(ZoneOffset.UTC).toString())
                .build();
    }

    public EtfAnalysisReportResponseDTO getReport(User user, String reportId, String periodOverride) {
        ManagedEtfAnalysisReport report = managedEtfAnalysisReportRepository.findByReportId(reportId)
                .orElseThrow(() -> new ApiException("ETF analysis report not found", HttpStatus.NOT_FOUND));
        if (!report.getOwnerUserId().equals(user.getId())) {
            throw new ApiException("ETF analysis report not found", HttpStatus.NOT_FOUND);
        }
        EtfAnalysisReportResponseDTO stored = readValue(report.getReportJson(), EtfAnalysisReportResponseDTO.class);
        if (periodOverride == null || periodOverride.isBlank()) {
            return stored;
        }
        String period = safeUpper(periodOverride);
        validatePeriod(period);
        ManagedEtf etf = getRequiredCustomEtf(user, report.getEtfCode());
        BigDecimal principalAmount = stored.getMetadata() != null && stored.getMetadata().getPrincipalAmountKrw() != null
                ? BigDecimal.valueOf(stored.getMetadata().getPrincipalAmountKrw())
                : DEFAULT_PRINCIPAL_KRW;
        String rebalancePolicy = stored.getMetadata() != null && stored.getMetadata().getRebalancePolicy() != null
                ? stored.getMetadata().getRebalancePolicy()
                : DEFAULT_REBALANCE_POLICY;
        return buildReport(reportId, etf, period, report.getBenchmark(), principalAmount, rebalancePolicy);
    }

    @Transactional
    public EtfAnalysisApplyResponseDTO applyReport(User user, String etfId, String reportId, EtfAnalysisApplyRequestDTO request) {
        if (request == null || request.getApplyMode() == null || !"REPLACE".equalsIgnoreCase(request.getApplyMode())) {
            throw new ApiException("applyMode must be REPLACE", HttpStatus.BAD_REQUEST);
        }
        ManagedEtf etf = getRequiredCustomEtf(user, etfId);
        ManagedEtfAnalysisReport report = managedEtfAnalysisReportRepository.findByReportId(reportId)
                .orElseThrow(() -> new ApiException("ETF analysis report not found", HttpStatus.NOT_FOUND));
        if (!report.getOwnerUserId().equals(user.getId()) || !report.getEtfCode().equals(etfId)) {
            throw new ApiException("ETF analysis report not found", HttpStatus.NOT_FOUND);
        }
        EtfAnalysisReportResponseDTO response = readValue(report.getReportJson(), EtfAnalysisReportResponseDTO.class);
        List<HoldingPayload> holdings = response.getAllocation().getItems().stream()
                .map(item -> new HoldingPayload(item.getSecurityId() != null && !item.getSecurityId().isBlank()
                        ? item.getSecurityId()
                        : resolveStockId(item.getName()), item.getWeight(), null))
                .toList();
        etf.setHoldingsJson(writeValue(holdings));
        etf.setLastReportId(reportId);
        managedEtfRepository.save(etf);
        return EtfAnalysisApplyResponseDTO.builder()
                .etfId(etfId)
                .reportId(reportId)
                .applied(Boolean.TRUE)
                .updatedAt(etf.getUpdatedAt().atOffset(ZoneOffset.UTC).toString())
                .build();
    }

    public EtfDiscoveryResponseDTO getPopularEtfs(String sort, String theme, String query, Integer page, Integer size, User user) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 10 : Math.min(size, 20);
        List<ManagedEtf> all = managedEtfRepository.findAll().stream()
                .filter(etf -> isSourceType(etf, "DISCOVERY"))
                .toList();
        List<EtfDiscoveryItemDTO> filtered = all.stream()
                .filter(etf -> theme == null || theme.isBlank() || theme.equalsIgnoreCase(etf.getTheme()))
                .filter(etf -> matchesDiscoveryQuery(etf, query))
                .sorted(resolveComparator(sort))
                .map(etf -> toDiscoveryItem(etf, user))
                .toList();
        List<String> themes = all.stream()
                .map(ManagedEtf::getTheme)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        int fromIndex = Math.min(safePage * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        return EtfDiscoveryResponseDTO.builder()
                .items(filtered.subList(fromIndex, toIndex))
                .themes(themes)
                .totalCount(filtered.size())
                .page(safePage)
                .size(safeSize)
                .hasNext(toIndex < filtered.size())
                .build();
    }

    public EtfDiscoveryDetailResponseDTO getDiscoveryDetail(String etfId, String period, User user) {
        ManagedEtf etf = getRequiredDiscoveryEtf(etfId);
        String safePeriod = period == null || period.isBlank() ? "1Y" : safeUpper(period);
        validatePeriod(safePeriod);
        boolean favorite = user != null && user.getId() != null && managedEtfFavoriteRepository.existsByUserIdAndEtfCode(user.getId(), etfId);
        return EtfDiscoveryDetailResponseDTO.builder()
                .etfId(etf.getEtfCode())
                .title(etf.getTitle())
                .subtitle(discoverySummaryValue(etf, "subtitle", Optional.ofNullable(etf.getTheme()).orElse("")))
                .description(discoverySummaryValue(etf, "description", Optional.ofNullable(etf.getShortDescription()).orElse("")))
                .badgeLabel(discoveryBadgeLabel(etf))
                .tags(parseTags(etf.getAnalysisSummaryJson()))
                .recentReturnRate3M(etf.getReturnRate() != null ? etf.getReturnRate().doubleValue() : 0.0)
                .riskLevel(Optional.ofNullable(etf.getRiskLevel()).orElse("MEDIUM"))
                .period(safePeriod)
                .favorite(favorite)
                .favoriteCount(discoveryFavoriteCount(etf))
                .thumbnailUrl(etf.getImageUrl())
                .trend(readTrend(etf.getTrendPointsJson(), safePeriod))
                .holdings(readHoldings(etf.getHoldingsJson()).stream()
                        .map(this::toDiscoveryHolding)
                        .toList())
                .build();
    }

    @Transactional
    public CustomEtfMutationResponseDTO applyDiscoveryEtf(User user, String etfId) {
        ManagedEtf source = getRequiredDiscoveryEtf(etfId);
        List<HoldingPayload> holdings = readHoldings(source.getHoldingsJson());
        if (holdings.isEmpty()) {
            throw new ApiException("ETF holdings are required", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        validateBacktestEligibleHoldings(holdings);
        ManagedEtf saved = managedEtfRepository.save(ManagedEtf.builder()
                .etfCode(generateEtfCode())
                .ownerUserId(user.getId())
                .sourceType("CUSTOM")
                .title(source.getTitle())
                .theme(source.getTheme())
                .imageUrl(source.getImageUrl())
                .shortDescription(discoverySummaryValue(source, "description", source.getShortDescription()))
                .holdingsJson(writeValue(holdings))
                .favoriteCount(0)
                .popularityScore(0)
                .publishedAt(LocalDateTime.now())
                .build());
        return CustomEtfMutationResponseDTO.builder()
                .etfId(saved.getEtfCode())
                .title(saved.getTitle())
                .totalWeight(sumWeights(readHoldings(saved.getHoldingsJson())))
                .createdAt(timestamp(saved.getCreatedAt(), saved.getUpdatedAt()))
                .updatedAt(timestamp(saved.getUpdatedAt(), saved.getCreatedAt()))
                .build();
    }

    @Transactional
    public EtfFavoriteResponseDTO favoriteDiscoveryEtf(User user, String etfId, boolean favorite) {
        ManagedEtf etf = getRequiredDiscoveryEtf(etfId);
        if (favorite) {
            if (!managedEtfFavoriteRepository.existsByUserIdAndEtfCode(user.getId(), etfId)) {
                managedEtfFavoriteRepository.save(ManagedEtfFavorite.builder().userId(user.getId()).etfCode(etfId).build());
            }
        } else {
            managedEtfFavoriteRepository.deleteByUserIdAndEtfCode(user.getId(), etfId);
        }
        return EtfFavoriteResponseDTO.builder()
                .etfId(etfId)
                .favorite(favorite)
                .favoriteCount(discoveryFavoriteCount(etf))
                .message(favorite ? "ETF added to favorites." : "ETF removed from favorites.")
                .build();
    }

    public EtfShareResponseDTO shareCustomEtf(User user, String etfId, EtfShareRequestDTO request) {
        ManagedEtf etf = getRequiredCustomEtf(user, etfId);
        String targetType = safeUpper(request != null ? request.getTargetType() : null);
        if (!List.of("COMMUNITY", "CHAT").contains(targetType)) {
            throw new ApiException("targetType must be COMMUNITY or CHAT", HttpStatus.BAD_REQUEST);
        }
        if ("CHAT".equals(targetType) && (request == null || request.getRoomId() == null)) {
            throw new ApiException("roomId is required when targetType is CHAT", HttpStatus.BAD_REQUEST);
        }
        return EtfShareResponseDTO.builder()
                .etfId(etfId)
                .targetType(targetType)
                .shared(Boolean.TRUE)
                .title("COMMUNITY".equals(targetType) ? "ETF ready to share to community." : "ETF ready to share to chat.")
                .description("COMMUNITY".equals(targetType)
                        ? etf.getTitle() + " can now be published as a community card."
                        : etf.getTitle() + " can now be shared to room " + request.getRoomId() + ".")
                .build();
    }

    private RecommendationContext resolveRecommendationContext(User user, EtfPortfolioFitRecommendationRequestDTO request) {
        if (request == null) {
            return new RecommendationContext(List.of(), "", "");
        }
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            validateRecommendationItems(request.getItems());
            return new RecommendationContext(
                    request.getItems().stream()
                            .map(item -> new HoldingPayload(item.getStockId(), item.getWeight(), null))
                            .toList(),
                    "요청 포트폴리오",
                    ""
            );
        }
        if (request.getCustomEtfId() != null && !request.getCustomEtfId().isBlank()) {
            ManagedEtf etf = getRequiredCustomEtf(user, request.getCustomEtfId());
            return new RecommendationContext(
                    readHoldings(etf.getHoldingsJson()),
                    Optional.ofNullable(etf.getTitle()).orElse("나만의 ETF"),
                    Optional.ofNullable(etf.getTheme()).orElse("")
            );
        }
        if (request.getAnalysisReportId() != null && !request.getAnalysisReportId().isBlank()) {
            ManagedEtfAnalysisReport report = managedEtfAnalysisReportRepository.findByReportId(request.getAnalysisReportId())
                    .orElseThrow(() -> new ApiException("ETF analysis report not found", HttpStatus.NOT_FOUND));
            if (!report.getOwnerUserId().equals(user.getId())) {
                throw new ApiException("ETF analysis report not found", HttpStatus.NOT_FOUND);
            }
            EtfAnalysisReportResponseDTO response = readValue(report.getReportJson(), EtfAnalysisReportResponseDTO.class);
            List<HoldingPayload> holdings = response.getAllocation() == null || response.getAllocation().getItems() == null
                    ? List.of()
                    : response.getAllocation().getItems().stream()
                    .map(item -> new HoldingPayload(
                            item.getSecurityId() != null && !item.getSecurityId().isBlank()
                                    ? item.getSecurityId()
                                    : resolveStockId(item.getName()),
                            item.getWeight(),
                            null))
                    .toList();
            return new RecommendationContext(holdings, "분석 리포트", "");
        }
        return new RecommendationContext(List.of(), "", "");
    }

    private void validateRecommendationItems(List<CustomEtfItemRequestDTO> items) {
        for (CustomEtfItemRequestDTO item : items) {
            if (item.getStockId() == null || item.getStockId().isBlank()) {
                throw new ApiException("stockId is required", HttpStatus.BAD_REQUEST);
            }
            if (item.getWeight() == null || item.getWeight() < 1 || item.getWeight() > 100) {
                throw new ApiException("weight must be between 1 and 100", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private int recommendationLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_RECOMMENDATION_LIMIT;
        }
        return Math.min(limit, MAX_RECOMMENDATION_LIMIT);
    }

    private String recommendationMarket(String marketParam) {
        String market = safeUpper(marketParam);
        return market.isBlank() || "ALL".equals(market) ? null : market;
    }

    private List<EtfAssetCatalogItem> recommendationCandidates(String market) {
        List<AssetMaster> assets = assetMasterRepository.searchActive(
                "",
                ASSET_TYPE_STOCK,
                market,
                PageRequest.of(0, ASSET_SEARCH_POOL_SIZE)
        );
        if (assets == null) {
            return List.of();
        }
        return assets.stream()
                .map(this::toAssetCatalogItem)
                .filter(this::isSearchVisibleAsset)
                .toList();
    }

    private RecommendationProfile buildRecommendationProfile(RecommendationContext context) {
        Map<String, Integer> marketWeights = new LinkedHashMap<>();
        List<String> keywords = new ArrayList<>();
        if (context.title() != null) {
            keywords.addAll(inferThemeKeywords(context.title()));
        }
        if (context.theme() != null) {
            keywords.addAll(inferThemeKeywords(context.theme()));
        }
        for (HoldingPayload holding : context.holdings()) {
            StockRef ref = resolveStock(holding.stockId());
            int weight = holding.weight() != null ? holding.weight() : 0;
            marketWeights.merge(broadMarket(ref.market()), weight, Integer::sum);
            keywords.addAll(inferThemeKeywords(ref.name() + " " + ref.symbol() + " " + ref.market()));
        }
        String dominantMarket = marketWeights.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
        List<String> distinctKeywords = keywords.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        String fingerprint = context.holdings().stream()
                .map(holding -> canonicalStockId(holding.stockId()) + ":" + (holding.weight() != null ? holding.weight() : 0))
                .sorted()
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
        String portfolioLabel = context.title() != null && !context.title().isBlank()
                ? context.title()
                : "사용자 포트폴리오";
        return new RecommendationProfile(dominantMarket, distinctKeywords, marketWeights.size(), fingerprint, portfolioLabel);
    }

    private PortfolioFitRecommendationCandidate toPortfolioFitRecommendationCandidate(
            EtfAssetCatalogItem item,
            RecommendationProfile profile) {
        ResolvedStockVisual visual = resolveStockVisual(item.market(), item.symbol(), item.name());
        CachedPriceCoverage coverage = cachedPriceCoverageForAsset(item.assetId());
        boolean selectable = isSearchSelectableAsset(item, coverage);
        String dataStatus = searchDataStatus(item, coverage);
        List<String> candidateKeywords = inferThemeKeywords(item.name() + " " + item.symbol() + " " + item.market());
        boolean sameMarket = broadMarket(item.market()).equals(profile.dominantMarket());
        boolean themeMatch = candidateKeywords.stream().anyMatch(profile.keywords()::contains);
        Optional<PortfolioFitModelScore> modelScore = scorePortfolioFitModel(item, profile, sameMarket, themeMatch, candidateKeywords);
        double score = portfolioFitScore(item, profile, sameMarket, themeMatch, modelScore);
        List<String> tags = recommendationTags(item, profile, sameMarket, themeMatch, candidateKeywords, modelScore);
        EtfPortfolioFitRecommendationItemDTO recommendation = EtfPortfolioFitRecommendationItemDTO.builder()
                .recommendationId("FIT_" + item.assetId())
                .stockId(item.assetId())
                .name(item.name())
                .symbol(item.symbol())
                .market(item.market())
                .fitScore(roundScore(score))
                .reason(recommendationReason(item, profile, sameMarket, themeMatch, candidateKeywords, modelScore))
                .tags(tags)
                .backtestEnabled(selectable)
                .dataStatus(dataStatus)
                .dataStatusMessage(searchDataStatusMessage(item, dataStatus, coverage))
                .logoUrl(visual.logoUrl())
                .visual(visual.visual())
                .build();
        return new PortfolioFitRecommendationCandidate(
                recommendation,
                recommendationBucket(item, profile, sameMarket, themeMatch, candidateKeywords)
        );
    }

    private List<PortfolioFitRecommendationCandidate> diversifyPortfolioFitRecommendations(
            List<PortfolioFitRecommendationCandidate> rankedCandidates,
            int limit) {
        if (rankedCandidates.isEmpty() || limit <= 0) {
            return List.of();
        }
        int maxPerBucket = limit >= 3 ? 2 : limit;
        List<PortfolioFitRecommendationCandidate> selected = new ArrayList<>();
        Set<String> selectedStockIds = new LinkedHashSet<>();
        Map<String, Integer> bucketCounts = new LinkedHashMap<>();
        for (PortfolioFitRecommendationCandidate candidate : rankedCandidates) {
            if (selected.size() >= limit) {
                break;
            }
            int bucketCount = bucketCounts.getOrDefault(candidate.bucket(), 0);
            if (bucketCount >= maxPerBucket) {
                continue;
            }
            selected.add(candidate);
            selectedStockIds.add(candidate.item().getStockId());
            bucketCounts.put(candidate.bucket(), bucketCount + 1);
        }
        for (PortfolioFitRecommendationCandidate candidate : rankedCandidates) {
            if (selected.size() >= limit) {
                break;
            }
            if (selectedStockIds.add(candidate.item().getStockId())) {
                selected.add(candidate);
            }
        }
        return selected;
    }

    private String recommendationBucket(EtfAssetCatalogItem item,
                                        RecommendationProfile profile,
                                        boolean sameMarket,
                                        boolean themeMatch,
                                        List<String> candidateKeywords) {
        if (themeMatch) {
            return candidateKeywords.stream()
                    .filter(profile.keywords()::contains)
                    .findFirst()
                    .map(keyword -> "THEME:" + keyword)
                    .orElse("THEME");
        }
        if (sameMarket && profile.dominantMarket() != null && !profile.dominantMarket().isBlank()) {
            return "MARKET:" + profile.dominantMarket();
        }
        return "DIVERSIFY:" + broadMarket(item.market());
    }

    private double portfolioFitScore(EtfAssetCatalogItem item,
                                     RecommendationProfile profile,
                                     boolean sameMarket,
                                     boolean themeMatch,
                                     Optional<PortfolioFitModelScore> modelScore) {
        double score = 0.45;
        if (sameMarket) {
            score += 0.18;
        }
        if (themeMatch) {
            score += 0.24;
        }
        if (!sameMarket && profile.marketCount() <= 1) {
            score += 0.05;
        }
        if (Boolean.TRUE.equals(item.backtestEnabled()) && DATA_STATUS_VERIFIED.equals(item.priceSourceStatus())) {
            score += 0.10;
        } else if (Boolean.TRUE.equals(item.backtestEnabled())) {
            score += 0.06;
        } else {
            score += 0.03;
        }
        if (modelScore.isPresent()) {
            PortfolioFitModelScore model = modelScore.get();
            double confidence = Math.max(0.0, Math.min(model.confidence(), 1.0));
            double adjustment = confidence * 0.18;
            score += model.positive() ? adjustment : -adjustment;
        }
        score += portfolioSpecificTieBreak(item, profile);
        return Math.max(0.0, Math.min(score, 0.98));
    }

    private double portfolioSpecificTieBreak(EtfAssetCatalogItem item, RecommendationProfile profile) {
        String seed = profile.fingerprint() + "|" + item.assetId();
        return Math.floorMod(seed.hashCode(), 10) / 100.0;
    }

    private List<String> recommendationTags(EtfAssetCatalogItem item,
                                            RecommendationProfile profile,
                                            boolean sameMarket,
                                            boolean themeMatch,
                                            List<String> candidateKeywords,
                                            Optional<PortfolioFitModelScore> modelScore) {
        List<String> tags = new ArrayList<>();
        modelScore.ifPresent(score -> tags.add(score.positive() ? "적합 신호" : "주의 신호"));
        if (modelScore.isPresent() && !modelScore.get().positive()) {
            tags.add("관찰 우선");
        } else if (themeMatch) {
            tags.add("추가 검토");
        } else {
            tags.add("분산용 검토");
        }
        if (themeMatch) {
            candidateKeywords.stream()
                    .filter(profile.keywords()::contains)
                    .findFirst()
                    .ifPresent(keyword -> tags.add(keyword + " 연계"));
        }
        if (sameMarket && themeMatch) {
            tags.add("시장 연계");
        } else if (sameMarket) {
            tags.add("섹터 분산");
        } else {
            tags.add(themeMatch ? "시장 분산" : "분산 보완");
        }
        if (Boolean.TRUE.equals(item.backtestEnabled())) {
            tags.add("백테스트 가능");
        }
        return tags.stream().distinct().limit(3).toList();
    }

    private String recommendationReason(EtfAssetCatalogItem item,
                                        RecommendationProfile profile,
                                        boolean sameMarket,
                                        boolean themeMatch,
                                        List<String> candidateKeywords,
                                        Optional<PortfolioFitModelScore> modelScore) {
        String candidateName = item.name() != null && !item.name().isBlank()
                ? item.name()
                : item.symbol();
        if (modelScore.isPresent() && modelScore.get().positive()) {
            return candidateName + "은 추가 검토 가능 후보예요. 역할: 현재 포트폴리오와 방향성이 맞고 핵심 테마 노출을 더 키우는 성장 보강축입니다. "
                    + "주의: 이미 같은 테마가 강하면 변동성도 같이 커질 수 있어요. 확인: 실적 모멘텀과 가격 부담을 같이 보세요.";
        }
        if (modelScore.isPresent() && !modelScore.get().positive()) {
            return candidateName + "은 관찰 우선 후보예요. 역할: 지금 포트폴리오와 바로 맞물리는 힘은 약하지만 분산 후보로 볼 수 있습니다. "
                    + "주의: 추가해도 포트폴리오 성격이 뚜렷하게 좋아지지 않을 수 있어요. 확인: 가격 흐름과 기존 보유 종목과의 상관 방향을 보세요.";
        }
        if (themeMatch) {
            String keyword = candidateKeywords.stream()
                    .filter(profile.keywords()::contains)
                    .findFirst()
                    .orElse("핵심 테마");
            return candidateName + "은 추가 검토 가능 후보예요. 역할: 현재 포트폴리오의 "
                    + keyword + " 성격을 더 선명하게 만드는 테마 보강축입니다. "
                    + "주의: 같은 방향 노출이 늘어 하락장 방어력은 약해질 수 있어요. 확인: 이 테마 비중을 더 키워도 버틸 수 있는지 보세요.";
        }
        if (sameMarket && profile.dominantMarket() != null && !profile.dominantMarket().isBlank()) {
            return candidateName + "은 분산 목적이라면 검토할 후보예요. 역할: 현재 포트폴리오의 "
                    + profile.dominantMarket() + " 안에서 특정 업종 편중을 낮추는 보완축입니다. "
                    + "주의: 수익 탄력을 키우기보다 흔들림을 낮추는 성격에 가깝습니다. 확인: 기존 핵심 종목과 다르게 움직이는지 보세요.";
        }
        return candidateName + "은 분산 목적이라면 검토할 후보예요. 역할: 현재 포트폴리오에 다른 시장 노출을 더해 한쪽 베팅을 완화하는 보완축입니다. "
                + "주의: 핵심 테마 수익률을 더 키우는 후보는 아닐 수 있어요. 확인: 환율, 시장 방향, 기존 보유 종목과의 동조성을 보세요.";
    }

    private Optional<PortfolioFitModelScore> scorePortfolioFitModel(EtfAssetCatalogItem item,
                                                                    RecommendationProfile profile,
                                                                    boolean sameMarket,
                                                                    boolean themeMatch,
                                                                    List<String> candidateKeywords) {
        if (portfolioFitModelClient == null) {
            return Optional.empty();
        }
        PortfolioFitModelInput input = new PortfolioFitModelInput(
                profile.portfolioLabel(),
                profile.keywords(),
                item.name(),
                item.symbol(),
                item.market(),
                candidateSignals(item, sameMarket, themeMatch, candidateKeywords)
        );
        return portfolioFitModelClient.score(input);
    }

    private List<String> candidateSignals(EtfAssetCatalogItem item,
                                          boolean sameMarket,
                                          boolean themeMatch,
                                          List<String> candidateKeywords) {
        List<String> signals = new ArrayList<>();
        signals.add(item.name() + " " + item.symbol() + " " + item.market());
        signals.add(sameMarket ? "기존 중심 시장과 같은 시장 후보" : "기존 포트폴리오에 다른 시장 노출을 더하는 후보");
        if (themeMatch) {
            signals.add("보유 테마와 후보 테마가 연결됨");
        }
        signals.addAll(candidateKeywords.stream().map(keyword -> keyword + " 후보").toList());
        return signals;
    }

    private List<String> inferThemeKeywords(String text) {
        String value = text == null ? "" : text.toLowerCase(Locale.ROOT);
        List<String> keywords = new ArrayList<>();
        if (value.contains("반도체") || value.contains("하이닉스") || value.contains("삼성전자")
                || value.contains("nvidia") || value.contains("nvda") || value.contains("amd")
                || value.contains("intel") || value.contains("chip") || value.contains("semiconductor")) {
            keywords.add("반도체");
        }
        if (value.contains("ai") || value.contains("인공지능") || value.contains("llm")) {
            keywords.add("AI");
        }
        if (value.contains("naver") || value.contains("카카오") || value.contains("google")
                || value.contains("meta") || value.contains("platform") || value.contains("플랫폼")) {
            keywords.add("플랫폼");
        }
        if (value.contains("tesla") || value.contains("전기차") || value.contains("배터리")
                || value.contains("2차전지") || value.contains("energy") || value.contains("에너지")) {
            keywords.add("모빌리티");
        }
        if (value.contains("금융") || value.contains("bank") || value.contains("jpm")
                || value.contains("배당") || value.contains("dividend")) {
            keywords.add("금융");
        }
        if (value.contains("헬스") || value.contains("바이오") || value.contains("health")
                || value.contains("pharma")) {
            keywords.add("헬스케어");
        }
        return keywords;
    }

    private String holdingTheme(StockRef ref, String fallbackTheme) {
        String source = (ref.name() != null ? ref.name() : "") + " "
                + (ref.symbol() != null ? ref.symbol() : "") + " "
                + (ref.market() != null ? ref.market() : "");
        List<String> keywords = inferThemeKeywords(source);
        if (!keywords.isEmpty()) {
            return String.join("/", keywords);
        }
        if (fallbackTheme != null && !fallbackTheme.isBlank()) {
            return fallbackTheme;
        }
        if (isUsMarket(ref.market())) {
            return "미국 주식";
        }
        if (isDomesticMarket(ref.market())) {
            return "국내 주식";
        }
        return "개별 주식";
    }

    private String broadMarket(String market) {
        if (isDomesticMarket(market)) {
            return "KRX";
        }
        if (isUsMarket(market)) {
            return "US";
        }
        return market == null ? "" : market.trim().toUpperCase(Locale.ROOT);
    }

    private String canonicalStockId(String stockId) {
        String normalized = stockId == null ? "" : stockId.trim().toUpperCase(Locale.ROOT);
        Optional<EtfAssetCatalogItem> catalogItem = findCatalogItem(normalized);
        if (catalogItem.isPresent()) {
            return catalogItem.get().assetId();
        }
        if (normalized.matches("\\d{6}")) {
            return "KRX_" + normalized;
        }
        return normalized;
    }

    private double roundScore(double score) {
        return BigDecimal.valueOf(score)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private ManagedEtf getRequiredCustomEtf(User user, String etfId) {
        ManagedEtf etf = managedEtfRepository.findByEtfCode(etfId)
                .orElseThrow(() -> new ApiException("Custom ETF not found", HttpStatus.NOT_FOUND));
        if (!user.getId().equals(etf.getOwnerUserId()) || !isSourceType(etf, "CUSTOM")) {
            throw new ApiException("Custom ETF not found", HttpStatus.NOT_FOUND);
        }
        return etf;
    }

    private ManagedEtf getRequiredDiscoveryEtf(String etfId) {
        ManagedEtf etf = managedEtfRepository.findByEtfCode(etfId)
                .orElseThrow(() -> new ApiException("ETF discovery item not found", HttpStatus.NOT_FOUND));
        if (!isSourceType(etf, "DISCOVERY")) {
            throw new ApiException("ETF discovery item not found", HttpStatus.NOT_FOUND);
        }
        return etf;
    }

    private boolean isSourceType(ManagedEtf etf, String sourceType) {
        return sourceType.equalsIgnoreCase(Optional.ofNullable(etf.getSourceType()).orElse(""));
    }

    private CustomEtfSummaryDTO toCustomSummary(ManagedEtf etf) {
        List<HoldingPayload> holdings = readHoldings(etf.getHoldingsJson());
        return CustomEtfSummaryDTO.builder()
                .etfId(etf.getEtfCode())
                .title(etf.getTitle())
                .thumbnailUrl(etf.getImageUrl())
                .itemCount(holdings.size())
                .totalWeight(sumWeights(holdings))
                .updatedAt(etf.getUpdatedAt().atOffset(ZoneOffset.UTC).toString())
                .build();
    }

    private CustomEtfDetailResponseDTO toCustomDetail(ManagedEtf etf) {
        return CustomEtfDetailResponseDTO.builder()
                .etfId(etf.getEtfCode())
                .title(etf.getTitle())
                .items(readHoldings(etf.getHoldingsJson()).stream().map(this::toCustomHolding).toList())
                .build();
    }

    private CustomEtfHoldingDTO toCustomHolding(HoldingPayload item) {
        StockRef ref = resolveStock(item.stockId());
        ResolvedStockVisual visual = resolveStockVisual(ref.market(), ref.symbol(), ref.name());
        return CustomEtfHoldingDTO.builder()
                .stockId(item.stockId())
                .name(ref.name())
                .symbol(ref.symbol())
                .market(ref.market())
                .assetType(ref.assetType())
                .currency(ref.currency())
                .weight(item.weight())
                .logoUrl(visual.logoUrl())
                .visual(visual.visual())
                .build();
    }

    private EtfDiscoveryDetailHoldingDTO toDiscoveryHolding(HoldingPayload item) {
        StockRef ref = resolveStock(item.stockId());
        ResolvedStockVisual visual = resolveStockVisual(ref.market(), ref.symbol(), ref.name());
        return EtfDiscoveryDetailHoldingDTO.builder()
                .name(ref.name())
                .symbol(ref.symbol())
                .market(ref.market())
                .assetType(ref.assetType())
                .currency(ref.currency())
                .weight(item.weight())
                .changeRate(item.changeRate() != null ? item.changeRate() : 0.0)
                .logoUrl(visual.logoUrl())
                .visual(visual.visual())
                .build();
    }

    private String discoveryBadgeLabel(ManagedEtf etf) {
        String badge = discoverySummaryValue(etf, "badge", null);
        if (badge != null && !badge.isBlank()) {
            return badge;
        }
        return discoveryFavoriteCount(etf) > 0 ? "인기" : Optional.ofNullable(etf.getTheme()).orElse("DISCOVERY");
    }

    private String discoverySummaryValue(ManagedEtf etf, String key, String fallback) {
        for (Map<String, Object> row : readMapList(etf.getAnalysisSummaryJson())) {
            Object value = row.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return fallback;
    }

    private int discoveryFavoriteCount(ManagedEtf etf) {
        int seeded = etf.getFavoriteCount() != null ? etf.getFavoriteCount() : 0;
        long actual = managedEtfFavoriteRepository.countByEtfCode(etf.getEtfCode());
        return seeded + (int) actual;
    }

    private EtfDiscoveryItemDTO toDiscoveryItem(ManagedEtf etf, User user) {
        boolean favorite = user != null && user.getId() != null && managedEtfFavoriteRepository.existsByUserIdAndEtfCode(user.getId(), etf.getEtfCode());
        double returnRate = etf.getReturnRate() != null ? etf.getReturnRate().doubleValue() : 0.0;
        return EtfDiscoveryItemDTO.builder()
                .etfId(etf.getEtfCode())
                .title(etf.getTitle())
                .subtitle(discoverySummaryValue(etf, "subtitle", Optional.ofNullable(etf.getTheme()).orElse("")))
                .theme(Optional.ofNullable(etf.getTheme()).orElse(""))
                .badgeLabel(discoveryBadgeLabel(etf))
                .returnRate3M(returnRate)
                .dailyExpectedReturnRate(returnRate)
                .followerCount(discoveryFavoriteCount(etf))
                .favorite(favorite)
                .thumbnailUrl(etf.getImageUrl())
                .build();
    }

    private Comparator<ManagedEtf> resolveComparator(String sort) {
        if ("POPULAR".equalsIgnoreCase(sort)) {
            return Comparator.comparingInt(this::discoveryFavoriteCount).reversed();
        }
        if ("RETURN".equalsIgnoreCase(sort)) {
            return Comparator.comparing((ManagedEtf etf) -> Optional.ofNullable(etf.getReturnRate()).orElse(BigDecimal.ZERO)).reversed();
        }
        return Comparator.comparing(this::discoveryPublishedAt).reversed();
    }

    private LocalDateTime discoveryPublishedAt(ManagedEtf etf) {
        LocalDateTime publishedAt = etf.getPublishedAt();
        return publishedAt != null ? publishedAt : Optional.ofNullable(etf.getCreatedAt()).orElse(LocalDateTime.MIN);
    }

    private EtfAssetCatalogItem toAssetCatalogItem(StockMaster stock) {
        String code = stock.getCode();
        String market = stock.getMarket() != null ? stock.getMarket() : "";
        String assetId = buildStockAssetId(market, code);
        Optional<AssetMaster> assetMaster = assetMasterRepository.findByAssetIdAndActiveTrue(assetId);
        if (assetMaster.isPresent()) {
            return toAssetCatalogItem(assetMaster.get());
        }
        return new EtfAssetCatalogItem(
                assetId,
                stock.getNameKr() != null ? stock.getNameKr() : code,
                code,
                market,
                searchAssetType(ASSET_TYPE_STOCK, code, stock.getNameKr()),
                isUsMarket(market) ? "USD" : "KRW",
                false,
                DATA_STATUS_PENDING,
                "Price data has not been verified"
        );
    }

    private List<AssetMaster> searchActiveAssets(String keyword, String assetType, String market) {
        List<AssetMaster> assets = assetMasterRepository.searchActive(
                keyword,
                assetType,
                market,
                PageRequest.of(0, ASSET_SEARCH_POOL_SIZE)
        );
        return assets != null ? assets : List.of();
    }

    private EtfAssetCatalogItem toAssetCatalogItem(AssetMaster asset) {
        return new EtfAssetCatalogItem(
                asset.getAssetId(),
                asset.getName(),
                asset.getSymbol(),
                asset.getMarket(),
                searchAssetType(asset.getAssetType(), asset.getSymbol(), asset.getName()),
                asset.getCurrency(),
                normalizeBacktestEnabled(asset),
                normalizeDataStatus(asset),
                asset.getLastPriceError()
        );
    }

    private List<AssetMaster> searchAliasMatchedAssets(String keyword, String assetType, String market) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        List<AssetMaster> assets = assetAliasRepository.searchActiveAssetMatches(
                keyword,
                assetType,
                market,
                PageRequest.of(0, ASSET_SEARCH_POOL_SIZE)
        );
        return assets != null ? assets : List.of();
    }

    private Optional<EtfAssetCatalogItem> exactUsTickerFallback(String keyword, String assetType, String market) {
        String normalized = keyword == null ? "" : keyword.trim().toUpperCase(Locale.ROOT);
        if (!(assetType.isBlank() || ASSET_TYPE_STOCK.equals(assetType))) {
            return Optional.empty();
        }
        if (!market.isBlank() && !"ALL".equals(market) && !"US".equals(market) && !isUsMarket(market)) {
            return Optional.empty();
        }
        if (!normalized.matches("[A-Z][A-Z0-9.\\-]{0,9}")) {
            return Optional.empty();
        }
        String symbol = normalized.startsWith("US_") ? normalized.substring(3) : normalized;
        if (symbol.isBlank() || !symbol.matches("[A-Z][A-Z0-9.\\-]{0,9}")) {
            return Optional.empty();
        }
        return Optional.of(new EtfAssetCatalogItem(
                "US_" + symbol,
                symbol,
                symbol,
                "US",
                searchAssetType(ASSET_TYPE_STOCK, symbol, symbol),
                "USD",
                false,
                DATA_STATUS_PENDING,
                "Price data has not been verified"
        ));
    }

    private List<EtfAssetCatalogItem> yahooSearchFallback(String keyword, String assetType, String market, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        if (!assetType.isBlank() && !List.of(
                ASSET_TYPE_STOCK,
                ASSET_TYPE_ETF,
                ASSET_TYPE_LEVERAGED_ETF,
                ASSET_TYPE_INVERSE_ETF
        ).contains(assetType)) {
            return List.of();
        }
        if (!market.isBlank() && !"ALL".equals(market) && !"US".equals(market) && !isUsMarket(market)) {
            return List.of();
        }
        return yahooAssetSearchClient.searchUsEquities(keyword, limit).stream()
                .map(result -> new EtfAssetCatalogItem(
                        "US_" + result.symbol(),
                        result.name(),
                        result.symbol(),
                        result.market(),
                        yahooAssetType(result),
                        result.currency(),
                        false,
                        DATA_STATUS_PENDING,
                        "Price data has not been verified"
                ))
                .filter(item -> assetType.isBlank() || assetType.equals(item.assetType()))
                .toList();
    }

    private String yahooAssetType(YahooAssetSearchClient.YahooAssetResult result) {
        if (!"ETF".equals(safeUpper(result.quoteType()))) {
            return searchAssetType(ASSET_TYPE_STOCK, result.symbol(), result.name());
        }
        return searchAssetType(ASSET_TYPE_ETF, result.symbol(), result.name());
    }

    private String searchAssetType(String assetType, String symbol, String name) {
        String normalizedType = safeUpper(assetType);
        if (!ASSET_TYPE_STOCK.equals(normalizedType)
                && !ASSET_TYPE_ETF.equals(normalizedType)
                && !ASSET_TYPE_LEVERAGED_ETF.equals(normalizedType)
                && !ASSET_TYPE_INVERSE_ETF.equals(normalizedType)) {
            return assetType;
        }
        String normalizedSymbol = safeUpper(symbol);
        String haystack = (normalizedSymbol + " " + safeUpper(name)).trim();
        boolean inverse = containsAny(haystack, "SHORT", "INVERSE", "BEAR", "ULTRASHORT")
                || KNOWN_INVERSE_ETF_SYMBOLS.contains(normalizedSymbol);
        boolean leveraged = containsAny(haystack, "2X", "3X", "ULTRA", "BULL", "LEVERAGED")
                || KNOWN_LEVERAGED_ETF_SYMBOLS.contains(normalizedSymbol);
        boolean etf = ASSET_TYPE_ETF.equals(normalizedType)
                || ASSET_TYPE_LEVERAGED_ETF.equals(normalizedType)
                || ASSET_TYPE_INVERSE_ETF.equals(normalizedType)
                || containsAny(haystack, " ETF", " ETN")
                || KNOWN_ETF_SYMBOLS.contains(normalizedSymbol)
                || leveraged
                || inverse;
        if (inverse) {
            return ASSET_TYPE_INVERSE_ETF;
        }
        if (leveraged) {
            return ASSET_TYPE_LEVERAGED_ETF;
        }
        return etf ? ASSET_TYPE_ETF : ASSET_TYPE_STOCK;
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private CustomEtfAssetSearchItemDTO toAssetSearchItem(EtfAssetCatalogItem item, CachedPriceCoverage coverage) {
        ResolvedStockVisual visual = resolveStockVisual(item.market(), item.symbol(), item.name());
        boolean selectable = isSearchSelectableAsset(item, coverage);
        String dataStatus = searchDataStatus(item, coverage);
        return CustomEtfAssetSearchItemDTO.builder()
                .assetId(item.assetId())
                .stockId(item.assetId())
                .name(item.name())
                .symbol(item.symbol())
                .market(item.market())
                .assetType(item.assetType())
                .currency(item.currency())
                .backtestEnabled(selectable)
                .dataStatus(dataStatus)
                .dataStatusMessage(searchDataStatusMessage(item, dataStatus, coverage))
                .priceCoverage1Y(priceCoverageDto("1Y", item, coverage))
                .priceCoverage3Y(priceCoverageDto("3Y", item, coverage))
                .priceCoverage5Y(priceCoverageDto("5Y", item, coverage))
                .logoUrl(visual.logoUrl())
                .visual(visual.visual())
                .build();
    }

    private String searchDataStatus(EtfAssetCatalogItem item, CachedPriceCoverage coverage) {
        if (PRICE_COVERAGE_READY.equals(priceCoverageStatus("1Y", item, coverage))) {
            return DATA_STATUS_VERIFIED;
        }
        if (DATA_STATUS_PRICE_UNAVAILABLE.equals(item.priceSourceStatus())) {
            return DATA_STATUS_PRICE_UNAVAILABLE;
        }
        return DATA_STATUS_PENDING;
    }

    private String searchDataStatusMessage(EtfAssetCatalogItem item, String dataStatus, CachedPriceCoverage coverage) {
        if (DATA_STATUS_VERIFIED.equals(dataStatus)) {
            return null;
        }
        if (DATA_STATUS_PRICE_UNAVAILABLE.equals(dataStatus)) {
            return blankToDefault(item.lastPriceError(), priceCoverageMessage(PRICE_COVERAGE_UNAVAILABLE));
        }
        return PENDING_VERIFICATION_MESSAGE;
    }

    private Map<String, CachedPriceCoverage> cachedPriceCoverageByAssetId(Set<String> assetIds) {
        if (assetIds.isEmpty()) {
            return Map.of();
        }
        List<String> orderedAssetIds = new ArrayList<>(assetIds);
        List<AssetPriceDailyRepository.AssetPriceCoverageSummary> summaries =
                assetPriceDailyRepository.findCoverageSummariesByAssetIds(orderedAssetIds);
        if (summaries == null || summaries.isEmpty()) {
            return Map.of();
        }
        Map<String, CachedPriceCoverage> coverageByAssetId = new LinkedHashMap<>();
        for (AssetPriceDailyRepository.AssetPriceCoverageSummary summary : summaries) {
            CachedPriceCoverage coverage = toCachedPriceCoverage(summary);
            coverageByAssetId.put(coverage.assetId(), coverage);
        }
        return coverageByAssetId;
    }

    private CachedPriceCoverage cachedPriceCoverageForAsset(String assetId) {
        if (assetId == null || assetId.isBlank()) {
            return CachedPriceCoverage.empty(assetId);
        }
        Optional<AssetPriceDailyRepository.AssetPriceCoverageSummary> summary =
                assetPriceDailyRepository.findCoverageSummaryByAssetId(assetId);
        return summary == null
                ? CachedPriceCoverage.empty(assetId)
                : summary.map(this::toCachedPriceCoverage).orElse(CachedPriceCoverage.empty(assetId));
    }

    private CachedPriceCoverage toCachedPriceCoverage(AssetPriceDailyRepository.AssetPriceCoverageSummary summary) {
        return new CachedPriceCoverage(
                summary.getAssetId(),
                summary.getFirstTradeDate(),
                summary.getLastTradeDate(),
                summary.getPriceCount() == null ? 0L : summary.getPriceCount()
        );
    }

    private CustomEtfPriceCoverageDTO priceCoverageDto(String period,
                                                       EtfAssetCatalogItem item,
                                                       CachedPriceCoverage coverage) {
        String status = priceCoverageStatus(period, item, coverage);
        return CustomEtfPriceCoverageDTO.builder()
                .period(period)
                .status(status)
                .availableFrom(coverage.firstTradeDate())
                .availableTo(coverage.lastTradeDate())
                .priceCount(coverage.priceCount())
                .message(priceCoverageMessage(status))
                .build();
    }

    private String priceCoverageStatus(String period, EtfAssetCatalogItem item, CachedPriceCoverage coverage) {
        if (isPriceCoverageReady(period, coverage)) {
            return PRICE_COVERAGE_READY;
        }
        if (coverage.hasAnyPriceHistory()) {
            return PRICE_COVERAGE_PARTIAL;
        }
        if (DATA_STATUS_PRICE_UNAVAILABLE.equals(item.priceSourceStatus())) {
            return PRICE_COVERAGE_UNAVAILABLE;
        }
        return PRICE_COVERAGE_PENDING;
    }

    private boolean isPriceCoverageReady(String period, CachedPriceCoverage coverage) {
        if (!coverage.hasAnyPriceHistory()) {
            return false;
        }
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = startDateForPeriod(period, endDate);
        return coverageRatio(startDate, endDate, coverage.firstTradeDate(), coverage.lastTradeDate()) >= MIN_PERIOD_COVERAGE_RATIO;
    }

    private String priceCoverageMessage(String status) {
        return switch (status) {
            case PRICE_COVERAGE_READY -> null;
            case PRICE_COVERAGE_PARTIAL -> "일부 가격 캐시가 있어 분석 시 기간이 줄어들 수 있습니다.";
            case PRICE_COVERAGE_UNAVAILABLE -> "가격 데이터가 부족해 분석할 수 없습니다.";
            default -> PENDING_VERIFICATION_MESSAGE;
        };
    }

    private ResolvedStockVisual resolveStockVisual(String market, String symbol, String name) {
        StockVisualDTO visual = stockVisualAssetResolver.resolve(market, symbol, name, null);
        return new ResolvedStockVisual(stockSymbolLogoUrlResolver.resolve(market, symbol, visual), visual);
    }

    private boolean isSearchVisibleAsset(EtfAssetCatalogItem item) {
        return isEquityLikeAssetType(item.assetType());
    }

    private boolean isCustomEtfSelectableAsset(EtfAssetCatalogItem item, CachedPriceCoverage coverage) {
        return isEquityLikeAssetType(item.assetType())
                && !PRICE_COVERAGE_UNAVAILABLE.equals(priceCoverageStatus("1Y", item, coverage));
    }

    private boolean isSearchSelectableAsset(EtfAssetCatalogItem item, CachedPriceCoverage coverage) {
        return isCustomEtfSelectableAsset(item, coverage);
    }

    private boolean isBacktestEligible(EtfAssetCatalogItem item) {
        return isEquityLikeAssetType(item.assetType())
                || Boolean.TRUE.equals(item.backtestEnabled())
                || isProxyAssetType(item.assetType());
    }

    private boolean normalizeBacktestEnabled(AssetMaster asset) {
        if (isProxyAssetType(asset.getAssetType())) {
            return true;
        }
        return Boolean.TRUE.equals(asset.getBacktestEnabled());
    }

    private String normalizeDataStatus(AssetMaster asset) {
        if (asset.getPriceSourceStatus() != null && !asset.getPriceSourceStatus().isBlank()) {
            return asset.getPriceSourceStatus();
        }
        if (isProxyAssetType(asset.getAssetType())) {
            return DATA_STATUS_PROXY;
        }
        return Boolean.TRUE.equals(asset.getBacktestEnabled()) ? DATA_STATUS_VERIFIED : DATA_STATUS_PENDING;
    }

    private boolean isProxyAssetType(String assetType) {
        return ASSET_TYPE_BOND.equals(assetType) || ASSET_TYPE_CASH.equals(assetType);
    }

    private boolean matchesAssetSearch(EtfAssetCatalogItem item, String keyword, String assetType, String market) {
        if (!assetType.isBlank() && !assetType.equals(item.assetType())) {
            return false;
        }
        if (!market.isBlank() && !"ALL".equals(market) && !matchesMarketFilter(item.market(), market)) {
            return false;
        }
        String needle = normalizeSearch(keyword);
        return needle.isBlank()
                || containsSearch(item.assetId(), needle)
                || containsSearch(item.name(), needle)
                || containsSearch(item.symbol(), needle)
                || containsSearch(item.market(), needle)
                || containsSearch(item.assetType(), needle)
                || containsSearch(item.currency(), needle);
    }

    private boolean matchesMarketFilter(String itemMarket, String marketFilter) {
        String market = itemMarket == null ? "" : itemMarket.trim().toUpperCase(Locale.ROOT);
        if ("KRX".equals(marketFilter)) {
            return "KRX".equals(market) || "KOSPI".equals(market) || "KOSDAQ".equals(market);
        }
        if ("US".equals(marketFilter)) {
            return isUsMarket(market);
        }
        return marketFilter.equals(market);
    }

    private String normalizeAssetType(String assetTypeParam) {
        String assetType = assetTypeParam == null ? "" : assetTypeParam.trim().toUpperCase(Locale.ROOT);
        if (assetType.isBlank() || "ALL".equals(assetType)) {
            return "";
        }
        if (!isEquityLikeAssetType(assetType)) {
            throw new ApiException("assetType must be STOCK, ETF, LEVERAGED_ETF, or INVERSE_ETF", HttpStatus.BAD_REQUEST);
        }
        return assetType;
    }

    private boolean isEquityLikeAssetType(String assetType) {
        return List.of(
                ASSET_TYPE_STOCK,
                ASSET_TYPE_ETF,
                ASSET_TYPE_LEVERAGED_ETF,
                ASSET_TYPE_INVERSE_ETF
        ).contains(assetType);
    }

    private String buildStockAssetId(String market, String code) {
        if (isDomesticMarket(market)) {
            return "KRX_" + code;
        }
        if (isUsMarket(market)) {
            return "US_" + code;
        }
        String normalizedMarket = market == null || market.isBlank()
                ? "STOCK"
                : market.trim().toUpperCase(Locale.ROOT);
        return normalizedMarket + "_" + code;
    }

    private boolean matchesDiscoveryQuery(ManagedEtf etf, String query) {
        String needle = normalizeSearch(query);
        if (needle.isBlank()) {
            return true;
        }
        if (containsSearch(etf.getTitle(), needle)
                || containsSearch(etf.getShortDescription(), needle)
                || containsSearch(etf.getTheme(), needle)
                || containsSearch(etf.getRiskLevel(), needle)) {
            return true;
        }
        for (String tag : parseTags(etf.getAnalysisSummaryJson())) {
            if (containsSearch(tag, needle)) {
                return true;
            }
        }
        for (HoldingPayload holding : readHoldings(etf.getHoldingsJson())) {
            StockRef stock = resolveStock(holding.stockId());
            if (containsSearch(holding.stockId(), needle)
                    || containsSearch(stock.name(), needle)
                    || containsSearch(stock.symbol(), needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSearch(String value, String needle) {
        return value != null && normalizeSearch(value).contains(needle);
    }

    private String normalizeSearch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String timestamp(LocalDateTime primary, LocalDateTime fallback) {
        LocalDateTime value = primary != null ? primary : fallback;
        return value == null ? java.time.OffsetDateTime.now(ZoneOffset.UTC).toString() : value.atOffset(ZoneOffset.UTC).toString();
    }

    private EtfAnalysisReportResponseDTO buildReport(String reportId,
                                                     ManagedEtf etf,
                                                     String period,
                                                     String benchmark,
                                                     BigDecimal principalAmount,
                                                     String rebalancePolicy) {
        List<HoldingPayload> holdings = readHoldings(etf.getHoldingsJson());
        if (holdings.isEmpty()) {
            throw new ApiException("ETF holdings are required for analysis", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        validateBacktestEligibleHoldings(holdings);
        LocalDate endDate = LocalDate.now();
        LocalDate requestedStartDate = startDateForPeriod(period, endDate);
        List<BacktestHolding> backtestHoldings = holdings.stream()
                .map(holding -> {
                    StockRef ref = resolveStock(holding.stockId());
                    return new BacktestHolding(
                            holding.stockId(),
                            ref.name(),
                            BigDecimal.valueOf(holding.weight()),
                            holdingTheme(ref, etf.getTheme())
                    );
                })
                .toList();
        BacktestPriceSeries priceSeries = fetchBacktestPriceSeries(
                holdings,
                requestedStartDate,
                endDate,
                period,
                benchmark
        );
        String actualPeriod = priceSeries.actualPeriod();

        BacktestResult backtestResult;
        try {
            backtestResult = etfBacktestEngine.run(BacktestRequest.builder()
                    .principalAmountKrw(principalAmount)
                    .transactionFeeRate(TRANSACTION_FEE_RATE)
                    .slippageRate(SLIPPAGE_RATE)
                    .rebalancePolicy(rebalancePolicy)
                    .periodLabel(periodLabel(actualPeriod))
                    .benchmarkName(benchmarkDisplayName(benchmark))
                    .holdings(backtestHoldings)
                    .priceSeriesBySecurityId(priceSeries.priceSeriesBySecurityId())
                    .benchmarkSeries(priceSeries.benchmarkSeries())
                    .build());
        } catch (IllegalArgumentException e) {
            throw new ApiException(e.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY);
        }
        InsightFacts facts = etfAiFeedbackService.buildInsightFacts(
                etf.getTitle(),
                periodLabel(actualPeriod),
                benchmarkDisplayName(benchmark),
                backtestResult,
                backtestHoldings,
                newsExposure(backtestHoldings)
        );
        EtfAiFeedbackService.FeedbackBuildResult feedbackResult = etfAiFeedbackService.buildFeedbackResult(facts);
        if (feedbackResult == null) {
            RuleBasedFeedback legacyFeedback = etfAiFeedbackService.buildFeedback(facts);
            if (legacyFeedback == null) {
                legacyFeedback = etfAiFeedbackService.buildFallbackFeedback(facts);
            }
            feedbackResult = new EtfAiFeedbackService.FeedbackBuildResult(
                    legacyFeedback,
                    etfAiFeedbackService.modelName(),
                    legacyFeedback != null && legacyFeedback.usedFallback() ? "legacy_fallback" : "legacy_accepted"
            );
        }
        RuleBasedFeedback feedback = feedbackResult.feedback();

        return EtfAnalysisReportResponseDTO.builder()
                .reportId(reportId)
                .etfId(etf.getEtfCode())
                .period(actualPeriod)
                .benchmark(benchmark)
                .highlights(EtfAnalysisHighlightsDTO.builder()
                        .returnRate(toDouble(backtestResult.totalReturnPercent()))
                        .benchmarkExcessReturn(toDouble(backtestResult.excessReturnPercent()))
                        .volatility(toDouble(backtestResult.volatilityPercent()))
                        .maxDrawdown(toDouble(backtestResult.maxDrawdownPercent()))
                        .annualizedReturn(toDouble(backtestResult.annualizedReturnPercent()))
                        .benchmarkReturn(toDouble(backtestResult.benchmarkReturnPercent()))
                        .sharpeRatio(toDouble(backtestResult.sharpeRatio()))
                        .sortinoRatio(toDouble(backtestResult.sortinoRatio()))
                        .beta(toDouble(backtestResult.beta()))
                        .trackingError(toDouble(backtestResult.trackingErrorPercent()))
                        .informationRatio(toDouble(backtestResult.informationRatio()))
                        .winRate(toDouble(backtestResult.winRatePercent()))
                        .benchmarkAnnualizedReturn(toDouble(backtestResult.benchmarkAnnualizedReturnPercent()))
                        .benchmarkVolatility(toDouble(backtestResult.benchmarkVolatilityPercent()))
                        .benchmarkMaxDrawdown(toDouble(backtestResult.benchmarkMaxDrawdownPercent()))
                        .build())
                .cumulativeProfit(EtfAnalysisCumulativeProfitDTO.builder()
                        .amount(toKrwInt(backtestResult.profitAmountKrw()))
                        .series(backtestResult.navSeries().stream()
                                .map(point -> seriesPoint(point.date(), toKrwInt(point.valueKrw())))
                                .toList())
                        .build())
                .riskDiagnosis(EtfAnalysisRiskDiagnosisDTO.builder()
                        .summary(feedback.summary())
                        .riskGrade(backtestResult.riskGrade())
                        .riskGradeLabel(backtestResult.riskGradeLabel())
                        .riskScore(backtestResult.riskScore())
                        .positiveFacts(facts.positiveFacts())
                        .riskFacts(facts.riskFacts())
                        .build())
                .allocation(EtfAnalysisAllocationDTO.builder()
                        .items(holdings.stream()
                                .map(holding -> {
                                    StockRef ref = resolveStock(holding.stockId());
                                    ResolvedStockVisual visual = resolveStockVisual(ref.market(), ref.symbol(), ref.name());
                                    return EtfAnalysisAllocationItemDTO.builder()
                                            .securityId(holding.stockId())
                                            .name(ref.name())
                                            .symbol(ref.symbol())
                                            .market(ref.market())
                                            .assetType(ref.assetType())
                                            .currency(ref.currency())
                                            .weight(holding.weight())
                                            .logoUrl(visual.logoUrl())
                                            .visual(visual.visual())
                                            .build();
                                })
                                .toList())
                        .build())
                .aiFeedback(toAiFeedbackDto(feedback))
                .metadata(EtfAnalysisBacktestMetadataDTO.builder()
                        .analysisVersion(ANALYSIS_VERSION)
                        .messageVersion(MESSAGE_VERSION)
                        .rebalancePolicy(rebalancePolicy)
                        .rebalanceIntervalMonths(rebalanceIntervalMonths(rebalancePolicy))
                        .transactionFeeRate(TRANSACTION_FEE_RATE.doubleValue())
                        .slippageRate(SLIPPAGE_RATE.doubleValue())
                        .principalAmountKrw(principalAmount.setScale(0, RoundingMode.HALF_UP).longValue())
                        .tradingDays(backtestResult.tradingDays())
                        .priceFrequency("MONTH_END")
                        .shareRoundingPolicy("INTEGER_FLOOR")
                        .dividendConsidered(Boolean.FALSE)
                        .marketScope("GLOBAL_STOCK_AND_ETF")
                        .usedFallbackMessage(feedback.usedFallback())
                        .requestedPeriod(period)
                        .actualPeriod(actualPeriod)
                        .priceDataStartDate(dateToString(priceSeries.commonStartDate()))
                        .priceDataEndDate(dateToString(priceSeries.commonEndDate()))
                        .periodDowngraded(priceSeries.periodDowngraded())
                        .dataWarnings(priceSeries.warnings())
                        .priceSource(PRICE_SOURCE)
                        .priceCachePolicy(PRICE_CACHE_POLICY)
                        .fxCachePolicy(FX_CACHE_POLICY)
                        .assumptions(backtestAssumptions(rebalancePolicy))
                        .limitations(backtestLimitations())
                        .llmModel(etfAiFeedbackService.modelName())
                        .promptVersion(etfAiFeedbackService.promptVersion())
                        .llmStatus(feedbackResult.llmStatus())
                        .llmFallbackReason(feedbackResult.fallbackReason())
                        .build())
                .insightFacts(OBJECT_MAPPER.convertValue(facts, MAP_TYPE))
                .analysisPacket(buildAnalysisPacket(
                        etf,
                        actualPeriod,
                        benchmark,
                        principalAmount,
                        rebalancePolicy,
                        backtestResult,
                        backtestHoldings,
                        priceSeries
                ))
                .createdAt(java.time.OffsetDateTime.now(ZoneOffset.UTC).toString())
                .build();
    }

    private Map<String, Object> buildAnalysisPacket(ManagedEtf etf,
                                                    String actualPeriod,
                                                    String benchmark,
                                                    BigDecimal principalAmount,
                                                    String rebalancePolicy,
                                                    BacktestResult result,
                                                    List<BacktestHolding> holdings,
                                                    BacktestPriceSeries priceSeries) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("portfolio_summary", Map.of(
                "portfolio_name", Optional.ofNullable(etf.getTitle()).orElse("나만의 ETF"),
                "analysis_start_date", dateToString(priceSeries.commonStartDate()),
                "analysis_end_date", dateToString(priceSeries.commonEndDate()),
                "period", actualPeriod,
                "rebalance_interval_months", rebalanceIntervalMonths(rebalancePolicy),
                "benchmark", benchmark,
                "holding_count", holdings.size(),
                "initial_capital", principalAmount.setScale(0, RoundingMode.HALF_UP).longValue(),
                "dividend_considered", false,
                "market_scope", "GLOBAL_STOCK_AND_ETF"
        ));
        packet.put("summary_metrics", nullableMap(
                "cumulative_return", toRatioDouble(result.totalReturnPercent()),
                "benchmark_cumulative_return", toRatioDouble(result.benchmarkReturnPercent()),
                "cagr", toRatioDouble(result.annualizedReturnPercent()),
                "benchmark_cagr", toRatioDouble(result.benchmarkAnnualizedReturnPercent()),
                "annualized_volatility", toRatioDouble(result.volatilityPercent()),
                "benchmark_annualized_volatility", toRatioDouble(result.benchmarkVolatilityPercent()),
                "sharpe_ratio", toDouble(result.sharpeRatio()),
                "sortino_ratio", toDouble(result.sortinoRatio()),
                "max_drawdown", toRatioDouble(result.maxDrawdownPercent()),
                "benchmark_max_drawdown", toRatioDouble(result.benchmarkMaxDrawdownPercent()),
                "beta", toDouble(result.beta()),
                "tracking_error", toRatioDouble(result.trackingErrorPercent()),
                "information_ratio", toDouble(result.informationRatio()),
                "win_rate", toRatioDouble(result.winRatePercent())
        ));
        packet.put("concentration", nullableMap(
                "top1_weight", toRatioDouble(result.topHoldingWeightPercent()),
                "top3_weight", toRatioDouble(result.top3WeightPercent()),
                "top5_weight", toRatioDouble(result.top5WeightPercent()),
                "hhi", toDouble(result.hhi()),
                "effective_holdings", toDouble(result.effectiveHoldings()),
                "cash_weight", toRatioDouble(result.cashWeightPercent())
        ));
        packet.put("latest_holdings", holdings.stream()
                .map(holding -> nullableMap(
                        "security_id", holding.securityId(),
                        "name", holding.name(),
                        "sector", holding.sector(),
                        "target_weight", toRatioDouble(holding.weightPercent())
                ))
                .toList());
        packet.put("sector_exposure", sectorExposure(holdings));
        packet.put("benchmark_comparison", nullableMap(
                "cagr_gap", ratioGap(result.annualizedReturnPercent(), result.benchmarkAnnualizedReturnPercent()),
                "volatility_gap", ratioGap(result.volatilityPercent(), result.benchmarkVolatilityPercent()),
                "max_drawdown_gap", ratioGap(result.maxDrawdownPercent(), result.benchmarkMaxDrawdownPercent())
        ));
        packet.put("data_quality", Map.of(
                "warnings", priceSeries.warnings() != null ? priceSeries.warnings() : List.of(),
                "price_frequency", "MONTH_END",
                "share_rounding_policy", "INTEGER_FLOOR"
        ));
        return packet;
    }

    private List<Map<String, Object>> sectorExposure(List<BacktestHolding> holdings) {
        Map<String, BigDecimal> weightsBySector = holdings.stream()
                .filter(holding -> holding.sector() != null && !holding.sector().isBlank())
                .collect(java.util.stream.Collectors.groupingBy(
                        BacktestHolding::sector,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.reducing(
                                BigDecimal.ZERO,
                                holding -> holding.weightPercent() != null ? holding.weightPercent() : BigDecimal.ZERO,
                                BigDecimal::add
                        )
                ));
        return weightsBySector.entrySet().stream()
                .map(entry -> nullableMap(
                        "sector", entry.getKey(),
                        "weight", toRatioDouble(entry.getValue())
                ))
                .toList();
    }

    private Map<String, Object> nullableMap(Object... keysAndValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length - 1; index += 2) {
            values.put(String.valueOf(keysAndValues[index]), keysAndValues[index + 1]);
        }
        return values;
    }

    private Double ratioGap(BigDecimal leftPercent, BigDecimal rightPercent) {
        if (leftPercent == null || rightPercent == null) {
            return null;
        }
        return toRatioDouble(leftPercent.subtract(rightPercent));
    }

    private Double toRatioDouble(BigDecimal percent) {
        if (percent == null) {
            return null;
        }
        return percent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP).doubleValue();
    }

    private BacktestPriceSeries fetchBacktestPriceSeries(List<HoldingPayload> holdings,
                                                         LocalDate requestedStartDate,
                                                         LocalDate endDate,
                                                         String requestedPeriod,
                                                         String benchmark) {
        List<CompletableFuture<SecurityPriceSeries>> holdingFutures = holdings.stream()
                .map(holding -> fetchPriceAsync(() -> new SecurityPriceSeries(
                        holding.stockId(),
                        historicalPriceProvider.getSecurityPriceSeries(holding.stockId(), requestedStartDate, endDate)
                )))
                .toList();
        CompletableFuture<List<BacktestPricePoint>> benchmarkFuture = fetchPriceAsync(
                () -> historicalPriceProvider.getBenchmarkSeries(benchmark, requestedStartDate, endDate));

        Map<String, List<BacktestPricePoint>> priceSeriesBySecurityId = new LinkedHashMap<>();
        for (CompletableFuture<SecurityPriceSeries> future : holdingFutures) {
            SecurityPriceSeries priceSeries = joinPriceFetch(future);
            List<BacktestPricePoint> cleanSeries = cleanPriceSeries(priceSeries.series());
            if (cleanSeries.size() < 2) {
                String message = "ETF asset has insufficient price data for backtest: " + priceSeries.securityId();
                markBacktestDataUnavailable(priceSeries.securityId(), message);
                throw new ApiException(message,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ERROR_CODE_PRICE_DATA_UNAVAILABLE);
            }
            markBacktestDataVerified(priceSeries.securityId());
            priceSeriesBySecurityId.put(priceSeries.securityId(), cleanSeries);
        }
        List<BacktestPricePoint> benchmarkSeries = cleanPriceSeries(joinPriceFetch(benchmarkFuture));
        if (benchmarkSeries.size() < 2) {
            throw new ApiException("Benchmark has insufficient price data for backtest: " + benchmark,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ERROR_CODE_BENCHMARK_PRICE_DATA_UNAVAILABLE);
        }
        PriceDataCoverage coverage = resolvePriceDataCoverage(
                requestedPeriod,
                endDate,
                priceSeriesBySecurityId,
                benchmarkSeries
        );
        return new BacktestPriceSeries(
                priceSeriesBySecurityId,
                benchmarkSeries,
                coverage.actualPeriod(),
                coverage.commonStartDate(),
                coverage.commonEndDate(),
                coverage.periodDowngraded(),
                coverage.warnings()
        );
    }

    private List<BacktestPricePoint> cleanPriceSeries(List<BacktestPricePoint> series) {
        if (series == null) {
            return List.of();
        }
        return series.stream()
                .filter(point -> point != null && point.date() != null && point.adjustedCloseKrw() != null)
                .filter(point -> point.adjustedCloseKrw().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(BacktestPricePoint::date))
                .toList();
    }

    private PriceDataCoverage resolvePriceDataCoverage(String requestedPeriod,
                                                       LocalDate endDate,
                                                       Map<String, List<BacktestPricePoint>> holdingSeriesBySecurityId,
                                                       List<BacktestPricePoint> benchmarkSeries) {
        Map<String, List<BacktestPricePoint>> allSeries = new LinkedHashMap<>(holdingSeriesBySecurityId);
        allSeries.put("__BENCHMARK__", benchmarkSeries);
        Optional<PriceDataCoverage> coverage = findPriceDataCoverage(requestedPeriod, endDate, allSeries);
        if (coverage.isPresent()) {
            return coverage.get();
        }
        if (findPriceDataCoverage(requestedPeriod, endDate, holdingSeriesBySecurityId).isPresent()) {
            throw new ApiException("Benchmark has insufficient price coverage for requested backtest period.",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ERROR_CODE_BENCHMARK_PRICE_DATA_UNAVAILABLE);
        }
        holdingSeriesBySecurityId.keySet().forEach(assetId ->
                markBacktestDataUnavailable(assetId, "ETF assets have insufficient price coverage for requested backtest period."));
        throw new ApiException("ETF assets have insufficient price coverage for requested backtest period.",
                HttpStatus.UNPROCESSABLE_ENTITY,
                ERROR_CODE_PRICE_DATA_UNAVAILABLE);
    }

    private Optional<PriceDataCoverage> findPriceDataCoverage(String requestedPeriod,
                                                             LocalDate endDate,
                                                             Map<String, List<BacktestPricePoint>> seriesById) {
        if (seriesById.isEmpty() || seriesById.values().stream().anyMatch(series -> series == null || series.size() < 2)) {
            return Optional.empty();
        }
        LocalDate commonStartDate = seriesById.values().stream()
                .map(series -> series.get(0).date())
                .max(LocalDate::compareTo)
                .orElse(null);
        LocalDate commonEndDate = seriesById.values().stream()
                .map(series -> series.get(series.size() - 1).date())
                .min(LocalDate::compareTo)
                .orElse(null);
        if (commonStartDate == null || commonEndDate == null || commonEndDate.isBefore(commonStartDate)) {
            return Optional.empty();
        }
        if ("1Y".equals(requestedPeriod)) {
            return Optional.of(priceDataCoverage(requestedPeriod, "1Y", commonStartDate, commonEndDate));
        }
        for (String candidatePeriod : candidatePeriodsFor(requestedPeriod)) {
            LocalDate candidateStartDate = startDateForPeriod(candidatePeriod, endDate);
            if (coverageRatio(candidateStartDate, endDate, commonStartDate, commonEndDate) >= MIN_PERIOD_COVERAGE_RATIO) {
                return Optional.of(priceDataCoverage(requestedPeriod, candidatePeriod, commonStartDate, commonEndDate));
            }
        }
        return Optional.empty();
    }

    private PriceDataCoverage priceDataCoverage(String requestedPeriod,
                                                String actualPeriod,
                                                LocalDate commonStartDate,
                                                LocalDate commonEndDate) {
        boolean downgraded = !actualPeriod.equals(requestedPeriod);
        List<String> warnings = downgraded
                ? List.of("Requested " + requestedPeriod + " but only " + actualPeriod + " common price history was available.")
                : List.of();
        return new PriceDataCoverage(
                commonStartDate,
                commonEndDate,
                actualPeriod,
                downgraded,
                warnings
        );
    }

    private double coverageRatio(LocalDate candidateStartDate,
                                 LocalDate endDate,
                                 LocalDate commonStartDate,
                                 LocalDate commonEndDate) {
        LocalDate overlapStartDate = commonStartDate.isAfter(candidateStartDate) ? commonStartDate : candidateStartDate;
        LocalDate overlapEndDate = commonEndDate.isBefore(endDate) ? commonEndDate : endDate;
        if (overlapEndDate.isBefore(overlapStartDate)) {
            return 0.0d;
        }
        long requiredDays = Math.max(1L, ChronoUnit.DAYS.between(candidateStartDate, endDate));
        long coveredDays = Math.max(0L, ChronoUnit.DAYS.between(overlapStartDate, overlapEndDate));
        return coveredDays / (double) requiredDays;
    }

    private List<String> candidatePeriodsFor(String requestedPeriod) {
        return switch (requestedPeriod) {
            case "ALL" -> List.of("ALL", "3Y", "1Y");
            case "5Y" -> List.of("5Y", "3Y", "1Y");
            case "3Y" -> List.of("3Y", "1Y");
            default -> List.of("1Y");
        };
    }

    private EtfNewsExposure newsExposure(List<BacktestHolding> holdings) {
        return etfNewsExposureService != null
                ? etfNewsExposureService.summarize(holdings)
                : EtfNewsExposure.empty();
    }

    private <T> CompletableFuture<T> fetchPriceAsync(Supplier<T> supplier) {
        return CompletableFuture
                .supplyAsync(supplier, priceFetchExecutor)
                .orTimeout(PRICE_FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private <T> T joinPriceFetch(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ApiException apiException) {
                throw apiException;
            }
            if (cause instanceof TimeoutException) {
                throw new ApiException("ETF price data fetch timed out. Please try again after price cache is warmed.",
                        HttpStatus.SERVICE_UNAVAILABLE);
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new ApiException("ETF price data fetch failed", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private static ThreadFactory priceFetchThreadFactory() {
        AtomicInteger sequence = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, "etf-price-fetch-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    private BigDecimal resolvePrincipal(Long principalAmountKrw) {
        if (principalAmountKrw == null) {
            return DEFAULT_PRINCIPAL_KRW;
        }
        if (principalAmountKrw <= 0) {
            throw new ApiException("principalAmountKrw must be greater than 0", HttpStatus.BAD_REQUEST);
        }
        return BigDecimal.valueOf(principalAmountKrw);
    }

    private String resolveRebalancePolicy(String rebalancePolicy) {
        String normalized = rebalancePolicy == null || rebalancePolicy.isBlank()
                ? DEFAULT_REBALANCE_POLICY
                : rebalancePolicy.trim().toUpperCase(Locale.ROOT);
        if (normalized.matches("\\d+")) {
            int months = Integer.parseInt(normalized);
            if (months >= 1 && months <= 12) {
                return normalized;
            }
        }
        if (!SUPPORTED_REBALANCE_POLICIES.contains(normalized)) {
            throw new ApiException("rebalancePolicy must be one of MONTHLY, QUARTERLY, SEMI_ANNUAL, NONE, or 1-12", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String resolveAnalysisPeriod(String period) {
        String normalized = safeUpper(period);
        return normalized.isBlank() ? DEFAULT_ANALYSIS_PERIOD : normalized;
    }

    private String resolveAnalysisBenchmark(String benchmark) {
        String normalized = safeUpper(benchmark);
        return normalized.isBlank() ? DEFAULT_ANALYSIS_BENCHMARK : normalized;
    }

    private List<String> backtestAssumptions(String rebalancePolicy) {
        return List.of(
                "월말 기준 가격으로만 포트폴리오 가치를 계산합니다.",
                "목표 비중 배분은 정수 주식 수 내림 기준이며 남는 금액은 현금으로 보유합니다.",
                "배당금, 세금, 거래비용은 반영하지 않습니다.",
                "미국 자산 가격은 가격 일자의 환율 제공자 값을 사용해 KRW로 환산합니다.",
                "리밸런싱 주기: " + rebalanceIntervalMonths(rebalancePolicy) + "개월."
        );
    }

    private int rebalanceIntervalMonths(String rebalancePolicy) {
        String normalized = safeUpper(rebalancePolicy);
        if ("NONE".equals(normalized)) {
            return 0;
        }
        if ("QUARTERLY".equals(normalized)) {
            return 3;
        }
        if ("SEMI_ANNUAL".equals(normalized)) {
            return 6;
        }
        if (normalized.matches("\\d+")) {
            int months = Integer.parseInt(normalized);
            return Math.max(1, Math.min(12, months));
        }
        return 1;
    }

    private List<String> backtestLimitations() {
        return List.of(
                "Custom ETF backtests accept stock and ETF-like assets, including leveraged or inverse ETFs when the source provides prices.",
                "dividend, delisting, tax, liquidity, and intramonth path effects are not modeled separately.",
                "Past performance simulation does not guarantee future returns."
        );
    }

    private LocalDate startDateForPeriod(String period, LocalDate endDate) {
        return switch (period) {
            case "3Y" -> endDate.minusYears(3);
            case "5Y" -> endDate.minusYears(5);
            case "ALL" -> endDate.minusYears(5);
            default -> endDate.minusYears(1);
        };
    }

    private String periodLabel(String period) {
        return switch (period) {
            case "3Y" -> "3년";
            case "5Y" -> "5년";
            case "ALL" -> "전체";
            default -> "1년";
        };
    }

    private String dateToString(LocalDate date) {
        return date != null ? date.toString() : null;
    }

    private String benchmarkDisplayName(String benchmark) {
        return switch (benchmark) {
            case "SP500" -> "S&P 500";
            case "NASDAQ" -> "NASDAQ";
            case "KOSPI" -> "KOSPI";
            default -> benchmark;
        };
    }

    private Double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    private int toKrwInt(BigDecimal value) {
        if (value == null) {
            return 0;
        }
        BigDecimal rounded = value.setScale(0, RoundingMode.HALF_UP);
        if (rounded.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            return Integer.MAX_VALUE;
        }
        if (rounded.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) < 0) {
            return Integer.MIN_VALUE;
        }
        return rounded.intValue();
    }

    private EtfAnalysisAiFeedbackDTO toAiFeedbackDto(RuleBasedFeedback feedback) {
        return EtfAnalysisAiFeedbackDTO.builder()
                .title(feedback.title())
                .summary(feedback.summary())
                .bullets(feedback.bullets() == null ? List.of() : feedback.bullets().stream()
                        .map(bullet -> EtfAnalysisFeedbackBulletDTO.builder()
                                .type(bullet.type())
                                .message(bullet.message())
                                .build())
                        .toList())
                .tone(feedback.tone())
                .disclaimer(feedback.disclaimer())
                .usedFallback(feedback.usedFallback())
                .build();
    }

    private EtfAnalysisSeriesPointDTO seriesPoint(LocalDate date, int value) {
        return EtfAnalysisSeriesPointDTO.builder().date(date.toString()).value(value).build();
    }

    private List<EtfDiscoveryTrendPointDTO> readTrend(String json, String period) {
        List<Map<String, Object>> rows = readMapList(json);
        if (!rows.isEmpty()) {
            return rows.stream()
                    .map(row -> EtfDiscoveryTrendPointDTO.builder()
                            .date(String.valueOf(row.get("date")))
                            .value(row.get("value") instanceof Number n ? n.doubleValue() : 0.0)
                            .build())
                    .toList();
        }
        LocalDate end = LocalDate.now();
        return switch (period) {
            case "3Y" -> List.of(trendPoint(end.minusYears(3), 100.0), trendPoint(end.minusYears(2), 106.0), trendPoint(end.minusYears(1), 112.0), trendPoint(end, 120.0));
            case "5Y" -> List.of(trendPoint(end.minusYears(5), 100.0), trendPoint(end.minusYears(3), 109.0), trendPoint(end.minusYears(1), 118.0), trendPoint(end, 128.0));
            case "ALL" -> List.of(trendPoint(end.minusYears(7), 100.0), trendPoint(end.minusYears(5), 104.0), trendPoint(end.minusYears(3), 111.0), trendPoint(end.minusYears(1), 121.0), trendPoint(end, 133.0));
            default -> List.of(trendPoint(end.minusMonths(12), 100.0), trendPoint(end.minusMonths(9), 104.0), trendPoint(end.minusMonths(6), 108.0), trendPoint(end.minusMonths(3), 115.0), trendPoint(end, 121.0));
        };
    }

    private EtfDiscoveryTrendPointDTO trendPoint(LocalDate date, double value) {
        return EtfDiscoveryTrendPointDTO.builder().date(date.toString()).value(value).build();
    }

    private List<String> parseTags(String json) {
        return readMapList(json).stream()
                .map(row -> row.get("tag"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    private List<Map<String, Object>> readMapList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, MAP_LIST_TYPE);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<HoldingPayload> readHoldings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, HOLDING_LIST_TYPE);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String writeHoldings(List<CustomEtfItemRequestDTO> items) {
        return writeValue(items.stream().map(item -> new HoldingPayload(item.getStockId(), item.getWeight(), null)).toList());
    }

    private String writeValue(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new ApiException("failed to serialize ETF data", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private <T> T readValue(String json, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new ApiException("failed to read ETF data", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void validateItems(List<CustomEtfItemRequestDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new ApiException("items is required", HttpStatus.BAD_REQUEST);
        }
        int totalWeight = 0;
        for (CustomEtfItemRequestDTO item : items) {
            if (item.getStockId() == null || item.getStockId().isBlank()) {
                throw new ApiException("stockId is required", HttpStatus.BAD_REQUEST);
            }
            if (item.getWeight() == null || item.getWeight() < 1 || item.getWeight() > 100) {
                throw new ApiException("weight must be between 1 and 100", HttpStatus.BAD_REQUEST);
            }
            requireSupportedCustomEtfAsset(item.getStockId());
            totalWeight += item.getWeight();
        }
        if (totalWeight != 100) {
            throw new ApiException("total weight must be 100", HttpStatus.BAD_REQUEST);
        }
    }

    private void requireSupportedCustomEtfAsset(String stockId) {
        Optional<EtfAssetCatalogItem> catalogItem = findCatalogItem(stockId);
        if (catalogItem.isPresent()) {
            if (!isEquityLikeAssetType(catalogItem.get().assetType())) {
                throw unsupportedCustomEtfAssetTypeException(catalogItem.get());
            }
            return;
        }
        String normalized = stockId == null ? "" : stockId.trim();
        if (normalized.startsWith("KRX_")) {
            String code = normalized.substring(4);
            if (code.matches("\\d{6}") && stockMasterRepository.findById(code).isPresent()) {
                return;
            }
        }
        if (normalized.matches("\\d{6}") && stockMasterRepository.findById(normalized).isPresent()) {
            return;
        }
        throw new ApiException("Unknown ETF assetId: " + normalized, HttpStatus.BAD_REQUEST);
    }

    private void validateBacktestEligibleHoldings(List<HoldingPayload> holdings) {
        for (HoldingPayload holding : holdings) {
            requireBacktestEligibleAsset(holding.stockId());
        }
    }

    private void requireBacktestEligibleAsset(String stockId) {
        Optional<EtfAssetCatalogItem> catalogItem = findCatalogItem(stockId);
        if (catalogItem.isPresent()) {
            EtfAssetCatalogItem item = catalogItem.get();
            if (!isEquityLikeAssetType(item.assetType())) {
                throw unsupportedCustomEtfAssetTypeException(item);
            }
            return;
        }

        String normalized = stockId == null ? "" : stockId.trim();
        if (normalized.startsWith("KRX_")) {
            String code = normalized.substring(4);
            if (code.matches("\\d{6}") && stockMasterRepository.findById(code).isPresent()) {
                return;
            }
        }
        if (normalized.matches("\\d{6}") && stockMasterRepository.findById(normalized).isPresent()) {
            return;
        }
        throw new ApiException("Unknown ETF assetId: " + normalized, HttpStatus.BAD_REQUEST);
    }

    private boolean isKnownPriceUnavailable(EtfAssetCatalogItem item) {
        return !isBacktestEligible(item) && DATA_STATUS_PRICE_UNAVAILABLE.equals(item.priceSourceStatus());
    }

    private boolean isPendingBacktestVerification(EtfAssetCatalogItem item) {
        return DATA_STATUS_PENDING.equals(item.priceSourceStatus());
    }

    private void markBacktestDataVerified(String assetId) {
        CachedPriceCoverage coverage = cachedPriceCoverageForAsset(assetId);
        EtfAssetCatalogItem coverageItem = new EtfAssetCatalogItem(
                assetId,
                assetId,
                assetId,
                "",
                ASSET_TYPE_STOCK,
                "",
                true,
                DATA_STATUS_PENDING,
                null
        );
        if (!PRICE_COVERAGE_READY.equals(priceCoverageStatus("1Y", coverageItem, coverage))) {
            return;
        }
        assetMasterRepository.findByAssetIdAndActiveTrue(assetId).ifPresent(asset -> {
            if (isEquityLikeAssetType(asset.getAssetType())
                    && (!Boolean.TRUE.equals(asset.getBacktestEnabled())
                    || DATA_STATUS_PENDING.equals(asset.getPriceSourceStatus())
                    || asset.getLastPriceError() != null)) {
                asset.setBacktestEnabled(true);
                asset.setPriceSourceStatus(DATA_STATUS_VERIFIED);
                asset.setLastPriceError(null);
                asset.setLastPriceVerifiedAt(LocalDateTime.now());
                assetMasterRepository.save(asset);
            }
        });
    }

    private void markBacktestDataUnavailable(String assetId, String reason) {
        assetMasterRepository.findByAssetIdAndActiveTrue(assetId).ifPresent(asset -> {
            if (isEquityLikeAssetType(asset.getAssetType())) {
                asset.setBacktestEnabled(false);
                asset.setPriceSourceStatus(DATA_STATUS_PRICE_UNAVAILABLE);
                asset.setLastPriceError(reason);
                asset.setLastPriceVerifiedAt(LocalDateTime.now());
                assetMasterRepository.save(asset);
            }
        });
    }

    private ApiException unsupportedAssetException(EtfAssetCatalogItem item) {
        String message = item.lastPriceError() != null && !item.lastPriceError().isBlank()
                ? item.lastPriceError()
                : "Price data has not been verified";
        return new ApiException("ETF asset is not backtest-enabled: " + item.assetId() + " (" + message + ")",
                HttpStatus.BAD_REQUEST);
    }

    private ApiException unsupportedCustomEtfAssetTypeException(EtfAssetCatalogItem item) {
        return new ApiException("Custom ETF only supports stock and ETF-like assets: " + item.assetId(), HttpStatus.BAD_REQUEST);
    }

    private void validatePeriod(String period) {
        if (!List.of("1Y", "3Y", "5Y", "ALL").contains(period)) {
            throw new ApiException("period must be one of 1Y, 3Y, 5Y, ALL", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateBenchmark(String benchmark) {
        if (!List.of("SP500", "KOSPI", "NASDAQ").contains(benchmark)) {
            throw new ApiException("benchmark must be one of SP500, KOSPI, NASDAQ", HttpStatus.BAD_REQUEST);
        }
    }

    private int sumWeights(List<HoldingPayload> holdings) {
        return holdings.stream().mapToInt(HoldingPayload::weight).sum();
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String safeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String generateEtfCode() {
        return "ETF_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
    }

    private StockRef resolveStock(String stockId) {
        String normalized = stockId == null ? "" : stockId.trim();
        Optional<EtfAssetCatalogItem> catalogItem = findCatalogItem(normalized);
        if (catalogItem.isPresent()) {
            return toStockRef(catalogItem.get());
        }
        String codeCandidate = normalized;
        if (normalized.startsWith("KRX_")) {
            codeCandidate = normalized.substring(4);
        } else if (normalized.startsWith("US_")) {
            String symbol = normalized.substring(3);
            Optional<StockMaster> stock = stockMasterRepository.findById(symbol);
            if (stock.isPresent()) {
                return toStockRef(stock.get());
            }
            return new StockRef(symbol, symbol, "US", ASSET_TYPE_STOCK, "USD");
        }
        if (codeCandidate.matches("\\d{6}")) {
            Optional<StockMaster> stock = stockMasterRepository.findById(codeCandidate);
            if (stock.isPresent()) {
                return toStockRef(stock.get());
            }
        }
        List<StockMaster> searched = stockMasterRepository.findByNameKrIlikeOrderByNameKrAsc(normalized, PageRequest.of(0, 1));
        if (!searched.isEmpty()) {
            return toStockRef(searched.get(0));
        }
        return new StockRef(normalized, normalized, inferMarket(normalized), inferAssetType(normalized), inferCurrency(normalized));
    }

    private String inferMarket(String stockId) {
        String normalized = stockId == null ? "" : stockId.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("KRX_")) {
            return "KRX";
        }
        if (normalized.startsWith("US_")) {
            return "US";
        }
        if (normalized.startsWith("BOND_")) {
            return "BOND";
        }
        if (normalized.startsWith("CASH_")) {
            return "CASH";
        }
        return "UNKNOWN";
    }

    private String inferAssetType(String stockId) {
        String normalized = stockId == null ? "" : stockId.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("BOND_")) {
            return ASSET_TYPE_BOND;
        }
        if (normalized.startsWith("CASH_")) {
            return ASSET_TYPE_CASH;
        }
        return ASSET_TYPE_STOCK;
    }

    private String inferCurrency(String stockId) {
        String normalized = stockId == null ? "" : stockId.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("US_") || normalized.contains("_US_") || normalized.endsWith("_USD")) {
            return "USD";
        }
        return "KRW";
    }

    private StockRef toStockRef(StockMaster stock) {
        String market = stock.getMarket() != null ? stock.getMarket() : "";
        return new StockRef(
                stock.getNameKr(),
                stock.getCode(),
                market,
                ASSET_TYPE_STOCK,
                isUsMarket(market) ? "USD" : "KRW"
        );
    }

    private StockRef toStockRef(EtfAssetCatalogItem item) {
        return new StockRef(item.name(), item.symbol(), item.market(), item.assetType(), item.currency());
    }

    private StockRef toStockRef(AssetMaster asset) {
        return new StockRef(asset.getName(), asset.getSymbol(), asset.getMarket(), asset.getAssetType(), asset.getCurrency());
    }

    private Optional<EtfAssetCatalogItem> findCatalogItem(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        Optional<AssetMaster> byAssetId = assetMasterRepository.findByAssetIdAndActiveTrue(upper);
        if (byAssetId.isPresent()) {
            return byAssetId.map(this::toAssetCatalogItem);
        }
        Optional<AssetMaster> bySymbol = assetMasterRepository.findFirstBySymbolIgnoreCaseAndAssetTypeAndActiveTrue(upper, ASSET_TYPE_STOCK);
        if (bySymbol.isPresent()) {
            return bySymbol.map(this::toAssetCatalogItem);
        }
        if (upper.startsWith("US_")) {
            String symbol = upper.substring(3);
            if (symbol.matches("[A-Z][A-Z0-9.\\-]{0,9}")) {
                return Optional.of(new EtfAssetCatalogItem(
                        upper,
                        symbol,
                        symbol,
                        "US",
                        ASSET_TYPE_STOCK,
                        "USD",
                        false,
                        DATA_STATUS_PENDING,
                        "Price data has not been verified"
                ));
            }
        }
        return Optional.empty();
    }

    private boolean isDomesticMarket(String market) {
        String normalized = market == null ? "" : market.trim().toUpperCase(Locale.ROOT);
        return "KRX".equals(normalized) || "KOSPI".equals(normalized) || "KOSDAQ".equals(normalized);
    }

    private boolean isUsMarket(String market) {
        String normalized = market == null ? "" : market.trim().toUpperCase(Locale.ROOT);
        return "US".equals(normalized) || "NASDAQ".equals(normalized) || "NYSE".equals(normalized) || "AMEX".equals(normalized);
    }

    private String resolveStockId(String name) {
        Optional<EtfAssetCatalogItem> catalogItem = findCatalogItem(name);
        if (catalogItem.isPresent()) {
            return catalogItem.get().assetId();
        }
        List<StockMaster> searched = stockMasterRepository.findByNameKrIlikeOrderByNameKrAsc(name, PageRequest.of(0, 1));
        if (!searched.isEmpty()) {
            return buildStockAssetId(searched.get(0).getMarket(), searched.get(0).getCode());
        }
        return name;
    }

    private record RecommendationContext(List<HoldingPayload> holdings, String title, String theme) {}
    private record RecommendationProfile(String dominantMarket, List<String> keywords, int marketCount, String fingerprint, String portfolioLabel) {}
    private record PortfolioFitRecommendationCandidate(EtfPortfolioFitRecommendationItemDTO item, String bucket) {}
    private record HoldingPayload(String stockId, Integer weight, Double changeRate) {}
    private record StockRef(String name, String symbol, String market, String assetType, String currency) {}
    private record ResolvedStockVisual(String logoUrl, StockVisualDTO visual) {}
    private record SecurityPriceSeries(String securityId, List<BacktestPricePoint> series) {}
    private record CachedPriceCoverage(String assetId,
                                       LocalDate firstTradeDate,
                                       LocalDate lastTradeDate,
                                       long priceCount) {
        private static CachedPriceCoverage empty(String assetId) {
            return new CachedPriceCoverage(assetId, null, null, 0L);
        }

        private boolean hasAnyPriceHistory() {
            return priceCount >= 2L && firstTradeDate != null && lastTradeDate != null;
        }
    }
    private record BacktestPriceSeries(Map<String, List<BacktestPricePoint>> priceSeriesBySecurityId,
                                       List<BacktestPricePoint> benchmarkSeries,
                                       String actualPeriod,
                                       LocalDate commonStartDate,
                                       LocalDate commonEndDate,
                                       boolean periodDowngraded,
                                       List<String> warnings) {}
    private record PriceDataCoverage(LocalDate commonStartDate,
                                     LocalDate commonEndDate,
                                     String actualPeriod,
                                     boolean periodDowngraded,
                                     List<String> warnings) {}
    private record EtfAssetCatalogItem(String assetId,
                                       String name,
                                       String symbol,
                                       String market,
                                       String assetType,
                                       String currency,
                                       Boolean backtestEnabled,
                                       String priceSourceStatus,
                                       String lastPriceError) {}
}
