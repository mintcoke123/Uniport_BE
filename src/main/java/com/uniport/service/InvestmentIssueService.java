package com.uniport.service;

import com.uniport.dto.InvestmentIssueCategoryDTO;
import com.uniport.dto.InvestmentIssueDetailResponseDTO;
import com.uniport.dto.InvestmentIssueItemDTO;
import com.uniport.dto.InvestmentIssueListResponseDTO;
import com.uniport.dto.InvestmentIssueRelatedEtfDTO;
import com.uniport.dto.InvestmentIssueRelatedStockDTO;
import com.uniport.dto.InvestmentIssueSharePreviewDTO;
import com.uniport.dto.InvestmentIssueShareRequestDTO;
import com.uniport.dto.InvestmentIssueShareResponseDTO;
import com.uniport.dto.InvestmentIssueSourceArticleDTO;
import com.uniport.entity.ChatMessage;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class InvestmentIssueService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISSUE_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_MAX_REASON_BULLETS = 3;
    private static final int DEFAULT_MAX_WATCH_POINTS = 2;
    private static final int DEFAULT_MAX_RELATED_STOCKS = 5;
    private static final int DEFAULT_MAX_RELATED_ETFS = 3;
    private static final String ROOM_ENDED_READ_ONLY_MESSAGE = "종료된 채팅방은 보기만 할 수 있습니다.";
    private static final Pattern NON_SLUG_CHARACTER = Pattern.compile("[^a-z0-9]+");
    private static final Comparator<LocalDateTime> NEWEST_FIRST =
            Comparator.nullsLast(Comparator.reverseOrder());

    private final NewsFeedClient newsFeedClient;
    private final RawNewsDeduplicator rawNewsDeduplicator;
    private final IssueClusterService issueClusterService;
    private final InvestmentIssueAnalyzer investmentIssueAnalyzer;
    private final MatchingRoomMemberRepository matchingRoomMemberRepository;
    private final MatchingRoomRepository matchingRoomRepository;
    private final ChatService chatService;
    private final Duration cacheTtl;
    private final Clock clock;
    private final int maxReasonBullets;
    private final int maxWatchPoints;
    private final int maxRelatedStocks;
    private final int maxRelatedEtfs;

    private CacheEntry issueCache;
    private CacheEntry previousIssueCache;

    @Autowired
    public InvestmentIssueService(NewsFeedClient newsFeedClient,
                                  RawNewsDeduplicator rawNewsDeduplicator,
                                  IssueClusterService issueClusterService,
                                  InvestmentIssueAnalyzer investmentIssueAnalyzer,
                                  MatchingRoomMemberRepository matchingRoomMemberRepository,
                                  MatchingRoomRepository matchingRoomRepository,
                                  ChatService chatService,
                                  @Value("${uniport.investment-issue.cache-ttl-seconds:300}") long cacheTtlSeconds,
                                  @Value("${uniport.investment-issue.display.max-reason-bullets:3}")
                                  int maxReasonBullets,
                                  @Value("${uniport.investment-issue.display.max-watch-points:2}")
                                  int maxWatchPoints,
                                  @Value("${uniport.investment-issue.display.max-related-stocks:5}")
                                  int maxRelatedStocks,
                                  @Value("${uniport.investment-issue.display.max-related-etfs:3}")
                                  int maxRelatedEtfs) {
        this(
                newsFeedClient,
                rawNewsDeduplicator,
                issueClusterService,
                investmentIssueAnalyzer,
                matchingRoomMemberRepository,
                matchingRoomRepository,
                chatService,
                Duration.ofSeconds(Math.max(0, cacheTtlSeconds)),
                Clock.system(DEFAULT_ZONE),
                maxReasonBullets,
                maxWatchPoints,
                maxRelatedStocks,
                maxRelatedEtfs
        );
    }

    InvestmentIssueService(NewsFeedClient newsFeedClient,
                           RawNewsDeduplicator rawNewsDeduplicator,
                           IssueClusterService issueClusterService,
                           InvestmentIssueAnalyzer investmentIssueAnalyzer,
                           Duration cacheTtl,
                           Clock clock) {
        this(
                newsFeedClient,
                rawNewsDeduplicator,
                issueClusterService,
                investmentIssueAnalyzer,
                null,
                null,
                null,
                cacheTtl,
                clock,
                DEFAULT_MAX_REASON_BULLETS,
                DEFAULT_MAX_WATCH_POINTS,
                DEFAULT_MAX_RELATED_STOCKS,
                DEFAULT_MAX_RELATED_ETFS
        );
    }

    InvestmentIssueService(NewsFeedClient newsFeedClient,
                           RawNewsDeduplicator rawNewsDeduplicator,
                           IssueClusterService issueClusterService,
                           InvestmentIssueAnalyzer investmentIssueAnalyzer,
                           Duration cacheTtl,
                           Clock clock,
                           int maxReasonBullets,
                           int maxWatchPoints,
                           int maxRelatedStocks,
                           int maxRelatedEtfs) {
        this(
                newsFeedClient,
                rawNewsDeduplicator,
                issueClusterService,
                investmentIssueAnalyzer,
                null,
                null,
                null,
                cacheTtl,
                clock,
                maxReasonBullets,
                maxWatchPoints,
                maxRelatedStocks,
                maxRelatedEtfs
        );
    }

    InvestmentIssueService(NewsFeedClient newsFeedClient,
                           RawNewsDeduplicator rawNewsDeduplicator,
                           IssueClusterService issueClusterService,
                           InvestmentIssueAnalyzer investmentIssueAnalyzer,
                           MatchingRoomMemberRepository matchingRoomMemberRepository,
                           MatchingRoomRepository matchingRoomRepository,
                           ChatService chatService,
                           Duration cacheTtl,
                           Clock clock,
                           int maxReasonBullets,
                           int maxWatchPoints,
                           int maxRelatedStocks,
                           int maxRelatedEtfs) {
        this.newsFeedClient = Objects.requireNonNull(newsFeedClient, "newsFeedClient must not be null");
        this.rawNewsDeduplicator = Objects.requireNonNull(rawNewsDeduplicator, "rawNewsDeduplicator must not be null");
        this.issueClusterService = Objects.requireNonNull(issueClusterService, "issueClusterService must not be null");
        this.investmentIssueAnalyzer = Objects.requireNonNull(investmentIssueAnalyzer,
                "investmentIssueAnalyzer must not be null");
        this.matchingRoomMemberRepository = matchingRoomMemberRepository;
        this.matchingRoomRepository = matchingRoomRepository;
        this.chatService = chatService;
        this.cacheTtl = cacheTtl == null ? Duration.ofSeconds(300) : cacheTtl;
        this.clock = clock == null ? Clock.system(DEFAULT_ZONE) : clock;
        this.maxReasonBullets = Math.max(0, maxReasonBullets);
        this.maxWatchPoints = Math.max(0, maxWatchPoints);
        this.maxRelatedStocks = Math.max(0, maxRelatedStocks);
        this.maxRelatedEtfs = Math.max(0, maxRelatedEtfs);
    }

    @Transactional(readOnly = true)
    public InvestmentIssueListResponseDTO getIssueList(String category, String cursor, Integer size) {
        InvestmentIssueCategory selectedCategory = parseCategory(category);
        List<CachedIssue> filteredIssues = currentIssues().stream()
                .filter(issue -> selectedCategory == InvestmentIssueCategory.ALL
                        || issue.issue().category() == selectedCategory)
                .toList();
        CachedIssue heroIssue = filteredIssues.isEmpty() ? null : filteredIssues.get(0);
        List<CachedIssue> itemCandidates = filteredIssues.size() <= 1
                ? List.of()
                : filteredIssues.subList(1, filteredIssues.size());
        int pageSize = normalizeSize(size);
        int startIndex = startIndexAfterCursor(itemCandidates, cursor);
        int endIndex = Math.min(startIndex + pageSize, itemCandidates.size());
        List<CachedIssue> pageItems = startIndex >= itemCandidates.size()
                ? List.of()
                : itemCandidates.subList(startIndex, endIndex);
        boolean hasNext = endIndex < itemCandidates.size();

        return InvestmentIssueListResponseDTO.builder()
                .categories(categories())
                .selectedCategory(selectedCategory.name())
                .heroIssue(heroIssue == null ? null : toItem(heroIssue))
                .items(pageItems.stream().map(this::toItem).toList())
                .nextCursor(hasNext && !pageItems.isEmpty() ? pageItems.get(pageItems.size() - 1).issueId() : null)
                .hasNext(hasNext)
                .build();
    }

    @Transactional(readOnly = true)
    public synchronized InvestmentIssueDetailResponseDTO getIssueDetail(String issueId) {
        String normalizedIssueId = trimToEmpty(issueId);
        CachedIssue staleCandidate = findCachedIssue(issueCache, normalizedIssueId);
        if (staleCandidate != null && !isExpired(issueCache)) {
            return toDetail(staleCandidate);
        }
        CachedIssue fallbackCandidate = staleCandidate != null
                ? staleCandidate
                : findCachedIssue(previousIssueCache, normalizedIssueId);

        CachedIssue refreshedCandidate = currentIssues().stream()
                .filter(issue -> issue.issueId().equals(normalizedIssueId))
                .findFirst()
                .orElse(null);
        if (refreshedCandidate != null) {
            return toDetail(refreshedCandidate);
        }
        if (fallbackCandidate != null) {
            return toDetail(fallbackCandidate);
        }
        throw new ApiException("investment issue not found", HttpStatus.NOT_FOUND);
    }

    @Transactional
    public InvestmentIssueShareResponseDTO shareInvestmentIssue(Long chatRoomId,
                                                                User user,
                                                                InvestmentIssueShareRequestDTO request) {
        if (chatRoomId == null) {
            throw new ApiException("Chat room id is required", HttpStatus.BAD_REQUEST);
        }
        if (user == null || user.getId() == null) {
            throw new ApiException("Authentication is required", HttpStatus.UNAUTHORIZED);
        }
        if (matchingRoomMemberRepository == null || chatService == null) {
            throw new IllegalStateException("Investment issue sharing dependencies are not configured");
        }
        if (!matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(chatRoomId, user.getId())) {
            throw new ApiException("해당 채팅방에 대한 접근 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
        if (isEndedRoom(chatRoomId)) {
            throw new ApiException(ROOM_ENDED_READ_ONLY_MESSAGE, HttpStatus.FORBIDDEN);
        }

        String issueId = request != null ? trimToEmpty(request.getIssueId()) : "";
        if (issueId.isBlank()) {
            throw new ApiException("Investment issue id is required", HttpStatus.BAD_REQUEST);
        }
        InvestmentIssueDetailResponseDTO issue = getIssueDetail(issueId);
        InvestmentIssueSharePreviewDTO preview = InvestmentIssueSharePreviewDTO.builder()
                .issueId(issue.getIssueId())
                .title(issue.getTitle())
                .label(issue.getLabel())
                .labelText(issue.getLabelText())
                .summary(issue.getSummary())
                .relatedStocks(safeList(issue.getRelatedStocks()).stream()
                        .map(InvestmentIssueRelatedStockDTO::getName)
                        .toList())
                .sourceCount(issue.getSourceCount())
                .build();

        ChatMessage saved = chatService.saveInvestmentIssueShareMessage(
                chatRoomId,
                user.getId(),
                user.getNickname(),
                preview
        );
        return InvestmentIssueShareResponseDTO.builder()
                .messageId(saved.getId())
                .chatRoomId(chatRoomId)
                .type(ChatService.TYPE_INVESTMENT_ISSUE_SHARE)
                .issue(preview)
                .createdAt(saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : null)
                .build();
    }

    private boolean isEndedRoom(Long chatRoomId) {
        if (matchingRoomRepository == null || chatRoomId == null) {
            return false;
        }
        Optional<com.uniport.entity.MatchingRoom> room = matchingRoomRepository.findById(chatRoomId);
        return room != null && room
                .map(value -> "ended".equalsIgnoreCase(value.getStatus()))
                .orElse(false);
    }

    private synchronized List<CachedIssue> currentIssues() {
        Instant now = clock.instant();
        if (issueCache != null && !isExpired(issueCache, now)) {
            return issueCache.issues();
        }

        List<CachedIssue> issues = computeCurrentIssues();
        if (issueCache != null && !issueCache.issues().isEmpty()) {
            previousIssueCache = issueCache;
        }
        issueCache = new CacheEntry(issues, now.plus(cacheTtl));
        return issues;
    }

    private List<CachedIssue> computeCurrentIssues() {
        List<FetchedNewsArticle> fetchedArticles = safeList(newsFeedClient.fetchLatest());
        List<FetchedNewsArticle> deduplicatedArticles = rawNewsDeduplicator.deduplicate(fetchedArticles);
        return issueClusterService.cluster(deduplicatedArticles).stream()
                .map(investmentIssueAnalyzer::analyze)
                .map(issue -> new CachedIssue(issueId(issue), issue))
                .sorted(Comparator
                        .comparing((CachedIssue issue) -> issue.issue().updatedAt(), NEWEST_FIRST)
                        .thenComparing(CachedIssue::issueId))
                .toList();
    }

    private InvestmentIssueItemDTO toItem(CachedIssue cachedIssue) {
        InvestmentIssue issue = cachedIssue.issue();
        InvestmentIssueCategory category = issue.category();
        InvestmentIssueLabel label = issue.label();
        return InvestmentIssueItemDTO.builder()
                .issueId(cachedIssue.issueId())
                .title(issue.title())
                .category(category == null ? null : category.name())
                .categoryLabel(category == null ? null : category.label())
                .label(label == null ? null : label.apiValue())
                .labelText(label == null ? null : label.labelText())
                .summary(issue.summary())
                .reasonBullets(limit(issue.reasonBullets(), maxReasonBullets))
                .watchPoints(limit(issue.watchPoints(), maxWatchPoints))
                .relatedStocks(relatedStocks(issue))
                .relatedEtfs(relatedEtfs(issue))
                .sourceCount(issue.sourceCount())
                .publishedAt(formatDateTime(issue.publishedAt()))
                .updatedAt(formatDateTime(issue.updatedAt()))
                .build();
    }

    private InvestmentIssueDetailResponseDTO toDetail(CachedIssue cachedIssue) {
        InvestmentIssue issue = cachedIssue.issue();
        InvestmentIssueCategory category = issue.category();
        InvestmentIssueLabel label = issue.label();
        return InvestmentIssueDetailResponseDTO.builder()
                .issueId(cachedIssue.issueId())
                .title(issue.title())
                .category(category == null ? null : category.name())
                .categoryLabel(category == null ? null : category.label())
                .label(label == null ? null : label.apiValue())
                .labelText(label == null ? null : label.labelText())
                .summary(issue.summary())
                .body(generatedBody(issue))
                .reasonBullets(limit(issue.reasonBullets(), maxReasonBullets))
                .watchPoints(limit(issue.watchPoints(), maxWatchPoints))
                .relatedStocks(relatedStocks(issue))
                .relatedEtfs(relatedEtfs(issue))
                .sourceCount(issue.sourceCount())
                .publishedAt(formatDateTime(issue.publishedAt()))
                .updatedAt(formatDateTime(issue.updatedAt()))
                .sourceArticles(sourceArticles(issue.sourceArticles()))
                .build();
    }

    private String generatedBody(InvestmentIssue issue) {
        List<String> sections = new ArrayList<>();
        String summary = trimToEmpty(issue.summary());
        if (!summary.isBlank()) {
            sections.add(summary);
        }
        List<String> reasons = limit(issue.reasonBullets(), maxReasonBullets);
        if (!reasons.isEmpty()) {
            sections.add("주요 근거\n" + bulletLines(reasons));
        }
        List<String> watchPoints = limit(issue.watchPoints(), maxWatchPoints);
        if (!watchPoints.isEmpty()) {
            sections.add("확인할 점\n" + bulletLines(watchPoints));
        }
        sections.add(sourceContextLine(issue.sourceCount()));
        return String.join("\n\n", sections);
    }

    private String sourceContextLine(int sourceCount) {
        if (sourceCount > 1) {
            return "이 설명은 같은 이슈로 묶인 기사 " + sourceCount + "건을 바탕으로 UniPort가 생성했어요.";
        }
        return "이 설명은 현재 확인된 기사 흐름을 바탕으로 UniPort가 생성했어요.";
    }

    private String bulletLines(List<String> values) {
        return values.stream()
                .map(value -> "- " + value)
                .toList()
                .stream()
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private List<InvestmentIssueRelatedStockDTO> relatedStocks(InvestmentIssue issue) {
        return limit(issue.relatedStocks(), maxRelatedStocks).stream()
                .map(stock -> InvestmentIssueRelatedStockDTO.builder()
                        .name(stock.name())
                        .symbol(stock.symbol())
                        .market(stock.market())
                        .reason(stockReason(issue, stock))
                        .build())
                .toList();
    }

    private String stockReason(InvestmentIssue issue, MappedStock stock) {
        if (StockMappingService.MATCH_TYPE_DIRECT.equals(stock.matchType())) {
            return stock.name() + "가 기사에서 직접 언급된 연관 종목입니다.";
        }
        return displaySubject(issue) + " 이슈와 같은 테마 후보로 함께 확인됐어요.";
    }

    private List<InvestmentIssueRelatedEtfDTO> relatedEtfs(InvestmentIssue issue) {
        return limit(issue.relatedEtfs(), maxRelatedEtfs).stream()
                .map(etf -> InvestmentIssueRelatedEtfDTO.builder()
                        .name(etf.name())
                        .symbol(etf.symbol())
                        .reason(displaySubject(issue) + " 흐름을 추적할 때 참고할 수 있는 ETF 후보입니다.")
                        .build())
                .toList();
    }

    private List<InvestmentIssueSourceArticleDTO> sourceArticles(List<FetchedNewsArticle> articles) {
        return safeList(articles).stream()
                .map(article -> InvestmentIssueSourceArticleDTO.builder()
                        .articleId(article.getId())
                        .sourceName(article.getSourceName())
                        .title(article.getTitle())
                        .summary(article.getSummary())
                        .publishedAt(formatDateTime(article.getPublishedAt()))
                        .externalUrl(article.getExternalUrl())
                        .build())
                .toList();
    }

    private String issueId(InvestmentIssue issue) {
        return "issue_"
                + issueDate(issue).format(ISSUE_DATE_FORMATTER)
                + "_"
                + slug(issue)
                + "_"
                + shortHash(hashSource(issue));
    }

    private LocalDate issueDate(InvestmentIssue issue) {
        LocalDate clusterDate = clusterKeyDate(issue.clusterKey());
        if (clusterDate != null) {
            return clusterDate;
        }
        LocalDateTime dateTime = issue.publishedAt() != null ? issue.publishedAt() : issue.updatedAt();
        if (dateTime != null) {
            return dateTime.toLocalDate();
        }
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone()).toLocalDate();
    }

    private LocalDate clusterKeyDate(String clusterKey) {
        String normalizedClusterKey = trimToEmpty(clusterKey);
        if (normalizedClusterKey.length() < 8) {
            return null;
        }
        String datePart = normalizedClusterKey.substring(0, 8);
        if (!datePart.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            return LocalDate.parse(datePart, ISSUE_DATE_FORMATTER);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String slug(InvestmentIssue issue) {
        String source = (trimToEmpty(issue.mainEntity()) + " "
                + trimToEmpty(issue.title()) + " "
                + (issue.category() == null ? "" : issue.category().name()))
                .toLowerCase(Locale.ROOT);
        String upperSource = source.toUpperCase(Locale.ROOT);

        if (upperSource.contains("HBM")) {
            return "hbm_semiconductor";
        }
        if (source.contains("환율") || source.contains("원달러") || source.contains("exchange")) {
            return "exchange_rate";
        }
        if (upperSource.contains("FOMC")) {
            return "fomc";
        }
        if (source.contains("엔비디아") || upperSource.contains("NVIDIA")) {
            return source.contains("실적") || source.contains("earnings") ? "nvidia_earnings" : "nvidia";
        }
        if (source.contains("삼성전자")) {
            return "samsung_electronics";
        }
        if (source.contains("반도체")) {
            return "semiconductor";
        }
        if (source.contains("금리")) {
            return "interest_rate";
        }
        if (source.contains("코스피")) {
            return "kospi";
        }

        String slug = NON_SLUG_CHARACTER.matcher(source).replaceAll("_")
                .replaceAll("^_+", "")
                .replaceAll("_+$", "");
        if (slug.isBlank()) {
            return issue.category() == null ? "market_issue" : issue.category().name().toLowerCase(Locale.ROOT);
        }
        return slug.length() > 48 ? slug.substring(0, 48).replaceAll("_+$", "") : slug;
    }

    private String hashSource(InvestmentIssue issue) {
        String clusterKey = trimToEmpty(issue.clusterKey());
        if (!clusterKey.isBlank()) {
            return clusterKey;
        }
        List<String> parts = new ArrayList<>();
        parts.add(trimToEmpty(issue.title()));
        parts.add(trimToEmpty(issue.summary()));
        parts.addAll(issue.sourceArticles().stream()
                .map(article -> trimToEmpty(article.getId()) + ":" + trimToEmpty(article.getExternalUrl()))
                .sorted()
                .toList());
        return String.join("|", parts);
    }

    private String shortHash(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(trimToEmpty(source).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 6);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private InvestmentIssueCategory parseCategory(String category) {
        if (category == null || category.isBlank()) {
            return InvestmentIssueCategory.ALL;
        }
        String normalized = category.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(InvestmentIssueCategory.values())
                .filter(value -> value.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new ApiException("unsupported investment issue category: " + category,
                        HttpStatus.BAD_REQUEST));
    }

    private List<InvestmentIssueCategoryDTO> categories() {
        return Arrays.stream(InvestmentIssueCategory.values())
                .map(category -> InvestmentIssueCategoryDTO.builder()
                        .category(category.name())
                        .label(category.label())
                        .build())
                .toList();
    }

    private boolean isExpired(CacheEntry cacheEntry) {
        return cacheEntry == null || isExpired(cacheEntry, clock.instant());
    }

    private boolean isExpired(CacheEntry cacheEntry, Instant now) {
        return !now.isBefore(cacheEntry.expiresAt());
    }

    private CachedIssue findCachedIssue(CacheEntry cacheEntry, String issueId) {
        if (cacheEntry == null || issueId.isBlank()) {
            return null;
        }
        return cacheEntry.issues().stream()
                .filter(issue -> issue.issueId().equals(issueId))
                .findFirst()
                .orElse(null);
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private int startIndexAfterCursor(List<CachedIssue> issues, String cursor) {
        String normalizedCursor = trimToEmpty(cursor);
        if (normalizedCursor.isBlank()) {
            return 0;
        }
        for (int index = 0; index < issues.size(); index++) {
            if (issues.get(index).issueId().equals(normalizedCursor)) {
                return index + 1;
            }
        }
        return 0;
    }

    private String displaySubject(InvestmentIssue issue) {
        String mainEntity = trimToEmpty(issue.mainEntity());
        if (!mainEntity.isBlank()) {
            return mainEntity;
        }
        String title = trimToEmpty(issue.title());
        if (!title.isBlank()) {
            return title;
        }
        return issue.category() == null ? "시장" : issue.category().label();
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(DEFAULT_ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private <T> List<T> limit(List<T> values, int limit) {
        List<T> safeValues = safeList(values);
        if (safeValues.size() <= limit) {
            return safeValues;
        }
        return safeValues.subList(0, limit);
    }

    private record CachedIssue(String issueId, InvestmentIssue issue) {
    }

    private record CacheEntry(List<CachedIssue> issues, Instant expiresAt) {

        private CacheEntry {
            issues = List.copyOf(issues);
        }
    }
}
