package com.uniport.service;

import com.uniport.dto.NewsItemResponseDTO;
import com.uniport.dto.NewsListResponseDTO;
import com.uniport.dto.NewsSharePreviewDTO;
import com.uniport.dto.NewsShareRequestDTO;
import com.uniport.dto.NewsShareResponseDTO;
import com.uniport.dto.RealtimeNewsCategoryDTO;
import com.uniport.dto.RealtimeNewsDetailResponseDTO;
import com.uniport.dto.RealtimeNewsItemDTO;
import com.uniport.dto.RealtimeNewsListResponseDTO;
import com.uniport.dto.RealtimeNewsRelatedStockDTO;
import com.uniport.dto.RealtimeNewsSourceArticleDTO;
import com.uniport.entity.ChatMessage;
import com.uniport.entity.ManagedNewsArticle;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.ManagedNewsArticleRepository;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class NewsService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final String DEFAULT_SOURCE = "UniPort Markets";
    private static final String ROOM_ENDED_READ_ONLY_MESSAGE = "종료된 채팅방은 보기만 할 수 있습니다.";

    private final ManagedNewsArticleRepository managedNewsArticleRepository;
    private final MatchingRoomMemberRepository matchingRoomMemberRepository;
    private final MatchingRoomRepository matchingRoomRepository;
    private final ChatService chatService;
    private final NewsFeedClient newsFeedClient;
    private final NewsSentimentAnalyzer newsSentimentAnalyzer;

    public NewsService(ManagedNewsArticleRepository managedNewsArticleRepository,
                       MatchingRoomMemberRepository matchingRoomMemberRepository,
                       MatchingRoomRepository matchingRoomRepository,
                       ChatService chatService,
                       NewsFeedClient newsFeedClient,
                       NewsSentimentAnalyzer newsSentimentAnalyzer) {
        this.managedNewsArticleRepository = managedNewsArticleRepository;
        this.matchingRoomMemberRepository = matchingRoomMemberRepository;
        this.matchingRoomRepository = matchingRoomRepository;
        this.chatService = chatService;
        this.newsFeedClient = newsFeedClient;
        this.newsSentimentAnalyzer = newsSentimentAnalyzer;
    }

    @Transactional(readOnly = true)
    public NewsListResponseDTO getNewsList(String category, Integer page, Integer size) {
        NewsCategory selectedCategory = parseCategory(category);
        int safePage = page != null && page >= 0 ? page : 0;
        int safeSize = size != null && size > 0 ? Math.min(size, 50) : 20;

        List<NewsArticleView> filtered = loadArticles().stream()
                .filter(article -> selectedCategory == NewsCategory.ALL || article.category() == selectedCategory)
                .sorted(latestFirst())
                .toList();

        NewsArticleView featured = filtered.stream()
                .filter(NewsArticleView::featured)
                .findFirst()
                .orElse(filtered.isEmpty() ? null : filtered.get(0));

        List<NewsArticleView> regularItems = filtered.stream()
                .filter(article -> featured == null || !article.id().equals(featured.id()))
                .toList();

        int fromIndex = Math.min(safePage * safeSize, regularItems.size());
        int toIndex = Math.min(fromIndex + safeSize, regularItems.size());
        boolean hasNext = toIndex < regularItems.size();

        return NewsListResponseDTO.builder()
                .featured(featured != null ? toItem(featured, false, true) : null)
                .items(regularItems.subList(fromIndex, toIndex).stream()
                        .map(article -> toItem(article, false, article.featured()))
                        .toList())
                .page(safePage)
                .size(safeSize)
                .hasNext(hasNext)
                .build();
    }

    @Transactional(readOnly = true)
    public NewsItemResponseDTO getNewsDetail(String newsId) {
        NewsArticleView article = requireArticleView(newsId);
        return toItem(article, true, article.featured());
    }

    @Transactional(readOnly = true)
    public RealtimeNewsListResponseDTO getRealtimeNewsList(String category, String cursor, Integer size) {
        RealtimeNewsCategory selectedCategory = parseRealtimeCategory(category);
        int safeSize = size != null && size > 0 ? Math.min(size, 50) : 20;

        List<NewsArticleView> filtered = loadArticles().stream()
                .filter(article -> matchesRealtimeCategory(article, selectedCategory))
                .sorted(latestFirst())
                .toList();

        NewsArticleView hero = filtered.isEmpty() ? null : filtered.get(0);
        List<NewsArticleView> regularItems = filtered.stream()
                .filter(article -> hero == null || !article.id().equals(hero.id()))
                .toList();

        int fromIndex = resolveCursorIndex(regularItems, cursor);
        int toIndex = Math.min(fromIndex + safeSize, regularItems.size());
        boolean hasNext = toIndex < regularItems.size();
        List<NewsArticleView> pageItems = regularItems.subList(fromIndex, toIndex);

        return RealtimeNewsListResponseDTO.builder()
                .categories(realtimeCategories())
                .selectedCategory(selectedCategory.name())
                .heroNews(hero != null ? toRealtimeItem(hero, selectedCategory) : null)
                .items(pageItems.stream()
                        .map(article -> toRealtimeItem(article, selectedCategory))
                        .toList())
                .nextCursor(hasNext && !pageItems.isEmpty() ? pageItems.get(pageItems.size() - 1).id() : null)
                .hasNext(hasNext)
                .build();
    }

    @Transactional(readOnly = true)
    public RealtimeNewsDetailResponseDTO getRealtimeNewsDetail(String newsId) {
        NewsArticleView article = requireArticleView(newsId);
        RealtimeNewsCategory realtimeCategory = classifyArticle(article);
        List<RealtimeNewsRelatedStockDTO> relatedStocks = extractRelatedStocks(article);
        NewsSentimentAnalysis sentiment = newsSentimentAnalyzer.analyze(toSentimentInput(article));
        return RealtimeNewsDetailResponseDTO.builder()
                .newsId(article.id())
                .category(realtimeCategory.name())
                .categoryLabel(realtimeCategory.label())
                .title(article.title())
                .summary(article.summary())
                .body(article.body())
                .sourceName(article.sourceName())
                .publishedAt(toIso(article.publishedAt()))
                .externalUrl(article.externalUrl())
                .coreSummary(buildCoreSummary(article))
                .sentiment(sentiment.sentiment())
                .sentimentLabel(sentiment.label())
                .sentimentScore(sentiment.score())
                .sentimentReason(sentiment.reason())
                .investmentPoints(buildInvestmentPoints(article, realtimeCategory, relatedStocks.stream()
                        .map(RealtimeNewsRelatedStockDTO::getName)
                        .toList()))
                .riskPoints(buildRiskPoints(realtimeCategory))
                .relatedStocks(relatedStocks)
                .sourceArticles(List.of(toSourceArticle(article)))
                .build();
    }

    @Transactional
    public NewsShareResponseDTO shareNews(Long chatRoomId, User user, NewsShareRequestDTO request) {
        if (chatRoomId == null) {
            throw new ApiException("Chat room id is required", HttpStatus.BAD_REQUEST);
        }
        if (user == null || user.getId() == null) {
            throw new ApiException("Authentication is required", HttpStatus.UNAUTHORIZED);
        }
        if (!matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(chatRoomId, user.getId())) {
            throw new ApiException("해당 채팅방에 대한 접근 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
        if (isEndedRoom(chatRoomId)) {
            throw new ApiException(ROOM_ENDED_READ_ONLY_MESSAGE, HttpStatus.FORBIDDEN);
        }

        String newsId = request != null ? request.getNewsId() : null;
        RealtimeNewsDetailResponseDTO news = getRealtimeNewsDetail(newsId);
        NewsSharePreviewDTO preview = NewsSharePreviewDTO.builder()
                .id(news.getNewsId())
                .categoryLabel(news.getCategoryLabel())
                .title(news.getTitle())
                .summary(news.getSummary())
                .sourceName(news.getSourceName())
                .publishedAt(news.getPublishedAt())
                .sentiment(news.getSentiment())
                .sentimentLabel(news.getSentimentLabel())
                .sentimentScore(news.getSentimentScore())
                .sentimentReason(news.getSentimentReason())
                .relatedStocks(news.getRelatedStocks().stream()
                        .map(RealtimeNewsRelatedStockDTO::getName)
                        .toList())
                .investmentPoints(news.getInvestmentPoints())
                .build();

        ChatMessage saved = chatService.saveNewsShareMessage(chatRoomId, user.getId(), user.getNickname(), preview);
        return NewsShareResponseDTO.builder()
                .messageId(saved.getId())
                .chatRoomId(chatRoomId)
                .type(ChatService.TYPE_NEWS_SHARE)
                .news(preview)
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

    private List<NewsArticleView> loadArticles() {
        List<NewsArticleView> fetchedArticles = loadFetchedArticles();
        if (!fetchedArticles.isEmpty()) {
            return fetchedArticles;
        }

        List<ManagedNewsArticle> managedArticles = managedNewsArticleRepository.findAllByOrderByPublishedAtDescIdDesc();
        if (managedArticles != null && !managedArticles.isEmpty()) {
            return managedArticles.stream()
                    .map(this::toView)
                    .toList();
        }
        return loadFallbackArticles();
    }

    private List<NewsArticleView> loadFetchedArticles() {
        List<FetchedNewsArticle> fetchedArticles = newsFeedClient.fetchLatest();
        if (fetchedArticles == null || fetchedArticles.isEmpty()) {
            return List.of();
        }
        return fetchedArticles.stream()
                .map(this::toView)
                .toList();
    }

    private Optional<ManagedNewsArticle> findManagedArticle(String newsId) {
        Optional<ManagedNewsArticle> byKey = managedNewsArticleRepository.findByNewsKey(newsId);
        if (byKey != null && byKey.isPresent()) {
            return byKey;
        }
        try {
            return managedNewsArticleRepository.findById(Long.parseLong(newsId));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private NewsArticleView requireArticleView(String newsId) {
        String normalizedNewsId = normalizeNewsId(newsId);
        Optional<NewsArticleView> fetchedArticle = loadFetchedArticles().stream()
                .filter(article -> article.id().equals(normalizedNewsId))
                .findFirst();
        if (fetchedArticle.isPresent()) {
            return fetchedArticle.get();
        }

        Optional<ManagedNewsArticle> managedArticle = findManagedArticle(normalizedNewsId);
        if (managedArticle.isPresent()) {
            return toView(managedArticle.get());
        }

        return loadFallbackArticles().stream()
                .filter(article -> article.id().equals(normalizedNewsId))
                .findFirst()
                .orElseThrow(() -> new ApiException("News article not found", HttpStatus.NOT_FOUND));
    }

    private NewsArticleView toView(ManagedNewsArticle article) {
        NewsCategory category = resolveCategory(article.getCategory(), article.getStockCode(), article.getStockName());
        LocalDateTime publishedAt = article.getPublishedAt() != null
                ? article.getPublishedAt()
                : article.getCreatedAt();
        return new NewsArticleView(
                article.getNewsKey() != null ? article.getNewsKey() : String.valueOf(article.getId()),
                category,
                category.label(),
                defaultIfBlank(article.getTitle(), "제목 없는 뉴스"),
                defaultIfBlank(article.getSummary(), ""),
                defaultIfBlank(article.getContent(), ""),
                defaultIfBlank(article.getSourceLabel(), DEFAULT_SOURCE),
                publishedAt,
                Boolean.TRUE.equals(article.getFeatured()),
                article.getImageUrl(),
                article.getExternalUrl()
        );
    }

    private NewsArticleView toView(FetchedNewsArticle article) {
        NewsCategory category = article.getCategory() != null ? article.getCategory() : NewsCategory.MARKET;
        return new NewsArticleView(
                article.getId(),
                category,
                category.label(),
                defaultIfBlank(article.getTitle(), "제목 없는 뉴스"),
                defaultIfBlank(article.getSummary(), ""),
                buildNewsroomBody(article, category),
                defaultIfBlank(article.getSourceName(), "네이버 뉴스"),
                article.getPublishedAt(),
                article.isFeatured(),
                null,
                article.getExternalUrl()
        );
    }

    private NewsItemResponseDTO toItem(NewsArticleView article, boolean includeBody, boolean forceFeatured) {
        return NewsItemResponseDTO.builder()
                .id(article.id())
                .category(article.category().name())
                .categoryLabel(article.categoryLabel())
                .title(article.title())
                .summary(article.summary())
                .body(includeBody ? article.body() : null)
                .sourceName(article.sourceName())
                .publishedAt(toIso(article.publishedAt()))
                .isFeatured(forceFeatured)
                .thumbnailUrl(article.thumbnailUrl())
                .externalUrl(article.externalUrl())
                .build();
    }

    private RealtimeNewsItemDTO toRealtimeItem(NewsArticleView article, RealtimeNewsCategory selectedCategory) {
        RealtimeNewsCategory category = selectedCategory == RealtimeNewsCategory.ALL
                ? classifyArticle(article)
                : selectedCategory;
        RealtimeNewsCategory displayCategory = displayRealtimeCategory(category);
        List<String> relatedStocks = extractRelatedStocks(article).stream()
                .map(RealtimeNewsRelatedStockDTO::getName)
                .toList();
        NewsSentimentAnalysis sentiment = newsSentimentAnalyzer.analyze(toSentimentInput(article));
        return RealtimeNewsItemDTO.builder()
                .newsId(article.id())
                .category(displayCategory.name())
                .categoryLabel(displayCategory.label())
                .title(article.title())
                .summary(article.summary())
                .sourceName(article.sourceName())
                .publishedAt(toIso(article.publishedAt()))
                .externalUrl(article.externalUrl())
                .sentiment(sentiment.sentiment())
                .sentimentLabel(sentiment.label())
                .sentimentScore(sentiment.score())
                .sentimentReason(sentiment.reason())
                .relatedStocks(relatedStocks)
                .investmentPoints(buildInvestmentPoints(article, category, relatedStocks).stream()
                        .limit(2)
                        .toList())
                .riskPoints(buildRiskPoints(category).stream()
                        .limit(2)
                        .toList())
                .build();
    }

    private NewsSentimentInput toSentimentInput(NewsArticleView article) {
        return new NewsSentimentInput(
                article.id(),
                article.title(),
                article.summary(),
                article.body(),
                article.sourceName()
        );
    }

    private RealtimeNewsSourceArticleDTO toSourceArticle(NewsArticleView article) {
        return RealtimeNewsSourceArticleDTO.builder()
                .articleId("ARTICLE_" + article.id())
                .sourceName(article.sourceName())
                .title(article.title())
                .summary(article.summary())
                .publishedAt(toIso(article.publishedAt()))
                .externalUrl(article.externalUrl())
                .build();
    }

    private int resolveCursorIndex(List<NewsArticleView> items, String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        String normalizedCursor = cursor.trim();
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).id().equals(normalizedCursor)) {
                return index + 1;
            }
        }
        return 0;
    }

    private List<RealtimeNewsCategoryDTO> realtimeCategories() {
        return List.of(
                        RealtimeNewsCategory.ALL,
                        RealtimeNewsCategory.MARKET,
                        RealtimeNewsCategory.THEME,
                        RealtimeNewsCategory.COMPANY
                ).stream()
                .map(category -> RealtimeNewsCategoryDTO.builder()
                        .category(category.name())
                        .label(category.label())
                        .build())
                .toList();
    }

    private RealtimeNewsCategory parseRealtimeCategory(String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return RealtimeNewsCategory.ALL;
        }
        String value = rawCategory.trim().toUpperCase(Locale.ROOT);
        try {
            return displayRealtimeCategory(RealtimeNewsCategory.valueOf(value));
        } catch (IllegalArgumentException ignored) {
            throw new ApiException("Unsupported realtime news category: " + rawCategory, HttpStatus.BAD_REQUEST);
        }
    }

    private boolean matchesRealtimeCategory(NewsArticleView article, RealtimeNewsCategory selectedCategory) {
        if (selectedCategory == RealtimeNewsCategory.ALL) {
            return true;
        }
        return displayRealtimeCategory(classifyArticle(article)) == selectedCategory;
    }

    private RealtimeNewsCategory classifyArticle(NewsArticleView article) {
        if (!extractRelatedStocks(article).isEmpty()) {
            return RealtimeNewsCategory.COMPANY;
        }
        String text = searchableText(article);
        if (containsAny(text, "실적", "영업이익", "매출", "어닝", "가이던스", "서프라이즈", "쇼크")) {
            return RealtimeNewsCategory.EARNINGS;
        }
        if (containsAny(text, "전쟁", "분쟁", "중동", "우크라이나", "러시아", "이란", "공급망", "외교")) {
            return RealtimeNewsCategory.GEOPOLITICAL;
        }
        if (containsAny(text, "정부", "정책", "규제", "세제", "금융위", "금감원", "지원책")) {
            return RealtimeNewsCategory.POLICY;
        }
        if (containsAny(text, "AI", "반도체", "방산", "원전", "로봇", "2차전지", "배터리", "바이오", "전력")) {
            return RealtimeNewsCategory.THEME;
        }
        if (article.category() == NewsCategory.DOMESTIC_STOCK || article.category() == NewsCategory.OVERSEAS_STOCK) {
            return RealtimeNewsCategory.COMPANY;
        }
        return RealtimeNewsCategory.MARKET;
    }

    private RealtimeNewsCategory displayRealtimeCategory(RealtimeNewsCategory category) {
        return switch (category) {
            case ALL -> RealtimeNewsCategory.ALL;
            case THEME -> RealtimeNewsCategory.THEME;
            case EARNINGS, COMPANY -> RealtimeNewsCategory.COMPANY;
            case MARKET, POLICY, GEOPOLITICAL -> RealtimeNewsCategory.MARKET;
        };
    }

    private String buildCoreSummary(NewsArticleView article) {
        String body = normalizeDisplayText(article.body());
        String summary = normalizeDisplayText(article.summary());
        if (body.isBlank() || isSameDisplayText(body, summary)) {
            return null;
        }
        return body;
    }

    private String normalizeDisplayText(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isSameDisplayText(String left, String right) {
        return left.replaceAll("\\s+", " ").equals(right.replaceAll("\\s+", " "));
    }

    private List<String> buildInvestmentPoints(NewsArticleView article,
                                               RealtimeNewsCategory category,
                                               List<String> relatedStockNames) {
        List<String> points = new ArrayList<>();
        switch (category) {
            case EARNINGS -> {
                points.add("실적 기대나 발표 결과가 투자 심리에 직접 영향을 줄 수 있어요.");
                points.add("예상치와 실제 숫자의 차이가 관련 종목 변동성을 키울 수 있어요.");
            }
            case POLICY -> {
                points.add("정책 변화는 관련 업종의 수급과 투자 심리에 빠르게 반영될 수 있어요.");
                points.add("수혜 업종과 규제 부담 업종을 나눠서 확인할 필요가 있어요.");
            }
            case GEOPOLITICAL -> {
                points.add("지정학 이슈는 유가, 환율, 방산, 운송 업종에 동시에 영향을 줄 수 있어요.");
                points.add("뉴스 전개 속도에 따라 단기 변동성이 커질 수 있어요.");
            }
            case THEME -> {
                points.add("같은 테마가 반복적으로 언급되면 관련 종목 관심이 함께 커질 수 있어요.");
                points.add("테마 내에서도 실적 연결성이 높은 종목을 우선 확인하는 것이 좋아요.");
            }
            case COMPANY -> {
                points.add("개별 기업 이슈는 해당 종목과 같은 밸류체인 종목에 영향을 줄 수 있어요.");
                points.add("뉴스가 실적, 수급, 정책 중 어떤 재료와 연결되는지 확인해야 해요.");
            }
            case MARKET, ALL -> {
                points.add("시장 전체 흐름은 지수, 금리, 환율 같은 공통 변수와 함께 봐야 해요.");
                points.add("대형주 움직임이 업종 전반으로 확산되는지 확인하는 것이 좋아요.");
            }
        }
        if (!relatedStockNames.isEmpty()) {
            points.add(0, String.join(", ", relatedStockNames) + " 등 관련 종목의 투자 심리가 함께 움직일 수 있어요.");
        }
        if (points.isEmpty()) {
            points.add(defaultIfBlank(article.summary(), "뉴스 요약을 기준으로 투자 재료를 확인해야 해요."));
        }
        return points;
    }

    private List<String> buildRiskPoints(RealtimeNewsCategory category) {
        return switch (category) {
            case EARNINGS -> List.of(
                    "실적 기대가 이미 주가에 반영돼 있으면 발표 이후 차익실현이 나올 수 있어요.",
                    "일회성 이익인지 지속 가능한 실적 개선인지 확인해야 해요."
            );
            case POLICY -> List.of(
                    "정책 발표 이후 실제 시행 시점과 수혜 범위가 달라질 수 있어요.",
                    "규제성 정책은 업종별로 영향이 엇갈릴 수 있어요."
            );
            case GEOPOLITICAL -> List.of(
                    "분쟁 완화 뉴스가 나오면 관련 테마가 빠르게 반락할 수 있어요.",
                    "확인되지 않은 속보성 뉴스는 변동성을 크게 만들 수 있어요."
            );
            case THEME -> List.of(
                    "테마성 급등은 실적 검증 전까지 변동성이 클 수 있어요.",
                    "같은 테마 안에서도 종목별 실적 연결성이 다를 수 있어요."
            );
            case COMPANY -> List.of(
                    "개별 호재가 단기 이슈에 그치면 주가가 되돌림을 보일 수 있어요.",
                    "관련 종목 전체로 확대 해석하기 전에 직접 수혜 여부를 확인해야 해요."
            );
            case MARKET, ALL -> List.of(
                    "시장 전체 뉴스는 단기 수급에 따라 빠르게 방향이 바뀔 수 있어요.",
                    "지수 흐름과 개별 종목 흐름이 항상 같은 방향으로 움직이지는 않아요."
            );
        };
    }

    private List<RealtimeNewsRelatedStockDTO> extractRelatedStocks(NewsArticleView article) {
        String text = headlineText(article);
        Map<String, RealtimeNewsRelatedStockDTO> stocks = new LinkedHashMap<>();
        addStockIfMatched(stocks, text, "삼성전자", "005930", "KOSPI", "삼성전자");
        addStockIfMatched(stocks, text, "SK하이닉스", "000660", "KOSPI", "SK하이닉스", "하이닉스");
        addStockIfMatched(stocks, text, "한미반도체", "042700", "KOSPI", "한미반도체");
        addStockIfMatched(stocks, text, "현대차", "005380", "KOSPI", "현대차", "현대자동차");
        addStockIfMatched(stocks, text, "기아", "000270", "KOSPI", "기아");
        addStockIfMatched(stocks, text, "NAVER", "035420", "KOSPI", "NAVER", "네이버");
        addStockIfMatched(stocks, text, "카카오", "035720", "KOSPI", "카카오");
        addStockIfMatched(stocks, text, "LG에너지솔루션", "373220", "KOSPI", "LG에너지솔루션", "LG엔솔");
        addStockIfMatched(stocks, text, "셀트리온", "068270", "KOSPI", "셀트리온");
        addStockIfMatched(stocks, text, "POSCO홀딩스", "005490", "KOSPI", "POSCO", "포스코");
        addStockIfMatched(stocks, text, "한국석유", "004090", "KOSPI", "한국석유");
        addStockIfMatched(stocks, text, "S-Oil", "010950", "KOSPI", "S-OIL", "에쓰오일");
        addStockIfMatched(stocks, text, "한화에어로스페이스", "012450", "KOSPI", "한화에어로스페이스");
        addStockIfMatched(stocks, text, "대한항공", "003490", "KOSPI", "대한항공");
        addStockIfMatched(stocks, text, "NVIDIA", "NVDA", "NASDAQ", "NVIDIA", "엔비디아");
        addStockIfMatched(stocks, text, "Tesla", "TSLA", "NASDAQ", "TESLA", "테슬라");
        addStockIfMatched(stocks, text, "Apple", "AAPL", "NASDAQ", "APPLE", "애플");
        return List.copyOf(stocks.values());
    }

    private void addStockIfMatched(Map<String, RealtimeNewsRelatedStockDTO> stocks,
                                   String text,
                                   String name,
                                   String symbol,
                                   String market,
                                   String... keywords) {
        if (!containsAny(text, keywords)) {
            return;
        }
        stocks.putIfAbsent(symbol, RealtimeNewsRelatedStockDTO.builder()
                .stockId(("KR".equals(market) || "KOSPI".equals(market) || "KOSDAQ".equals(market) ? "KR_" : "US_") + symbol)
                .name(name)
                .symbol(symbol)
                .market(market)
                .build());
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank()
                    && text.contains(keyword.trim().toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String searchableText(NewsArticleView article) {
        return (defaultIfBlank(article.title(), "") + " "
                + defaultIfBlank(article.summary(), "") + " "
                + defaultIfBlank(article.body(), "") + " "
                + defaultIfBlank(article.sourceName(), ""))
                .toUpperCase(Locale.ROOT);
    }

    private String headlineText(NewsArticleView article) {
        return (defaultIfBlank(article.title(), "") + " "
                + defaultIfBlank(article.summary(), ""))
                .toUpperCase(Locale.ROOT);
    }

    private List<NewsArticleView> loadFallbackArticles() {
        return List.of(
                fallback("news_001", NewsCategory.MARKET, true,
                        "코스피, 반도체 강세에 장 초반 상승 출발",
                        "외국인 순매수와 대형 기술주 반등이 지수 흐름을 이끌고 있어요.",
                        "반도체 대형주를 중심으로 매수세가 유입되면서 코스피가 장 초반 강세를 보이고 있어요.\n\n대형 기술주의 반등이 지수 흐름을 이끌고 있지만, 업종 전반으로 매수세가 확산되는지는 아직 확인이 필요합니다.",
                        LocalDateTime.of(2026, 5, 11, 11, 48)),
                fallback("news_002", NewsCategory.OVERSEAS_STOCK, false,
                        "AI 서버 밸류체인, 실적 전망 상향 흐름 지속",
                        "글로벌 빅테크의 AI 인프라 투자가 이어지면서 반도체와 서버 부품 기업의 모멘텀이 유지되고 있어요.",
                        "미국 대형 기술주의 AI 인프라 투자가 다시 확대되며 서버 밸류체인 전반의 실적 기대가 높아지고 있어요.\n\n다만 이미 기대가 많이 반영된 종목은 실적 발표 전후 변동성이 커질 수 있습니다.",
                        LocalDateTime.of(2026, 5, 11, 11, 32)),
                fallback("news_003", NewsCategory.DOMESTIC_STOCK, false,
                        "국내 배터리주, 원가 부담 완화 기대에 반등",
                        "원재료 가격 안정과 전기차 수요 회복 기대가 맞물리며 2차전지 업종에 저가 매수가 유입되고 있어요.",
                        "국내 2차전지 관련 종목들이 원재료 가격 안정 기대에 반등하고 있어요.\n\n아직 업황 회복 속도에는 차이가 있어 개별 기업의 수주와 재고 흐름을 함께 확인해야 합니다.",
                        LocalDateTime.of(2026, 5, 11, 10, 58)),
                fallback("news_004", NewsCategory.MARKET, false,
                        "원달러 환율, 미국 금리 경계에 제한적 상승",
                        "미국 물가 지표 발표를 앞두고 환율 변동성이 커졌지만, 수출주에는 일부 방어 요인으로 작용하고 있어요.",
                        "원달러 환율이 미국 금리 경계감에 소폭 상승하고 있어요.\n\n환율 상승은 수출 기업의 원화 환산 실적에는 긍정적일 수 있지만, 외국인 수급에는 부담이 될 수 있습니다.",
                        LocalDateTime.of(2026, 5, 11, 10, 20)),
                fallback("news_005", NewsCategory.OVERSEAS_STOCK, false,
                        "미국 빅테크, 클라우드 성장률 둔화 우려에도 강보합",
                        "클라우드 성장률은 완만해졌지만 AI 서비스 매출 기대가 주가 하단을 지지하고 있어요.",
                        "미국 빅테크 주가는 클라우드 성장률 둔화 우려에도 강보합 흐름을 보이고 있어요.\n\n투자자들은 기존 클라우드 매출보다 AI 서비스와 인프라 수익화 속도에 더 주목하고 있습니다.",
                        LocalDateTime.of(2026, 5, 11, 9, 45)),
                fallback("news_006", NewsCategory.DOMESTIC_STOCK, false,
                        "금융주, 배당 기대와 금리 전망 사이에서 혼조",
                        "배당 매력은 유지되고 있지만 금리 인하 시점 불확실성이 은행주 흐름을 엇갈리게 만들고 있어요.",
                        "국내 금융주는 배당 기대와 금리 전망 사이에서 혼조세를 보이고 있어요.\n\n안정적인 현금흐름을 선호하는 투자자에게는 관심이 이어지지만, 순이자마진 방향성은 계속 확인해야 합니다.",
                        LocalDateTime.of(2026, 5, 11, 9, 10))
        );
    }

    private NewsArticleView fallback(String id,
                                     NewsCategory category,
                                     boolean featured,
                                     String title,
                                     String summary,
                                     String body,
                                     LocalDateTime publishedAt) {
        return new NewsArticleView(
                id,
                category,
                category.label(),
                title,
                summary,
                body,
                DEFAULT_SOURCE,
                publishedAt,
                featured,
                null,
                null
        );
    }

    private Comparator<NewsArticleView> latestFirst() {
        return Comparator.comparing(NewsArticleView::publishedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private String buildNewsroomBody(FetchedNewsArticle article, NewsCategory category) {
        String title = defaultIfBlank(article.getTitle(), "이번 뉴스");
        String summary = defaultIfBlank(article.getSummary(), "네이버 뉴스 검색 API에서 확인한 최신 뉴스입니다.");
        String source = defaultIfBlank(article.getSourceName(), "네이버 뉴스");
        String categoryContext = switch (category) {
            case MARKET -> "시황 뉴스는 지수, 금리, 환율처럼 시장 전체 방향을 움직이는 재료를 먼저 확인하는 것이 좋습니다.";
            case DOMESTIC_STOCK -> "국내주식 뉴스는 코스피와 코스닥 업종 흐름, 대형주 수급, 기업 실적 기대를 함께 보는 것이 중요합니다.";
            case OVERSEAS_STOCK -> "해외주식 뉴스는 미국 증시와 빅테크 흐름이 국내 투자심리에도 이어질 수 있는지 살펴보면 좋습니다.";
            case ALL -> "뉴스를 볼 때는 제목보다 출처, 발행 시각, 시장에 미칠 영향을 함께 확인하는 것이 좋습니다.";
        };
        return "UniPort 뉴스룸은 네이버 뉴스 검색 API에서 확인한 '" + title + "' 흐름을 투자 입문자 관점으로 정리했어요."
                + "\n\n핵심 요약: " + summary
                + "\n\n" + categoryContext
                + "\n\n원문 전문은 저작권 보호를 위해 앱 안에 복제하지 않고, 출처인 " + source + "의 원문 링크에서 확인할 수 있게 연결합니다.";
    }

    private NewsCategory parseCategory(String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return NewsCategory.ALL;
        }
        String value = rawCategory.trim().toUpperCase(Locale.ROOT);
        try {
            return NewsCategory.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            throw new ApiException("Unsupported news category: " + rawCategory, HttpStatus.BAD_REQUEST);
        }
    }

    private NewsCategory resolveCategory(String rawCategory, String stockCode, String stockName) {
        if (rawCategory != null && !rawCategory.isBlank()) {
            NewsCategory parsed = parseCategory(rawCategory);
            return parsed == NewsCategory.ALL ? NewsCategory.MARKET : parsed;
        }
        String normalizedCode = stockCode != null ? stockCode.trim().toUpperCase(Locale.ROOT) : "";
        String normalizedName = stockName != null ? stockName.trim().toUpperCase(Locale.ROOT) : "";
        if (normalizedCode.equals("KOSPI") || normalizedCode.equals("KOSDAQ")
                || normalizedName.contains("KOSPI") || normalizedName.contains("KOSDAQ")) {
            return NewsCategory.MARKET;
        }
        if (!normalizedCode.isBlank() && normalizedCode.matches("\\d{6}")) {
            return NewsCategory.DOMESTIC_STOCK;
        }
        if (!normalizedCode.isBlank()) {
            return NewsCategory.OVERSEAS_STOCK;
        }
        return NewsCategory.MARKET;
    }

    private String normalizeNewsId(String newsId) {
        if (newsId == null || newsId.isBlank()) {
            throw new ApiException("News id is required", HttpStatus.BAD_REQUEST);
        }
        return newsId.trim();
    }

    private String toIso(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atZone(SEOUL_ZONE)
                .toOffsetDateTime()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private String defaultIfBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private enum RealtimeNewsCategory {
        ALL("전체"),
        MARKET("시황"),
        EARNINGS("실적"),
        POLICY("정책"),
        GEOPOLITICAL("지정학"),
        THEME("테마"),
        COMPANY("종목");

        private final String label;

        RealtimeNewsCategory(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private record NewsArticleView(
            String id,
            NewsCategory category,
            String categoryLabel,
            String title,
            String summary,
            String body,
            String sourceName,
            LocalDateTime publishedAt,
            boolean featured,
            String thumbnailUrl,
            String externalUrl
    ) {
    }

}
