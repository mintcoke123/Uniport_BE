package com.uniport.service;

import com.uniport.dto.NewsItemResponseDTO;
import com.uniport.dto.NewsListResponseDTO;
import com.uniport.dto.NewsSharePreviewDTO;
import com.uniport.dto.NewsShareRequestDTO;
import com.uniport.dto.NewsShareResponseDTO;
import com.uniport.entity.ChatMessage;
import com.uniport.entity.ManagedNewsArticle;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.ManagedNewsArticleRepository;
import com.uniport.repository.MatchingRoomMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class NewsService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final String DEFAULT_SOURCE = "UniPort Markets";

    private final ManagedNewsArticleRepository managedNewsArticleRepository;
    private final MatchingRoomMemberRepository matchingRoomMemberRepository;
    private final ChatService chatService;
    private final NewsFeedClient newsFeedClient;

    public NewsService(ManagedNewsArticleRepository managedNewsArticleRepository,
                       MatchingRoomMemberRepository matchingRoomMemberRepository,
                       ChatService chatService,
                       NewsFeedClient newsFeedClient) {
        this.managedNewsArticleRepository = managedNewsArticleRepository;
        this.matchingRoomMemberRepository = matchingRoomMemberRepository;
        this.chatService = chatService;
        this.newsFeedClient = newsFeedClient;
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
        String normalizedNewsId = normalizeNewsId(newsId);
        Optional<NewsArticleView> fetchedArticle = loadFetchedArticles().stream()
                .filter(article -> article.id().equals(normalizedNewsId))
                .findFirst();
        if (fetchedArticle.isPresent()) {
            return toItem(fetchedArticle.get(), true, fetchedArticle.get().featured());
        }

        Optional<ManagedNewsArticle> managedArticle = findManagedArticle(normalizedNewsId);
        if (managedArticle.isPresent()) {
            NewsArticleView view = toView(managedArticle.get());
            return toItem(view, true, view.featured());
        }

        return loadFallbackArticles().stream()
                .filter(article -> article.id().equals(normalizedNewsId))
                .findFirst()
                .map(article -> toItem(article, true, article.featured()))
                .orElseThrow(() -> new ApiException("News article not found", HttpStatus.NOT_FOUND));
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

        String newsId = request != null ? request.getNewsId() : null;
        NewsItemResponseDTO news = getNewsDetail(newsId);
        NewsSharePreviewDTO preview = NewsSharePreviewDTO.builder()
                .id(news.getId())
                .categoryLabel(news.getCategoryLabel())
                .title(news.getTitle())
                .summary(news.getSummary())
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
