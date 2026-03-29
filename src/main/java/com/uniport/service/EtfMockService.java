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
import com.uniport.dto.EtfDiscoveryItemDTO;
import com.uniport.dto.EtfDiscoveryResponseDTO;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
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

    public EtfAnalysisReportResponseDTO getReport(User user, String reportId) {
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

        List<EtfDiscoveryItemDTO> base = List.of(
                EtfDiscoveryItemDTO.builder().etfId("ETF_900").title("K-푸드 성장주").subtitle("글로벌 유통형").theme("국내").returnRate3M(15.4).followerCount(6200).thumbnailUrl("https://example.com/k-food.png").build(),
                EtfDiscoveryItemDTO.builder().etfId("ETF_901").title("AI 테크").subtitle("성장 집중").theme("기술").returnRate3M(24.8).followerCount(12500).thumbnailUrl("https://example.com/ai-tech.png").build(),
                EtfDiscoveryItemDTO.builder().etfId("ETF_902").title("반도체 밸류체인").subtitle("공급망 핵심").theme("기술").returnRate3M(31.2).followerCount(9100).thumbnailUrl("https://example.com/semiconductor.png").build(),
                EtfDiscoveryItemDTO.builder().etfId("ETF_903").title("배당 귀족").subtitle("워렌버핏 픽").theme("배당").returnRate3M(8.2).followerCount(8400).thumbnailUrl("https://example.com/dividend.png").build(),
                EtfDiscoveryItemDTO.builder().etfId("ETF_904").title("ESG 친환경").subtitle("미래 에너지").theme("ESG").returnRate3M(-2.4).followerCount(3100).thumbnailUrl("https://example.com/esg.png").build()
        );

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
        if (!List.of("1Y", "3Y", "5Y").contains(safeUpper(period))) {
            throw new ApiException("period must be one of 1Y, 3Y, 5Y", HttpStatus.BAD_REQUEST);
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
            default -> 1_245_000;
        };
        double returnRate = switch (period) {
            case "3Y" -> 18.9;
            case "5Y" -> 26.3;
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
            default -> List.of(
                    point(end.minusYears(1), 1_000_000, formatter),
                    point(end.minusMonths(6), 1_140_000, formatter),
                    point(end, finalValue, formatter)
            );
        };
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
}
