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
import com.uniport.dto.EtfShareRequestDTO;
import com.uniport.dto.EtfShareResponseDTO;
import com.uniport.entity.AssetMaster;
import com.uniport.entity.ManagedEtf;
import com.uniport.entity.ManagedEtfAnalysisReport;
import com.uniport.entity.ManagedEtfFavorite;
import com.uniport.entity.StockMaster;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.AssetMasterRepository;
import com.uniport.repository.AssetAliasRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    private static final BigDecimal TRANSACTION_FEE_RATE = new BigDecimal("0.0005");
    private static final BigDecimal SLIPPAGE_RATE = new BigDecimal("0.0003");
    private static final String DEFAULT_ANALYSIS_PERIOD = "1Y";
    private static final String DEFAULT_ANALYSIS_BENCHMARK = "SP500";
    private static final String DEFAULT_REBALANCE_POLICY = "MONTHLY";
    private static final List<String> SUPPORTED_REBALANCE_POLICIES = List.of("MONTHLY", "QUARTERLY", "SEMI_ANNUAL", "NONE");
    private static final String ANALYSIS_VERSION = "backtest-v1.0.0";
    private static final String MESSAGE_VERSION = "ai-feedback-v1.0.0";
    private static final String PRICE_SOURCE = "asset_price_daily cache -> KIS_DOMESTIC_ADJUSTED_CLOSE + KIS_OVERSEAS_DAILY_PRICE";
    private static final String PRICE_CACHE_POLICY = "asset_price_daily";
    private static final String FX_CACHE_POLICY = "fx_rate_daily";
    private static final int DEFAULT_ASSET_SEARCH_SIZE = 10;
    private static final int MAX_ASSET_SEARCH_SIZE = 30;
    private static final int ASSET_SEARCH_POOL_SIZE = 200;
    private static final int PRICE_FETCH_POOL_SIZE = 2;
    private static final int PRICE_FETCH_TIMEOUT_SECONDS = 8;
    private static final String ASSET_TYPE_STOCK = "STOCK";
    private static final String ASSET_TYPE_BOND = "BOND";
    private static final String ASSET_TYPE_CASH = "CASH";
    private static final String DATA_STATUS_VERIFIED = "VERIFIED";
    private static final String DATA_STATUS_PROXY = "PROXY";
    private static final String DATA_STATUS_PENDING = "PENDING_VERIFICATION";
    private static final String DATA_STATUS_PRICE_UNAVAILABLE = "PRICE_UNAVAILABLE";

    private final ManagedEtfRepository managedEtfRepository;
    private final ManagedEtfAnalysisReportRepository managedEtfAnalysisReportRepository;
    private final ManagedEtfFavoriteRepository managedEtfFavoriteRepository;
    private final StockMasterRepository stockMasterRepository;
    private final AssetMasterRepository assetMasterRepository;
    private final AssetAliasRepository assetAliasRepository;
    private final HistoricalPriceProvider historicalPriceProvider;
    private final EtfBacktestEngine etfBacktestEngine;
    private final EtfAiFeedbackService etfAiFeedbackService;
    private final StockVisualAssetResolver stockVisualAssetResolver;
    private final ExecutorService priceFetchExecutor = Executors.newFixedThreadPool(PRICE_FETCH_POOL_SIZE, priceFetchThreadFactory());

    public EtfDataService(ManagedEtfRepository managedEtfRepository,
                          ManagedEtfAnalysisReportRepository managedEtfAnalysisReportRepository,
                          ManagedEtfFavoriteRepository managedEtfFavoriteRepository,
                          StockMasterRepository stockMasterRepository,
                          AssetMasterRepository assetMasterRepository,
                          AssetAliasRepository assetAliasRepository,
                          HistoricalPriceProvider historicalPriceProvider,
                          EtfBacktestEngine etfBacktestEngine,
                          EtfAiFeedbackService etfAiFeedbackService,
                          StockVisualAssetResolver stockVisualAssetResolver) {
        this.managedEtfRepository = managedEtfRepository;
        this.managedEtfAnalysisReportRepository = managedEtfAnalysisReportRepository;
        this.managedEtfFavoriteRepository = managedEtfFavoriteRepository;
        this.stockMasterRepository = stockMasterRepository;
        this.assetMasterRepository = assetMasterRepository;
        this.assetAliasRepository = assetAliasRepository;
        this.historicalPriceProvider = historicalPriceProvider;
        this.etfBacktestEngine = etfBacktestEngine;
        this.etfAiFeedbackService = etfAiFeedbackService;
        this.stockVisualAssetResolver = stockVisualAssetResolver;
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

        LinkedHashMap<String, CustomEtfAssetSearchItemDTO> candidates = new LinkedHashMap<>();
        String repositoryAssetType = assetType.isBlank() ? null : assetType;
        String repositoryMarket = market.isBlank() || "ALL".equals(market) ? null : market;
        searchActiveAssets(keyword, repositoryAssetType, repositoryMarket).stream()
                .map(this::toAssetCatalogItem)
                .filter(item -> matchesAssetSearch(item, keyword, assetType, market))
                .filter(this::isSearchVisibleAsset)
                .forEach(item -> candidates.putIfAbsent(item.assetId(), toAssetSearchItem(item)));
        searchAliasMatchedAssets(keyword, repositoryAssetType, repositoryMarket).stream()
                .map(this::toAssetCatalogItem)
                .filter(item -> matchesAssetSearch(item, "", assetType, market))
                .filter(this::isSearchVisibleAsset)
                .forEach(item -> candidates.putIfAbsent(item.assetId(), toAssetSearchItem(item)));

        if (assetType.isBlank() || ASSET_TYPE_STOCK.equals(assetType)) {
            List<StockMaster> stocks = keyword.isBlank()
                    ? stockMasterRepository.findAll(PageRequest.of(0, ASSET_SEARCH_POOL_SIZE)).getContent()
                    : stockMasterRepository.searchForEtfAssetCandidates(keyword, PageRequest.of(0, ASSET_SEARCH_POOL_SIZE));
            stocks.stream()
                    .map(this::toAssetCatalogItem)
                    .filter(item -> matchesAssetSearch(item, keyword, assetType, market))
                    .filter(this::isSearchVisibleAsset)
                    .forEach(item -> candidates.putIfAbsent(item.assetId(), toAssetSearchItem(item)));
        }
        if (candidates.isEmpty()) {
            exactUsTickerFallback(keyword, assetType, market)
                    .filter(this::isSearchVisibleAsset)
                    .ifPresent(item -> candidates.put(item.assetId(), toAssetSearchItem(item)));
        }

        List<CustomEtfAssetSearchItemDTO> all = new ArrayList<>(candidates.values());
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
                .period(period)
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
        String logoUrl = null;
        return CustomEtfHoldingDTO.builder()
                .stockId(item.stockId())
                .name(ref.name())
                .symbol(ref.symbol())
                .market(ref.market())
                .assetType(ref.assetType())
                .currency(ref.currency())
                .weight(item.weight())
                .logoUrl(logoUrl)
                .visual(stockVisualAssetResolver.resolve(ref.market(), ref.symbol(), ref.name(), logoUrl))
                .build();
    }

    private EtfDiscoveryDetailHoldingDTO toDiscoveryHolding(HoldingPayload item) {
        StockRef ref = resolveStock(item.stockId());
        String logoUrl = null;
        return EtfDiscoveryDetailHoldingDTO.builder()
                .name(ref.name())
                .symbol(ref.symbol())
                .market(ref.market())
                .assetType(ref.assetType())
                .currency(ref.currency())
                .weight(item.weight())
                .changeRate(item.changeRate() != null ? item.changeRate() : 0.0)
                .logoUrl(logoUrl)
                .visual(stockVisualAssetResolver.resolve(ref.market(), ref.symbol(), ref.name(), logoUrl))
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
                ASSET_TYPE_STOCK,
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
                asset.getAssetType(),
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
                ASSET_TYPE_STOCK,
                "USD",
                false,
                DATA_STATUS_PENDING,
                "Price data has not been verified"
        ));
    }

    private CustomEtfAssetSearchItemDTO toAssetSearchItem(EtfAssetCatalogItem item) {
        String logoUrl = null;
        boolean selectable = isCustomEtfSelectableAsset(item);
        return CustomEtfAssetSearchItemDTO.builder()
                .assetId(item.assetId())
                .stockId(item.assetId())
                .name(item.name())
                .symbol(item.symbol())
                .market(item.market())
                .assetType(item.assetType())
                .currency(item.currency())
                .backtestEnabled(selectable)
                .dataStatus(item.priceSourceStatus())
                .dataStatusMessage(selectable ? null : item.lastPriceError())
                .logoUrl(logoUrl)
                .visual(stockVisualAssetResolver.resolve(item.market(), item.symbol(), item.name(), logoUrl))
                .build();
    }

    private boolean isSearchVisibleAsset(EtfAssetCatalogItem item) {
        return isCustomEtfSelectableAsset(item);
    }

    private boolean isCustomEtfSelectableAsset(EtfAssetCatalogItem item) {
        return ASSET_TYPE_STOCK.equals(item.assetType()) && !isKnownPriceUnavailable(item);
    }

    private boolean isBacktestEligible(EtfAssetCatalogItem item) {
        return Boolean.TRUE.equals(item.backtestEnabled()) || isProxyAssetType(item.assetType());
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
            return ASSET_TYPE_STOCK;
        }
        if (!ASSET_TYPE_STOCK.equals(assetType)) {
            throw new ApiException("assetType must be STOCK", HttpStatus.BAD_REQUEST);
        }
        return assetType;
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
        LocalDate startDate = startDateForPeriod(period, endDate);
        List<BacktestHolding> backtestHoldings = holdings.stream()
                .map(holding -> {
                    StockRef ref = resolveStock(holding.stockId());
                    return new BacktestHolding(
                            holding.stockId(),
                            ref.name(),
                            BigDecimal.valueOf(holding.weight()),
                            etf.getTheme()
                    );
                })
                .toList();
        BacktestPriceSeries priceSeries = fetchBacktestPriceSeries(holdings, startDate, endDate, benchmark);

        BacktestResult backtestResult;
        try {
            backtestResult = etfBacktestEngine.run(BacktestRequest.builder()
                    .principalAmountKrw(principalAmount)
                    .transactionFeeRate(TRANSACTION_FEE_RATE)
                    .slippageRate(SLIPPAGE_RATE)
                    .rebalancePolicy(rebalancePolicy)
                    .periodLabel(periodLabel(period))
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
                periodLabel(period),
                benchmarkDisplayName(benchmark),
                backtestResult
        );
        RuleBasedFeedback feedback = etfAiFeedbackService.buildFeedback(facts);

        return EtfAnalysisReportResponseDTO.builder()
                .reportId(reportId)
                .etfId(etf.getEtfCode())
                .period(period)
                .benchmark(benchmark)
                .highlights(EtfAnalysisHighlightsDTO.builder()
                        .returnRate(toDouble(backtestResult.totalReturnPercent()))
                        .benchmarkExcessReturn(toDouble(backtestResult.excessReturnPercent()))
                        .volatility(toDouble(backtestResult.volatilityPercent()))
                        .maxDrawdown(toDouble(backtestResult.maxDrawdownPercent()))
                        .annualizedReturn(toDouble(backtestResult.annualizedReturnPercent()))
                        .benchmarkReturn(toDouble(backtestResult.benchmarkReturnPercent()))
                        .sharpeRatio(toDouble(backtestResult.sharpeRatio()))
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
                                    String logoUrl = null;
                                    return EtfAnalysisAllocationItemDTO.builder()
                                            .securityId(holding.stockId())
                                            .name(ref.name())
                                            .symbol(ref.symbol())
                                            .market(ref.market())
                                            .assetType(ref.assetType())
                                            .currency(ref.currency())
                                            .weight(holding.weight())
                                            .visual(stockVisualAssetResolver.resolve(ref.market(), ref.symbol(), ref.name(), logoUrl))
                                            .build();
                                })
                                .toList())
                        .build())
                .aiFeedback(toAiFeedbackDto(feedback))
                .metadata(EtfAnalysisBacktestMetadataDTO.builder()
                        .analysisVersion(ANALYSIS_VERSION)
                        .messageVersion(MESSAGE_VERSION)
                        .rebalancePolicy(rebalancePolicy)
                        .transactionFeeRate(TRANSACTION_FEE_RATE.doubleValue())
                        .slippageRate(SLIPPAGE_RATE.doubleValue())
                        .principalAmountKrw(principalAmount.setScale(0, RoundingMode.HALF_UP).longValue())
                        .tradingDays(backtestResult.tradingDays())
                        .usedFallbackMessage(feedback.usedFallback())
                        .priceSource(PRICE_SOURCE)
                        .priceCachePolicy(PRICE_CACHE_POLICY)
                        .fxCachePolicy(FX_CACHE_POLICY)
                        .assumptions(backtestAssumptions(rebalancePolicy))
                        .limitations(backtestLimitations())
                        .llmModel(etfAiFeedbackService.modelName())
                        .promptVersion(etfAiFeedbackService.promptVersion())
                        .build())
                .insightFacts(OBJECT_MAPPER.convertValue(facts, MAP_TYPE))
                .createdAt(java.time.OffsetDateTime.now(ZoneOffset.UTC).toString())
                .build();
    }

    private BacktestPriceSeries fetchBacktestPriceSeries(List<HoldingPayload> holdings,
                                                         LocalDate startDate,
                                                         LocalDate endDate,
                                                         String benchmark) {
        List<CompletableFuture<SecurityPriceSeries>> holdingFutures = holdings.stream()
                .map(holding -> fetchPriceAsync(() -> new SecurityPriceSeries(
                        holding.stockId(),
                        historicalPriceProvider.getSecurityPriceSeries(holding.stockId(), startDate, endDate)
                )))
                .toList();
        CompletableFuture<List<BacktestPricePoint>> benchmarkFuture = fetchPriceAsync(
                () -> historicalPriceProvider.getBenchmarkSeries(benchmark, startDate, endDate));

        Map<String, List<BacktestPricePoint>> priceSeriesBySecurityId = new LinkedHashMap<>();
        for (CompletableFuture<SecurityPriceSeries> future : holdingFutures) {
            SecurityPriceSeries priceSeries = joinPriceFetch(future);
            if (priceSeries.series() == null || priceSeries.series().size() < 2) {
                throw new ApiException("ETF asset has insufficient price data for backtest: " + priceSeries.securityId(),
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            markBacktestDataVerified(priceSeries.securityId());
            priceSeriesBySecurityId.put(priceSeries.securityId(), priceSeries.series());
        }
        return new BacktestPriceSeries(priceSeriesBySecurityId, joinPriceFetch(benchmarkFuture));
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
        if (!SUPPORTED_REBALANCE_POLICIES.contains(normalized)) {
            throw new ApiException("rebalancePolicy must be one of MONTHLY, QUARTERLY, SEMI_ANNUAL, NONE", HttpStatus.BAD_REQUEST);
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
                "The backtest starts with the requested principal amount in KRW.",
                "The portfolio applies the " + rebalancePolicy + " rebalance policy over available trading dates.",
                "A transaction fee and slippage rate are deducted whenever rebalancing trades are simulated.",
                "US asset prices are converted to KRW using the FX provider rate for each price date."
        );
    }

    private List<String> backtestLimitations() {
        return List.of(
                "Custom ETF backtests accept verified stock assets only.",
                "dividend, split, delisting, tax, and liquidity effects are not separately modeled unless already reflected in the source adjusted price.",
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
            if (!ASSET_TYPE_STOCK.equals(catalogItem.get().assetType())) {
                throw unsupportedCustomEtfAssetTypeException(catalogItem.get());
            }
            if (isKnownPriceUnavailable(catalogItem.get())) {
                throw unsupportedAssetException(catalogItem.get());
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
            if (!ASSET_TYPE_STOCK.equals(item.assetType())) {
                throw unsupportedCustomEtfAssetTypeException(item);
            }
            if (isBacktestEligible(item) || isPendingBacktestVerification(item)) {
                return;
            }
            throw unsupportedAssetException(item);
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
        assetMasterRepository.findByAssetIdAndActiveTrue(assetId).ifPresent(asset -> {
            if (ASSET_TYPE_STOCK.equals(asset.getAssetType())
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

    private ApiException unsupportedAssetException(EtfAssetCatalogItem item) {
        String message = item.lastPriceError() != null && !item.lastPriceError().isBlank()
                ? item.lastPriceError()
                : "Price data has not been verified";
        return new ApiException("ETF asset is not backtest-enabled: " + item.assetId() + " (" + message + ")",
                HttpStatus.BAD_REQUEST);
    }

    private ApiException unsupportedCustomEtfAssetTypeException(EtfAssetCatalogItem item) {
        return new ApiException("Custom ETF only supports STOCK assets: " + item.assetId(), HttpStatus.BAD_REQUEST);
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

    private record HoldingPayload(String stockId, Integer weight, Double changeRate) {}
    private record StockRef(String name, String symbol, String market, String assetType, String currency) {}
    private record SecurityPriceSeries(String securityId, List<BacktestPricePoint> series) {}
    private record BacktestPriceSeries(Map<String, List<BacktestPricePoint>> priceSeriesBySecurityId,
                                       List<BacktestPricePoint> benchmarkSeries) {}
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
