package com.uniport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.dto.CustomEtfCreateRequestDTO;
import com.uniport.dto.CustomEtfDetailResponseDTO;
import com.uniport.dto.CustomEtfHoldingDTO;
import com.uniport.dto.CustomEtfItemRequestDTO;
import com.uniport.dto.CustomEtfListResponseDTO;
import com.uniport.dto.CustomEtfMutationResponseDTO;
import com.uniport.dto.CustomEtfSummaryDTO;
import com.uniport.dto.CustomEtfUpdateRequestDTO;
import com.uniport.dto.EtfAnalysisAllocationDTO;
import com.uniport.dto.EtfAnalysisAllocationItemDTO;
import com.uniport.dto.EtfAnalysisApplyRequestDTO;
import com.uniport.dto.EtfAnalysisApplyResponseDTO;
import com.uniport.dto.EtfAnalysisCumulativeProfitDTO;
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
import com.uniport.entity.ManagedEtf;
import com.uniport.entity.ManagedEtfAnalysisReport;
import com.uniport.entity.ManagedEtfFavorite;
import com.uniport.entity.StockMaster;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.ManagedEtfAnalysisReportRepository;
import com.uniport.repository.ManagedEtfFavoriteRepository;
import com.uniport.repository.ManagedEtfRepository;
import com.uniport.repository.StockMasterRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class EtfDataService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<HoldingPayload>> HOLDING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST_TYPE = new TypeReference<>() {};

    private final ManagedEtfRepository managedEtfRepository;
    private final ManagedEtfAnalysisReportRepository managedEtfAnalysisReportRepository;
    private final ManagedEtfFavoriteRepository managedEtfFavoriteRepository;
    private final StockMasterRepository stockMasterRepository;

    public EtfDataService(ManagedEtfRepository managedEtfRepository,
                          ManagedEtfAnalysisReportRepository managedEtfAnalysisReportRepository,
                          ManagedEtfFavoriteRepository managedEtfFavoriteRepository,
                          StockMasterRepository stockMasterRepository) {
        this.managedEtfRepository = managedEtfRepository;
        this.managedEtfAnalysisReportRepository = managedEtfAnalysisReportRepository;
        this.managedEtfFavoriteRepository = managedEtfFavoriteRepository;
        this.stockMasterRepository = stockMasterRepository;
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
                .createdAt(saved.getCreatedAt().atOffset(ZoneOffset.UTC).toString())
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
                .updatedAt(saved.getUpdatedAt().atOffset(ZoneOffset.UTC).toString())
                .build();
    }

    @Transactional
    public EtfAnalysisStartResponseDTO analyze(User user, String etfId, EtfAnalysisRequestDTO request) {
        ManagedEtf etf = getRequiredCustomEtf(user, etfId);
        String period = safeUpper(request != null ? request.getPeriod() : null);
        String benchmark = safeUpper(request != null ? request.getBenchmark() : null);
        validatePeriod(period);
        validateBenchmark(benchmark);

        String reportId = "REPORT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        EtfAnalysisReportResponseDTO response = buildReport(reportId, etf, period, benchmark);
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
        return buildReport(reportId, etf, period, report.getBenchmark());
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
                .map(item -> new HoldingPayload(resolveStockId(item.getName()), item.getWeight()))
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

    public EtfDiscoveryResponseDTO getPopularEtfs(String sort, String theme, Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 10 : Math.min(size, 20);
        List<ManagedEtf> all = managedEtfRepository.findAll().stream()
                .filter(etf -> isSourceType(etf, "DISCOVERY"))
                .toList();
        List<EtfDiscoveryItemDTO> filtered = all.stream()
                .filter(etf -> theme == null || theme.isBlank() || theme.equalsIgnoreCase(etf.getTheme()))
                .sorted(resolveComparator(sort))
                .map(etf -> toDiscoveryItem(etf, null))
                .toList();
        int fromIndex = Math.min(safePage * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        return EtfDiscoveryResponseDTO.builder()
                .items(filtered.subList(fromIndex, toIndex))
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
                .subtitle(Optional.ofNullable(etf.getShortDescription()).orElse(""))
                .description(Optional.ofNullable(etf.getShortDescription()).orElse(""))
                .badgeLabel(Optional.ofNullable(etf.getRiskLevel()).orElse("DISCOVERY"))
                .tags(parseTags(etf.getAnalysisSummaryJson()))
                .recentReturnRate3M(etf.getReturnRate() != null ? etf.getReturnRate().doubleValue() : 0.0)
                .riskLevel(Optional.ofNullable(etf.getRiskLevel()).orElse("MEDIUM"))
                .period(safePeriod)
                .favorite(favorite)
                .favoriteCount((int) managedEtfFavoriteRepository.countByEtfCode(etfId))
                .thumbnailUrl(etf.getImageUrl())
                .trend(readTrend(etf.getTrendPointsJson(), safePeriod))
                .holdings(readHoldings(etf.getHoldingsJson()).stream()
                        .map(this::toDiscoveryHolding)
                        .toList())
                .build();
    }

    @Transactional
    public EtfFavoriteResponseDTO favoriteDiscoveryEtf(User user, String etfId, boolean favorite) {
        getRequiredDiscoveryEtf(etfId);
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
                .favoriteCount((int) managedEtfFavoriteRepository.countByEtfCode(etfId))
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
        return CustomEtfHoldingDTO.builder()
                .stockId(item.stockId())
                .name(ref.name())
                .symbol(ref.symbol())
                .weight(item.weight())
                .logoUrl(null)
                .build();
    }

    private EtfDiscoveryDetailHoldingDTO toDiscoveryHolding(HoldingPayload item) {
        StockRef ref = resolveStock(item.stockId());
        return EtfDiscoveryDetailHoldingDTO.builder()
                .name(ref.name())
                .symbol(ref.symbol())
                .weight(item.weight())
                .changeRate(0.0)
                .logoUrl(null)
                .build();
    }

    private EtfDiscoveryItemDTO toDiscoveryItem(ManagedEtf etf, User user) {
        boolean favorite = user != null && user.getId() != null && managedEtfFavoriteRepository.existsByUserIdAndEtfCode(user.getId(), etf.getEtfCode());
        return EtfDiscoveryItemDTO.builder()
                .etfId(etf.getEtfCode())
                .title(etf.getTitle())
                .subtitle(Optional.ofNullable(etf.getShortDescription()).orElse(""))
                .theme(Optional.ofNullable(etf.getTheme()).orElse(""))
                .badgeLabel(Optional.ofNullable(etf.getRiskLevel()).orElse("DISCOVERY"))
                .returnRate3M(etf.getReturnRate() != null ? etf.getReturnRate().doubleValue() : 0.0)
                .followerCount((int) managedEtfFavoriteRepository.countByEtfCode(etf.getEtfCode()))
                .favorite(favorite)
                .thumbnailUrl(etf.getImageUrl())
                .build();
    }

    private Comparator<ManagedEtf> resolveComparator(String sort) {
        if ("POPULAR".equalsIgnoreCase(sort)) {
            return Comparator.comparingLong((ManagedEtf etf) -> managedEtfFavoriteRepository.countByEtfCode(etf.getEtfCode())).reversed();
        }
        return Comparator.comparing((ManagedEtf etf) -> Optional.ofNullable(etf.getReturnRate()).orElse(BigDecimal.ZERO)).reversed();
    }

    private EtfAnalysisReportResponseDTO buildReport(String reportId, ManagedEtf etf, String period, String benchmark) {
        List<HoldingPayload> holdings = readHoldings(etf.getHoldingsJson());
        int weightedScore = holdings.stream().mapToInt(holding -> holding.weight() * holding.weight()).sum();
        double returnRate = Math.round((weightedScore / 250.0) * periodMultiplier(period) * 10.0) / 10.0;
        double benchmarkExcess = Math.round((returnRate - benchmarkBase(benchmark)) * 10.0) / 10.0;
        double volatility = Math.round((12.0 + holdings.size() * 1.4) * 10.0) / 10.0;
        double maxDrawdown = Math.round((-4.0 - holdings.size() * 0.9) * 10.0) / 10.0;
        int finalValue = 1_000_000 + (int) Math.round(returnRate * 12_500);

        return EtfAnalysisReportResponseDTO.builder()
                .reportId(reportId)
                .etfId(etf.getEtfCode())
                .period(period)
                .benchmark(benchmark)
                .highlights(EtfAnalysisHighlightsDTO.builder()
                        .returnRate(returnRate)
                        .benchmarkExcessReturn(benchmarkExcess)
                        .volatility(volatility)
                        .maxDrawdown(maxDrawdown)
                        .build())
                .cumulativeProfit(EtfAnalysisCumulativeProfitDTO.builder()
                        .amount(finalValue)
                        .series(buildSeries(period, finalValue))
                        .build())
                .riskDiagnosis(EtfAnalysisRiskDiagnosisDTO.builder()
                        .summary(buildRiskSummary(holdings.size(), etf.getRiskLevel()))
                        .build())
                .allocation(EtfAnalysisAllocationDTO.builder()
                        .items(holdings.stream()
                                .map(holding -> EtfAnalysisAllocationItemDTO.builder()
                                        .name(resolveStock(holding.stockId()).name())
                                        .weight(holding.weight())
                                        .build())
                                .toList())
                        .build())
                .createdAt(java.time.OffsetDateTime.now(ZoneOffset.UTC).toString())
                .build();
    }

    private List<EtfAnalysisSeriesPointDTO> buildSeries(String period, int finalValue) {
        LocalDate end = LocalDate.now();
        return switch (period) {
            case "3Y" -> List.of(seriesPoint(end.minusYears(3), 1_000_000), seriesPoint(end.minusMonths(18), 1_180_000), seriesPoint(end, finalValue));
            case "5Y" -> List.of(seriesPoint(end.minusYears(5), 1_000_000), seriesPoint(end.minusYears(2), 1_260_000), seriesPoint(end, finalValue));
            case "ALL" -> List.of(seriesPoint(end.minusYears(7), 1_000_000), seriesPoint(end.minusYears(4), 1_220_000), seriesPoint(end.minusYears(2), 1_340_000), seriesPoint(end, finalValue));
            default -> List.of(seriesPoint(end.minusYears(1), 1_000_000), seriesPoint(end.minusMonths(6), 1_090_000), seriesPoint(end, finalValue));
        };
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
        return writeValue(items.stream().map(item -> new HoldingPayload(item.getStockId(), item.getWeight())).toList());
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
            totalWeight += item.getWeight();
        }
        if (totalWeight != 100) {
            throw new ApiException("total weight must be 100", HttpStatus.BAD_REQUEST);
        }
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

    private double periodMultiplier(String period) {
        return switch (period) {
            case "3Y" -> 1.45;
            case "5Y" -> 1.9;
            case "ALL" -> 2.3;
            default -> 1.0;
        };
    }

    private double benchmarkBase(String benchmark) {
        return switch (benchmark) {
            case "NASDAQ" -> 10.5;
            case "KOSPI" -> 6.0;
            default -> 8.0;
        };
    }

    private String buildRiskSummary(int holdingCount, String riskLevel) {
        String normalizedRisk = riskLevel == null || riskLevel.isBlank() ? "MEDIUM" : riskLevel.toUpperCase(Locale.ROOT);
        return "Holdings: " + holdingCount + ", risk level: " + normalizedRisk + ". Diversification and rebalancing are based on the saved ETF composition.";
    }

    private StockRef resolveStock(String stockId) {
        String normalized = stockId == null ? "" : stockId.trim();
        String codeCandidate = normalized;
        if (normalized.startsWith("KRX_")) {
            codeCandidate = normalized.substring(4);
        } else if (normalized.startsWith("US_")) {
            String symbol = normalized.substring(3);
            return new StockRef(symbol, symbol);
        }
        if (codeCandidate.matches("\\d{6}")) {
            Optional<StockMaster> stock = stockMasterRepository.findById(codeCandidate);
            if (stock.isPresent()) {
                return new StockRef(stock.get().getNameKr(), stock.get().getCode());
            }
        }
        List<StockMaster> searched = stockMasterRepository.findByNameKrIlikeOrderByNameKrAsc(normalized, PageRequest.of(0, 1));
        if (!searched.isEmpty()) {
            return new StockRef(searched.get(0).getNameKr(), searched.get(0).getCode());
        }
        return new StockRef(normalized, normalized);
    }

    private String resolveStockId(String name) {
        List<StockMaster> searched = stockMasterRepository.findByNameKrIlikeOrderByNameKrAsc(name, PageRequest.of(0, 1));
        if (!searched.isEmpty()) {
            return searched.get(0).getCode();
        }
        return name;
    }

    private record HoldingPayload(String stockId, Integer weight) {}
    private record StockRef(String name, String symbol) {}
}
