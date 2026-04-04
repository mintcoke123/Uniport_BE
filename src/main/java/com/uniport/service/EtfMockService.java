package com.uniport.service;

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
import com.uniport.dto.EtfDiscoveryTrendPointDTO;
import com.uniport.dto.EtfDiscoveryResponseDTO;
import com.uniport.dto.EtfFavoriteResponseDTO;
import com.uniport.dto.EtfShareRequestDTO;
import com.uniport.dto.EtfShareResponseDTO;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class EtfMockService {

    private static final List<StockMeta> DEFAULT_AI_TECH = List.of(
            new StockMeta("US_AAPL", "Apple Inc.", "AAPL", "https://cdn.example.com/aapl.png"),
            new StockMeta("US_TSLA", "Tesla Inc.", "TSLA", "https://cdn.example.com/tsla.png"),
            new StockMeta("US_NVDA", "NVIDIA", "NVDA", "https://cdn.example.com/nvda.png")
    );

    private static final Map<String, StockMeta> STOCK_META = Map.ofEntries(
            Map.entry("US_AAPL", new StockMeta("US_AAPL", "Apple Inc.", "AAPL", "https://cdn.example.com/aapl.png")),
            Map.entry("US_TSLA", new StockMeta("US_TSLA", "Tesla Inc.", "TSLA", "https://cdn.example.com/tsla.png")),
            Map.entry("US_NVDA", new StockMeta("US_NVDA", "NVIDIA", "NVDA", "https://cdn.example.com/nvda.png")),
            Map.entry("US_MSFT", new StockMeta("US_MSFT", "Microsoft Corp.", "MSFT", "https://cdn.example.com/msft.png")),
            Map.entry("US_GOOGL", new StockMeta("US_GOOGL", "Google Alphabet", "GOOGL", "https://cdn.example.com/googl.png")),
            Map.entry("US_AMZN", new StockMeta("US_AMZN", "Amazon.com Inc.", "AMZN", "https://cdn.example.com/amzn.png")),
            Map.entry("KRX_005930", new StockMeta("KRX_005930", "삼성전자", "005930", "https://cdn.example.com/samsung.png")),
            Map.entry("KRX_035720", new StockMeta("KRX_035720", "카카오", "035720", "https://cdn.example.com/kakao.png"))
    );

    private final ConcurrentHashMap<Long, LinkedHashMap<String, CustomEtfState>> customEtfsByUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EtfAnalysisReportState> reports = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, HashSet<String>> favoriteEtfsByUser = new ConcurrentHashMap<>();
    private final AtomicInteger etfSequence = new AtomicInteger(103);
    private final AtomicInteger reportSequence = new AtomicInteger(301);

    public CustomEtfListResponseDTO getCustomEtfs(User user) {
        LinkedHashMap<String, CustomEtfState> etfs = getUserEtfs(user);
        List<CustomEtfSummaryDTO> items = etfs.values().stream()
                .sorted(Comparator.comparing(CustomEtfState::updatedAt).reversed())
                .map(this::toSummary)
                .toList();

        return CustomEtfListResponseDTO.builder()
                .items(items)
                .build();
    }

    public CustomEtfMutationResponseDTO createCustomEtf(User user, CustomEtfCreateRequestDTO request) {
        validateItems(request.getItems());

        String etfId = "ETF_" + etfSequence.getAndIncrement();
        Instant now = Instant.now();
        CustomEtfState state = new CustomEtfState(
                etfId,
                blankToDefault(request.getTitle(), "새 나만의 ETF"),
                normalizeItems(request.getItems()),
                now,
                now,
                null
        );

        getUserEtfs(user).put(etfId, state);
        return toMutationResponse(state, true);
    }

    public CustomEtfDetailResponseDTO getCustomEtf(User user, String etfId) {
        return toDetail(getRequiredEtf(user, etfId));
    }

    public CustomEtfMutationResponseDTO updateCustomEtf(User user, String etfId, CustomEtfUpdateRequestDTO request) {
        CustomEtfState state = getRequiredEtf(user, etfId);
        validateItems(request.getItems());

        state.title = blankToDefault(request.getTitle(), state.title);
        state.items = normalizeItems(request.getItems());
        state.updatedAt = Instant.now();

        return toMutationResponse(state, false);
    }

    public EtfAnalysisStartResponseDTO analyze(User user, String etfId, EtfAnalysisRequestDTO request) {
        CustomEtfState state = getRequiredEtf(user, etfId);
        validatePeriod(request.getPeriod());
        validateBenchmark(request.getBenchmark());

        String reportId = "REPORT_" + reportSequence.getAndIncrement();
        Instant now = Instant.now();
        EtfAnalysisReportResponseDTO response = buildReport(reportId, state, request.getPeriod().toUpperCase(Locale.ROOT), request.getBenchmark().toUpperCase(Locale.ROOT), now);
        EtfAnalysisReportState report = new EtfAnalysisReportState(
                reportId,
                user.getId(),
                state.etfId,
                request.getPeriod().toUpperCase(Locale.ROOT),
                request.getBenchmark().toUpperCase(Locale.ROOT),
                now,
                response
        );
        reports.put(reportId, report);
        state.lastReportId = reportId;

        return EtfAnalysisStartResponseDTO.builder()
                .reportId(reportId)
                .etfId(state.etfId)
                .status("COMPLETED")
                .createdAt(now.toString())
                .build();
    }

    public EtfAnalysisReportResponseDTO getReport(User user, String reportId, String periodOverride) {
        EtfAnalysisReportState report = reports.get(reportId);
        if (report == null) {
            throw new ApiException("ETF analysis report not found", HttpStatus.NOT_FOUND);
        }
        if (!Objects.equals(report.userId(), user.getId())) {
            throw new ApiException("ETF analysis report not found", HttpStatus.NOT_FOUND);
        }

        CustomEtfState state = getRequiredEtf(user, report.etfId);
        if (!Objects.equals(state.etfId, report.etfId)) {
            throw new ApiException("ETF analysis report not found", HttpStatus.NOT_FOUND);
        }
        if (periodOverride != null && !periodOverride.isBlank()) {
            String safePeriod = safeUpper(periodOverride);
            validatePeriod(safePeriod);
            return buildReport(reportId, state, safePeriod, report.benchmark, report.createdAt);
        }
        return report.response;
    }

    public EtfAnalysisApplyResponseDTO applyReport(User user, String etfId, String reportId, EtfAnalysisApplyRequestDTO request) {
        CustomEtfState state = getRequiredEtf(user, etfId);
        EtfAnalysisReportState report = reports.get(reportId);
        if (report == null || !Objects.equals(report.userId(), user.getId()) || !Objects.equals(report.etfId, etfId)) {
            throw new ApiException("ETF analysis report not found", HttpStatus.NOT_FOUND);
        }
        if (request == null || request.getApplyMode() == null || !"REPLACE".equalsIgnoreCase(request.getApplyMode())) {
            throw new ApiException("applyMode must be REPLACE", HttpStatus.BAD_REQUEST);
        }

        state.items = report.response.getAllocation().getItems().stream()
                .map(item -> {
                    StockMeta meta = STOCK_META.values().stream()
                            .filter(stock -> stock.name().equalsIgnoreCase(item.getName()))
                            .findFirst()
                            .orElse(new StockMeta(item.getName().toUpperCase(Locale.ROOT).replace(" ", "_"), item.getName(), item.getName(), null));
                    return new CustomEtfItemState(meta.stockId(), item.getWeight());
                })
                .collect(Collectors.toCollection(ArrayList::new));
        state.updatedAt = Instant.now();

        return EtfAnalysisApplyResponseDTO.builder()
                .etfId(etfId)
                .reportId(reportId)
                .applied(Boolean.TRUE)
                .updatedAt(state.updatedAt.toString())
                .build();
    }

    public EtfDiscoveryResponseDTO getPopularEtfs(String sort, String theme, Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 10 : Math.min(size, 20);

        List<EtfDiscoveryItemDTO> base = discoveryCatalog().stream()
                .map(item -> toDiscoveryItem(item, null))
                .toList();

        List<EtfDiscoveryItemDTO> filtered = base.stream()
                .filter(item -> theme == null || theme.isBlank() || item.getTheme().equalsIgnoreCase(theme))
                .sorted(resolveComparator(sort))
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
        String safePeriod = safeUpper(period == null || period.isBlank() ? "1Y" : period);
        validatePeriod(safePeriod);
        DiscoveryEtfState state = getDiscoveryEtf(etfId);
        boolean favorite = isFavorite(user, etfId);

        return EtfDiscoveryDetailResponseDTO.builder()
                .etfId(state.etfId())
                .title(state.title())
                .subtitle(state.subtitle())
                .description(state.description())
                .badgeLabel(state.badgeLabel())
                .tags(state.tags())
                .recentReturnRate3M(state.returnRate3M())
                .riskLevel(state.riskLevel())
                .period(safePeriod)
                .favorite(favorite)
                .favoriteCount(state.followerCount() + (favorite ? 1 : 0))
                .thumbnailUrl(state.thumbnailUrl())
                .trend(buildDiscoveryTrend(safePeriod, state))
                .holdings(state.holdings().stream()
                        .map(holding -> EtfDiscoveryDetailHoldingDTO.builder()
                                .name(holding.name())
                                .symbol(holding.symbol())
                                .weight(holding.weight())
                                .changeRate(holding.changeRate())
                                .logoUrl(holding.logoUrl())
                                .build())
                        .toList())
                .build();
    }

    public EtfFavoriteResponseDTO favoriteDiscoveryEtf(User user, String etfId, boolean favorite) {
        getDiscoveryEtf(etfId);
        HashSet<String> favorites = favoriteEtfsByUser.computeIfAbsent(user.getId(), ignored -> new HashSet<>());
        if (favorite) {
            favorites.add(etfId);
        } else {
            favorites.remove(etfId);
        }
        DiscoveryEtfState state = getDiscoveryEtf(etfId);
        return EtfFavoriteResponseDTO.builder()
                .etfId(etfId)
                .favorite(favorite)
                .favoriteCount(state.followerCount() + (favorite ? 1 : 0))
                .message(favorite ? "관심 ETF에 추가되었어요." : "관심 ETF에서 제거되었어요.")
                .build();
    }

    public EtfShareResponseDTO shareCustomEtf(User user, String etfId, EtfShareRequestDTO request) {
        CustomEtfState state = getRequiredEtf(user, etfId);
        String targetType = safeUpper(request != null ? request.getTargetType() : null);
        if (!List.of("COMMUNITY", "CHAT").contains(targetType)) {
            throw new ApiException("targetType must be COMMUNITY or CHAT", HttpStatus.BAD_REQUEST);
        }
        if ("CHAT".equals(targetType) && (request == null || request.getRoomId() == null)) {
            throw new ApiException("roomId is required when targetType is CHAT", HttpStatus.BAD_REQUEST);
        }
        String title = "COMMUNITY".equals(targetType)
                ? "커뮤니티에 포트폴리오 공유 준비가 완료되었어요."
                : "채팅방에 포트폴리오 공유 준비가 완료되었어요.";
        String description = "COMMUNITY".equals(targetType)
                ? state.title + " 포트폴리오를 커뮤니티 카드로 노출할 수 있어요."
                : "선택한 채팅방에 " + state.title + " 포트폴리오를 공유할 수 있어요.";
        return EtfShareResponseDTO.builder()
                .etfId(etfId)
                .targetType(targetType)
                .shared(Boolean.TRUE)
                .title(title)
                .description(description)
                .build();
    }

    private Comparator<EtfDiscoveryItemDTO> resolveComparator(String sort) {
        if ("POPULAR".equalsIgnoreCase(sort)) {
            return Comparator.comparing(EtfDiscoveryItemDTO::getFollowerCount).reversed();
        }
        return Comparator.comparing(EtfDiscoveryItemDTO::getReturnRate3M).reversed();
    }

    private LinkedHashMap<String, CustomEtfState> getUserEtfs(User user) {
        return customEtfsByUser.computeIfAbsent(user.getId(), ignored -> seedDefaultEtfs());
    }

    private LinkedHashMap<String, CustomEtfState> seedDefaultEtfs() {
        LinkedHashMap<String, CustomEtfState> seeded = new LinkedHashMap<>();
        Instant now = Instant.now();
        seeded.put("ETF_101", new CustomEtfState(
                "ETF_101",
                "AI 테크",
                new ArrayList<>(List.of(
                        new CustomEtfItemState("US_AAPL", 40),
                        new CustomEtfItemState("US_TSLA", 35),
                        new CustomEtfItemState("US_NVDA", 25)
                )),
                now.minusSeconds(86_400),
                now.minusSeconds(3_600),
                null
        ));
        seeded.put("ETF_102", new CustomEtfState(
                "ETF_102",
                "배당 귀족",
                new ArrayList<>(List.of(
                        new CustomEtfItemState("US_MSFT", 35),
                        new CustomEtfItemState("US_AAPL", 25),
                        new CustomEtfItemState("US_AMZN", 20),
                        new CustomEtfItemState("US_GOOGL", 20)
                )),
                now.minusSeconds(172_800),
                now.minusSeconds(7_200),
                null
        ));
        return seeded;
    }

    private CustomEtfState getRequiredEtf(User user, String etfId) {
        CustomEtfState state = getUserEtfs(user).get(etfId);
        if (state == null) {
            throw new ApiException("Custom ETF not found", HttpStatus.NOT_FOUND);
        }
        return state;
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
        if (!List.of("1Y", "3Y", "5Y", "ALL").contains(safeUpper(period))) {
            throw new ApiException("period must be one of 1Y, 3Y, 5Y, ALL", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateBenchmark(String benchmark) {
        if (!List.of("SP500", "KOSPI", "NASDAQ").contains(safeUpper(benchmark))) {
            throw new ApiException("benchmark must be one of SP500, KOSPI, NASDAQ", HttpStatus.BAD_REQUEST);
        }
    }

    private String safeUpper(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }

    private ArrayList<CustomEtfItemState> normalizeItems(List<CustomEtfItemRequestDTO> items) {
        return items.stream()
                .map(item -> new CustomEtfItemState(item.getStockId(), item.getWeight()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private CustomEtfSummaryDTO toSummary(CustomEtfState state) {
        return CustomEtfSummaryDTO.builder()
                .etfId(state.etfId)
                .title(state.title)
                .thumbnailUrl("https://example.com/" + state.etfId.toLowerCase(Locale.ROOT) + ".png")
                .itemCount(state.items.size())
                .totalWeight(state.items.stream().mapToInt(CustomEtfItemState::weight).sum())
                .updatedAt(state.updatedAt.toString())
                .build();
    }

    private CustomEtfMutationResponseDTO toMutationResponse(CustomEtfState state, boolean created) {
        return CustomEtfMutationResponseDTO.builder()
                .etfId(state.etfId)
                .title(state.title)
                .totalWeight(state.items.stream().mapToInt(CustomEtfItemState::weight).sum())
                .createdAt(created ? state.createdAt.toString() : null)
                .updatedAt(created ? null : state.updatedAt.toString())
                .build();
    }

    private CustomEtfDetailResponseDTO toDetail(CustomEtfState state) {
        return CustomEtfDetailResponseDTO.builder()
                .etfId(state.etfId)
                .title(state.title)
                .items(state.items.stream()
                        .map(item -> {
                            StockMeta meta = STOCK_META.getOrDefault(item.stockId(), new StockMeta(item.stockId(), item.stockId(), item.stockId(), null));
                            return CustomEtfHoldingDTO.builder()
                                    .stockId(item.stockId())
                                    .name(meta.name())
                                    .symbol(meta.symbol())
                                    .weight(item.weight())
                                    .logoUrl(meta.logoUrl())
                                    .build();
                        })
                        .toList())
                .build();
    }

    private EtfAnalysisReportResponseDTO buildReport(String reportId, CustomEtfState state, String period, String benchmark, Instant createdAt) {
        int baseAmount = switch (period) {
            case "3Y" -> 1_680_000;
            case "5Y" -> 2_120_000;
            case "ALL" -> 2_980_000;
            default -> 1_245_000;
        };
        double returnRate = switch (period) {
            case "3Y" -> 18.9;
            case "5Y" -> 26.3;
            case "ALL" -> 34.7;
            default -> 12.4;
        };
        double benchmarkExcessReturn = "NASDAQ".equals(benchmark) ? 1.8 : ("KOSPI".equals(benchmark) ? 5.1 : 3.2);

        List<EtfAnalysisSeriesPointDTO> series = buildSeries(period, baseAmount);

        List<EtfAnalysisAllocationItemDTO> allocationItems = state.items.stream()
                .map(item -> {
                    StockMeta meta = STOCK_META.getOrDefault(item.stockId(), new StockMeta(item.stockId(), item.stockId(), item.stockId(), null));
                    return EtfAnalysisAllocationItemDTO.builder()
                            .name(meta.name())
                            .weight(item.weight())
                            .build();
                })
                .toList();

        return EtfAnalysisReportResponseDTO.builder()
                .reportId(reportId)
                .etfId(state.etfId)
                .period(period)
                .benchmark(benchmark)
                .highlights(EtfAnalysisHighlightsDTO.builder()
                        .returnRate(returnRate)
                        .benchmarkExcessReturn(benchmarkExcessReturn)
                        .volatility(14.2)
                        .maxDrawdown(-8.5)
                        .build())
                .cumulativeProfit(EtfAnalysisCumulativeProfitDTO.builder()
                        .amount(baseAmount)
                        .series(series)
                        .build())
                .riskDiagnosis(EtfAnalysisRiskDiagnosisDTO.builder()
                        .summary("시장 대비 리스크 관리가 원활하며 균형 잡힌 분산 투자가 이루어진 설계입니다.")
                        .build())
                .allocation(EtfAnalysisAllocationDTO.builder()
                        .items(allocationItems)
                        .build())
                .createdAt(createdAt.toString())
                .build();
    }

    private List<EtfAnalysisSeriesPointDTO> buildSeries(String period, int finalValue) {
        LocalDate end = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
        return switch (period) {
            case "3Y" -> List.of(
                    point(end.minusYears(3), 1_000_000, formatter),
                    point(end.minusMonths(18), 1_280_000, formatter),
                    point(end, finalValue, formatter)
            );
            case "5Y" -> List.of(
                    point(end.minusYears(5), 1_000_000, formatter),
                    point(end.minusYears(2), 1_540_000, formatter),
                    point(end, finalValue, formatter)
            );
            case "ALL" -> List.of(
                    point(end.minusYears(7), 1_000_000, formatter),
                    point(end.minusYears(4), 1_420_000, formatter),
                    point(end.minusYears(2), 1_980_000, formatter),
                    point(end, finalValue, formatter)
            );
            default -> List.of(
                    point(end.minusYears(1), 1_000_000, formatter),
                    point(end.minusMonths(6), 1_140_000, formatter),
                    point(end, finalValue, formatter)
            );
        };
    }

    private List<DiscoveryEtfState> discoveryCatalog() {
        return List.of(
                new DiscoveryEtfState(
                        "ETF_900", "K-푸드 성장주", "글로벌 유통형", "국내", "인기",
                        "글로벌 시장에서 성장 중인 K-푸드 브랜드를 중심으로 구성한 ETF입니다.",
                        List.of("K푸드", "리테일", "소비재"),
                        15.4, "중간", 6200, "https://example.com/k-food.png",
                        List.of(
                                new DiscoveryHolding("오리온", "271560", 40, 12.1, "https://cdn.example.com/orion.png"),
                                new DiscoveryHolding("농심", "004370", 35, 8.7, "https://cdn.example.com/nongshim.png"),
                                new DiscoveryHolding("CJ제일제당", "097950", 25, 5.2, "https://cdn.example.com/cj.png")
                        )
                ),
                new DiscoveryEtfState(
                        "ETF_901", "AI 테크", "성장 중심", "기술", "인기",
                        "전 세계 AI 혁명을 주도하는 반도체 및 소프트웨어 핵심 기업 7곳에 집중 투자하는 포트폴리오입니다.",
                        List.of("반도체", "소프트웨어", "LLM", "성장주"),
                        24.8, "높음", 12500, "https://example.com/ai-tech.png",
                        List.of(
                                new DiscoveryHolding("LG에너지솔루션", "005930", 100, 39.0, "https://cdn.example.com/lges.png"),
                                new DiscoveryHolding("엔비디아", "NVDA", 25, 18.4, "https://cdn.example.com/nvda.png"),
                                new DiscoveryHolding("마이크로소프트", "MSFT", 20, 12.2, "https://cdn.example.com/msft.png"),
                                new DiscoveryHolding("알파벳", "GOOGL", 15, 9.5, "https://cdn.example.com/googl.png")
                        )
                ),
                new DiscoveryEtfState(
                        "ETF_902", "반도체 밸류체인", "공급망 핵심", "기술", "급등",
                        "AI 서버와 메모리 수요 확대에 맞춘 반도체 공급망 핵심 기업 포트폴리오입니다.",
                        List.of("반도체", "HBM", "서버"),
                        31.2, "높음", 9100, "https://example.com/semiconductor.png",
                        List.of(
                                new DiscoveryHolding("삼성전자", "005930", 40, 39.0, "https://cdn.example.com/samsung.png"),
                                new DiscoveryHolding("SK하이닉스", "000660", 35, 27.2, "https://cdn.example.com/skhynix.png"),
                                new DiscoveryHolding("ASML", "ASML", 25, 11.3, "https://cdn.example.com/asml.png")
                        )
                ),
                new DiscoveryEtfState(
                        "ETF_903", "배당 귀족", "워렌버핏 픽", "배당", "안정형",
                        "현금흐름이 안정적인 고배당 기업 중심으로 구성한 포트폴리오입니다.",
                        List.of("배당", "현금흐름", "가치주"),
                        8.2, "낮음", 8400, "https://example.com/dividend.png",
                        List.of(
                                new DiscoveryHolding("코카콜라", "KO", 40, 3.1, "https://cdn.example.com/ko.png"),
                                new DiscoveryHolding("존슨앤드존슨", "JNJ", 35, 2.8, "https://cdn.example.com/jnj.png"),
                                new DiscoveryHolding("P&G", "PG", 25, 4.2, "https://cdn.example.com/pg.png")
                        )
                ),
                new DiscoveryEtfState(
                        "ETF_904", "ESG 친환경", "미래 에너지", "ESG", "테마형",
                        "친환경 에너지와 전력 인프라 전환에 맞춘 ESG 테마 포트폴리오입니다.",
                        List.of("ESG", "전력기기", "친환경"),
                        -2.4, "중간", 3100, "https://example.com/esg.png",
                        List.of(
                                new DiscoveryHolding("퍼스트솔라", "FSLR", 35, -1.2, "https://cdn.example.com/fslr.png"),
                                new DiscoveryHolding("넥스트에라", "NEE", 35, 2.3, "https://cdn.example.com/nee.png"),
                                new DiscoveryHolding("HD현대일렉트릭", "267260", 30, 14.5, "https://cdn.example.com/hdelectric.png")
                        )
                )
        );
    }

    private DiscoveryEtfState getDiscoveryEtf(String etfId) {
        return discoveryCatalog().stream()
                .filter(item -> item.etfId().equals(etfId))
                .findFirst()
                .orElseThrow(() -> new ApiException("ETF discovery item not found", HttpStatus.NOT_FOUND));
    }

    private EtfDiscoveryItemDTO toDiscoveryItem(DiscoveryEtfState state, User user) {
        boolean favorite = isFavorite(user, state.etfId());
        return EtfDiscoveryItemDTO.builder()
                .etfId(state.etfId())
                .title(state.title())
                .subtitle(state.subtitle())
                .theme(state.theme())
                .badgeLabel(state.badgeLabel())
                .returnRate3M(state.returnRate3M())
                .followerCount(state.followerCount() + (favorite ? 1 : 0))
                .favorite(favorite)
                .thumbnailUrl(state.thumbnailUrl())
                .build();
    }

    private boolean isFavorite(User user, String etfId) {
        if (user == null || user.getId() == null) {
            return false;
        }
        return favoriteEtfsByUser.getOrDefault(user.getId(), new HashSet<>()).contains(etfId);
    }

    private List<EtfDiscoveryTrendPointDTO> buildDiscoveryTrend(String period, DiscoveryEtfState state) {
        LocalDate end = LocalDate.now();
        return switch (period) {
            case "3Y" -> List.of(
                    trend(end.minusYears(3), 72.0),
                    trend(end.minusYears(2), 91.5),
                    trend(end.minusYears(1), 104.2),
                    trend(end, 128.4 + state.returnRate3M())
            );
            case "5Y" -> List.of(
                    trend(end.minusYears(5), 55.0),
                    trend(end.minusYears(3), 79.3),
                    trend(end.minusYears(1), 112.6),
                    trend(end, 148.8 + state.returnRate3M())
            );
            case "ALL" -> List.of(
                    trend(end.minusYears(7), 43.0),
                    trend(end.minusYears(5), 66.4),
                    trend(end.minusYears(3), 94.5),
                    trend(end.minusYears(1), 118.2),
                    trend(end, 156.2 + state.returnRate3M())
            );
            default -> List.of(
                    trend(end.minusMonths(12), 100.0),
                    trend(end.minusMonths(9), 108.2),
                    trend(end.minusMonths(6), 96.4),
                    trend(end.minusMonths(3), 121.5),
                    trend(end, 124.8 + state.returnRate3M())
            );
        };
    }

    private EtfDiscoveryTrendPointDTO trend(LocalDate date, double value) {
        return EtfDiscoveryTrendPointDTO.builder()
                .date(date.toString())
                .value(value)
                .build();
    }

    private EtfAnalysisSeriesPointDTO point(LocalDate date, int value, DateTimeFormatter formatter) {
        return EtfAnalysisSeriesPointDTO.builder()
                .date(date.format(formatter))
                .value(value)
                .build();
    }

    private record StockMeta(String stockId, String name, String symbol, String logoUrl) {
    }

    private record CustomEtfItemState(String stockId, Integer weight) {
    }

    private static final class CustomEtfState {
        private final String etfId;
        private String title;
        private ArrayList<CustomEtfItemState> items;
        private final Instant createdAt;
        private Instant updatedAt;
        private String lastReportId;

        private CustomEtfState(String etfId,
                               String title,
                               ArrayList<CustomEtfItemState> items,
                               Instant createdAt,
                               Instant updatedAt,
                               String lastReportId) {
            this.etfId = etfId;
            this.title = title;
            this.items = items;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.lastReportId = lastReportId;
        }

        private Instant updatedAt() {
            return updatedAt;
        }
    }

    private record EtfAnalysisReportState(
            String reportId,
            Long userId,
            String etfId,
            String period,
            String benchmark,
            Instant createdAt,
            EtfAnalysisReportResponseDTO response
    ) {
    }

    private record DiscoveryHolding(
            String name,
            String symbol,
            Integer weight,
            Double changeRate,
            String logoUrl
    ) {
    }

    private record DiscoveryEtfState(
            String etfId,
            String title,
            String subtitle,
            String theme,
            String badgeLabel,
            String description,
            List<String> tags,
            Double returnRate3M,
            String riskLevel,
            Integer followerCount,
            String thumbnailUrl,
            List<DiscoveryHolding> holdings
    ) {
    }
}
