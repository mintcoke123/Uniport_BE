package com.uniport.service;

import com.uniport.dto.NewsListResponseDTO;
import com.uniport.dto.NewsShareRequestDTO;
import com.uniport.dto.NewsShareResponseDTO;
import com.uniport.dto.NewsItemResponseDTO;
import com.uniport.dto.RealtimeNewsDetailResponseDTO;
import com.uniport.dto.RealtimeNewsListResponseDTO;
import com.uniport.entity.ChatMessage;
import com.uniport.entity.ManagedNewsArticle;
import com.uniport.entity.MatchingRoom;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.ManagedNewsArticleRepository;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsServiceTest {

    private ManagedNewsArticleRepository newsRepository;
    private MatchingRoomMemberRepository matchingRoomMemberRepository;
    private MatchingRoomRepository matchingRoomRepository;
    private ChatService chatService;
    private NewsFeedClient newsFeedClient;
    private NewsService newsService;

    @BeforeEach
    void setUp() {
        newsRepository = mock(ManagedNewsArticleRepository.class);
        matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        matchingRoomRepository = mock(MatchingRoomRepository.class);
        chatService = mock(ChatService.class);
        newsFeedClient = mock(NewsFeedClient.class);
        when(newsFeedClient.fetchLatest()).thenReturn(List.of());
        newsService = new NewsService(
                newsRepository,
                matchingRoomMemberRepository,
                matchingRoomRepository,
                chatService,
                newsFeedClient,
                new KeywordNewsSentimentAnalyzer()
        );
    }

    @Test
    void getNewsList_prefersNaverApiArticlesWhenAvailable() {
        when(newsFeedClient.fetchLatest()).thenReturn(List.of(
                fetched("naver_market_1", NewsCategory.MARKET, true, LocalDateTime.of(2026, 5, 11, 12, 20)),
                fetched("naver_domestic_1", NewsCategory.DOMESTIC_STOCK, false, LocalDateTime.of(2026, 5, 11, 12, 10))
        ));
        when(newsRepository.findAllByOrderByPublishedAtDescIdDesc()).thenReturn(List.of(
                article("managed_1", "MARKET", true, LocalDateTime.of(2026, 5, 11, 11, 0))
        ));

        NewsListResponseDTO response = newsService.getNewsList("ALL", 0, 20);

        assertEquals("naver_market_1", response.getFeatured().getId());
        assertEquals(List.of("naver_domestic_1"), response.getItems().stream().map(NewsItemResponseDTO::getId).toList());
    }

    @Test
    void getRealtimeNewsList_returnsPointBasedContractAndFiltersByRealtimeCategory() {
        when(newsFeedClient.fetchLatest()).thenReturn(List.of(
                fetchedWithTitle(
                        "hankyung_earnings_1",
                        NewsCategory.DOMESTIC_STOCK,
                        "삼성전자 실적 기대, 반도체 업황 회복 전망",
                        "영업이익 개선 기대가 커지며 투자 심리가 회복되고 있어요.",
                        "한국경제",
                        false,
                        LocalDateTime.of(2026, 5, 11, 16, 20)
                ),
                fetchedWithTitle(
                        "naver_policy_1",
                        NewsCategory.MARKET,
                        "정부, 밸류업 세제 정책 발표",
                        "정책 기대가 증시에 영향을 주고 있어요.",
                        "네이버 뉴스",
                        false,
                        LocalDateTime.of(2026, 5, 11, 16, 10)
                )
        ));

        RealtimeNewsListResponseDTO response = newsService.getRealtimeNewsList("EARNINGS", null, 20);

        assertEquals("COMPANY", response.getSelectedCategory());
        assertEquals(List.of("ALL", "MARKET", "THEME", "COMPANY"),
                response.getCategories().stream().map(category -> category.getCategory()).toList());
        assertEquals("hankyung_earnings_1", response.getHeroNews().getNewsId());
        assertEquals("COMPANY", response.getHeroNews().getCategory());
        assertEquals("종목", response.getHeroNews().getCategoryLabel());
        assertEquals("한국경제", response.getHeroNews().getSourceName());
        assertEquals(List.of("삼성전자"), response.getHeroNews().getRelatedStocks());
        assertEquals("POSITIVE", response.getHeroNews().getSentiment());
        assertEquals("호재", response.getHeroNews().getSentimentLabel());
        org.junit.jupiter.api.Assertions.assertTrue(response.getHeroNews().getSentimentReason().contains("긍정"));
        org.junit.jupiter.api.Assertions.assertFalse(response.getHeroNews().getInvestmentPoints().isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(response.getHeroNews().getRiskPoints().isEmpty());
        assertEquals(List.of(), response.getItems());
        assertEquals(false, response.getHasNext());
    }

    @Test
    void getRealtimeNewsList_classifiesNegativeSentimentFromActualArticleText() {
        when(newsFeedClient.fetchLatest()).thenReturn(List.of(
                fetchedWithTitle(
                        "naver_negative_1",
                        NewsCategory.DOMESTIC_STOCK,
                        "삼성전자 실적 쇼크 우려에 반도체 급락",
                        "영업이익 둔화와 차익실현 매물이 겹치며 투자 심리가 악화되고 있어요.",
                        "네이버 뉴스",
                        false,
                        LocalDateTime.of(2026, 5, 11, 16, 30)
                )
        ));

        RealtimeNewsListResponseDTO response = newsService.getRealtimeNewsList("ALL", null, 20);

        assertEquals("naver_negative_1", response.getHeroNews().getNewsId());
        assertEquals("NEGATIVE", response.getHeroNews().getSentiment());
        assertEquals("악재", response.getHeroNews().getSentimentLabel());
        org.junit.jupiter.api.Assertions.assertTrue(response.getHeroNews().getSentimentReason().contains("부정"));
        assertEquals(List.of("삼성전자"), response.getHeroNews().getRelatedStocks());
    }

    @Test
    void getRealtimeNewsList_companyIncludesMarketFeedArticleWhenStockIsMentioned() {
        when(newsFeedClient.fetchLatest()).thenReturn(List.of(
                fetchedWithTitle(
                        "naver_market_stock_1",
                        NewsCategory.MARKET,
                        "삼성전자 실적 쇼크 우려에 반도체 급락",
                        "영업이익 둔화와 차익실현 매물이 겹치며 투자 심리가 악화되고 있어요.",
                        "네이버 뉴스",
                        false,
                        LocalDateTime.of(2026, 5, 11, 16, 30)
                ),
                fetchedWithTitle(
                        "naver_market_plain_1",
                        NewsCategory.MARKET,
                        "코스피, 환율 안정에 상승 출발",
                        "외국인 순매수가 지수 흐름을 이끌고 있어요.",
                        "네이버 뉴스",
                        false,
                        LocalDateTime.of(2026, 5, 11, 16, 20)
                )
        ));

        RealtimeNewsListResponseDTO response = newsService.getRealtimeNewsList("COMPANY", null, 20);

        assertEquals("naver_market_stock_1", response.getHeroNews().getNewsId());
        assertEquals("COMPANY", response.getHeroNews().getCategory());
        assertEquals("종목", response.getHeroNews().getCategoryLabel());
        assertEquals(List.of("삼성전자"), response.getHeroNews().getRelatedStocks());
        assertEquals(List.of(), response.getItems());
    }

    @Test
    void getRealtimeNewsList_prefersFinbertAnalyzerResultOverKeywordFallback() {
        when(newsFeedClient.fetchLatest()).thenReturn(List.of(
                fetchedWithTitle(
                        "finbert_positive_1",
                        NewsCategory.DOMESTIC_STOCK,
                        "삼성전자 반등 기대",
                        "제목에는 기대가 있지만 모델은 문맥상 악재로 판단한 케이스예요.",
                        "네이버 뉴스",
                        false,
                        LocalDateTime.of(2026, 5, 11, 16, 40)
                )
        ));
        NewsService modelBackedNewsService = new NewsService(
                newsRepository,
                matchingRoomMemberRepository,
                matchingRoomRepository,
                chatService,
                newsFeedClient,
                input -> NewsSentimentAnalysis.negative(0.93, "FinBERT가 금융 문맥상 부정 신호로 분류했어요.")
        );

        RealtimeNewsListResponseDTO response = modelBackedNewsService.getRealtimeNewsList("ALL", null, 20);

        assertEquals("NEGATIVE", response.getHeroNews().getSentiment());
        assertEquals("악재", response.getHeroNews().getSentimentLabel());
        assertEquals(0.93, response.getHeroNews().getSentimentScore());
        org.junit.jupiter.api.Assertions.assertTrue(response.getHeroNews().getSentimentReason().contains("FinBERT"));
    }

    @Test
    void getRealtimeNewsDetail_returnsSummaryPointsRisksRelatedStocksAndSourceArticles() {
        when(newsFeedClient.fetchLatest()).thenReturn(List.of(
                fetchedWithTitle(
                        "hankyung_company_1",
                        NewsCategory.DOMESTIC_STOCK,
                        "삼성전자 반등, 반도체 투자 심리 회복",
                        "AI 서버 수요 기대가 반도체 밸류체인으로 이어지고 있어요.",
                        "한국경제",
                        false,
                        LocalDateTime.of(2026, 5, 11, 16, 10)
                )
        ));

        RealtimeNewsDetailResponseDTO response = newsService.getRealtimeNewsDetail("hankyung_company_1");

        assertEquals("hankyung_company_1", response.getNewsId());
        assertEquals("삼성전자 반등, 반도체 투자 심리 회복", response.getTitle());
        org.junit.jupiter.api.Assertions.assertTrue(response.getBody().contains("핵심 요약"));
        org.junit.jupiter.api.Assertions.assertTrue(response.getCoreSummary().contains("AI 서버 수요 기대"));
        assertFalse(response.getSummary().equals(response.getCoreSummary()));
        assertEquals("POSITIVE", response.getSentiment());
        assertEquals("호재", response.getSentimentLabel());
        org.junit.jupiter.api.Assertions.assertTrue(response.getSentimentReason().contains("긍정"));
        org.junit.jupiter.api.Assertions.assertFalse(response.getInvestmentPoints().isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(response.getRiskPoints().isEmpty());
        assertEquals("삼성전자", response.getRelatedStocks().get(0).getName());
        assertEquals("005930", response.getRelatedStocks().get(0).getSymbol());
        assertEquals("한국경제", response.getSourceArticles().get(0).getSourceName());
        assertEquals("https://example.com/hankyung_company_1", response.getSourceArticles().get(0).getExternalUrl());
    }

    @Test
    void getRealtimeNewsDetail_omitsCoreSummaryWhenItOnlyDuplicatesSummary() {
        ManagedNewsArticle article = article("news_duplicate_summary", "DOMESTIC_STOCK", false,
                LocalDateTime.of(2026, 5, 11, 10, 15));
        article.setTitle("삼성전자 반등");
        article.setSummary("반도체 투자 심리가 회복되고 있어요.");
        article.setContent("반도체 투자 심리가 회복되고 있어요.");
        when(newsRepository.findByNewsKey("news_duplicate_summary")).thenReturn(Optional.of(article));

        RealtimeNewsDetailResponseDTO response = newsService.getRealtimeNewsDetail("news_duplicate_summary");

        assertEquals("반도체 투자 심리가 회복되고 있어요.", response.getSummary());
        assertEquals("반도체 투자 심리가 회복되고 있어요.", response.getBody());
        assertNull(response.getCoreSummary());
    }

    @Test
    void getRealtimeNewsDetail_fetchesFetchedArticleFullContentOnDemand() {
        String fullContent = "첫 번째 원문 본문 문단입니다.\n\n두 번째 원문 본문 문단입니다.";
        when(newsFeedClient.fetchLatest()).thenReturn(List.of(
                FetchedNewsArticle.builder()
                        .id("naver_full_text")
                        .category(NewsCategory.MARKET)
                        .title("코스피 상승 출발")
                        .summary("검색 API 요약입니다.")
                        .content("")
                        .sourceName("테스트경제")
                        .publishedAt(LocalDateTime.of(2026, 5, 11, 12, 20))
                        .featured(true)
                        .externalUrl("https://example.com/full-text")
                        .build()
        ));
        when(newsFeedClient.fetchArticleContent("https://example.com/full-text")).thenReturn(fullContent);

        RealtimeNewsDetailResponseDTO response = newsService.getRealtimeNewsDetail("naver_full_text");

        assertEquals(fullContent, response.getBody());
        assertEquals(fullContent, response.getCoreSummary());
        verify(newsFeedClient).fetchArticleContent("https://example.com/full-text");
    }

    @Test
    void getNewsDetail_canResolveNaverApiArticleById() {
        when(newsFeedClient.fetchLatest()).thenReturn(List.of(
                fetched("naver_overseas_1", NewsCategory.OVERSEAS_STOCK, false, LocalDateTime.of(2026, 5, 11, 12, 20))
        ));

        NewsItemResponseDTO response = newsService.getNewsDetail("naver_overseas_1");

        assertEquals("naver_overseas_1", response.getId());
        assertEquals("https://example.com/naver_overseas_1", response.getExternalUrl());
        org.junit.jupiter.api.Assertions.assertTrue(response.getBody().contains("UniPort 뉴스룸"));
        org.junit.jupiter.api.Assertions.assertTrue(response.getBody().contains("naver_overseas_1 요약"));
    }

    @Test
    void getNewsList_filtersByCategoryAndReturnsFeaturedSeparately() {
        ManagedNewsArticle market = article("news_market", "MARKET", true, LocalDateTime.of(2026, 5, 11, 11, 48));
        ManagedNewsArticle domestic = article("news_domestic", "DOMESTIC_STOCK", false, LocalDateTime.of(2026, 5, 11, 11, 32));
        when(newsRepository.findAllByOrderByPublishedAtDescIdDesc()).thenReturn(List.of(market, domestic));

        NewsListResponseDTO response = newsService.getNewsList("MARKET", 0, 20);

        assertEquals("news_market", response.getFeatured().getId());
        assertEquals("MARKET", response.getFeatured().getCategory());
        assertEquals("시황", response.getFeatured().getCategoryLabel());
        assertEquals("2026-05-11T11:48:00+09:00", response.getFeatured().getPublishedAt());
        assertEquals(List.of(), response.getItems());
        assertFalse(response.getHasNext());
    }

    @Test
    void getNewsList_paginatesNonFeaturedItemsLatestFirst() {
        ManagedNewsArticle featured = article("news_featured", "MARKET", true, LocalDateTime.of(2026, 5, 11, 12, 0));
        ManagedNewsArticle first = article("news_first", "OVERSEAS_STOCK", false, LocalDateTime.of(2026, 5, 11, 11, 50));
        ManagedNewsArticle second = article("news_second", "DOMESTIC_STOCK", false, LocalDateTime.of(2026, 5, 11, 11, 40));
        ManagedNewsArticle third = article("news_third", "MARKET", false, LocalDateTime.of(2026, 5, 11, 11, 30));
        when(newsRepository.findAllByOrderByPublishedAtDescIdDesc()).thenReturn(List.of(featured, first, second, third));

        NewsListResponseDTO response = newsService.getNewsList("ALL", 0, 2);

        assertEquals("news_featured", response.getFeatured().getId());
        assertEquals(List.of("news_first", "news_second"), response.getItems().stream().map(NewsItemResponseDTO::getId).toList());
        assertEquals(0, response.getPage());
        assertEquals(2, response.getSize());
        assertEquals(true, response.getHasNext());
    }

    @Test
    void getNewsDetail_returnsBodyAndMetadataByNewsKey() {
        ManagedNewsArticle article = article("news_detail", "OVERSEAS_STOCK", false, LocalDateTime.of(2026, 5, 11, 10, 15));
        article.setContent("첫 번째 문단입니다.\n\n두 번째 문단입니다.");
        article.setExternalUrl("https://example.com/news/detail");
        when(newsRepository.findByNewsKey("news_detail")).thenReturn(Optional.of(article));

        NewsItemResponseDTO response = newsService.getNewsDetail("news_detail");

        assertEquals("news_detail", response.getId());
        assertEquals("해외주식", response.getCategoryLabel());
        assertEquals("첫 번째 문단입니다.\n\n두 번째 문단입니다.", response.getBody());
        assertEquals("https://example.com/news/detail", response.getExternalUrl());
    }

    @Test
    void shareNews_savesNewsShareMessageOnlyWhenUserBelongsToChatRoom() {
        User user = User.builder()
                .nickname("뉴스공유러")
                .build();
        user.setId(7L);
        ManagedNewsArticle article = article("news_share", "DOMESTIC_STOCK", false, LocalDateTime.of(2026, 5, 11, 9, 0));
        when(newsRepository.findByNewsKey("news_share")).thenReturn(Optional.of(article));
        when(matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(3L, 7L)).thenReturn(true);
        when(chatService.saveNewsShareMessage(eq(3L), eq(7L), eq("뉴스공유러"), any()))
                .thenReturn(ChatMessage.builder()
                        .id(99L)
                        .roomId(3L)
                        .userId(7L)
                        .userNickname("뉴스공유러")
                        .createdAt(Instant.parse("2026-05-11T03:00:00Z"))
                        .build());

        NewsShareResponseDTO response = newsService.shareNews(3L, user, NewsShareRequestDTO.builder().newsId("news_share").build());

        assertEquals(99L, response.getMessageId());
        assertEquals(3L, response.getChatRoomId());
        assertEquals("NEWS_SHARE", response.getType());
        assertEquals("news_share", response.getNews().getId());
        assertEquals("UniPort Markets", response.getNews().getSourceName());
        assertEquals("2026-05-11T09:00:00+09:00", response.getNews().getPublishedAt());
        assertEquals("호재", response.getNews().getSentimentLabel());
        org.junit.jupiter.api.Assertions.assertTrue(response.getNews().getSentimentReason().contains("긍정"));
        org.junit.jupiter.api.Assertions.assertFalse(response.getNews().getInvestmentPoints().isEmpty());
        verify(chatService).saveNewsShareMessage(eq(3L), eq(7L), eq("뉴스공유러"), any());
    }

    @Test
    void shareNews_rejectsUserOutsideChatRoom() {
        User user = User.builder().nickname("비회원").build();
        user.setId(7L);
        when(matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(3L, 7L)).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class,
                () -> newsService.shareNews(3L, user, NewsShareRequestDTO.builder().newsId("news_share").build()));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void shareNews_rejectsEndedChatRoomBeforeSavingMessage() {
        User user = User.builder().nickname("뉴스공유러").build();
        user.setId(7L);
        MatchingRoom endedRoom = MatchingRoom.builder()
                .id(3L)
                .capacity(3)
                .status("ended")
                .build();
        when(matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(3L, 7L)).thenReturn(true);
        when(matchingRoomRepository.findById(3L)).thenReturn(Optional.of(endedRoom));

        ApiException exception = assertThrows(ApiException.class,
                () -> newsService.shareNews(3L, user, NewsShareRequestDTO.builder().newsId("news_share").build()));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("종료된 채팅방은 보기만 할 수 있습니다.", exception.getMessage());
        verify(chatService, never()).saveNewsShareMessage(eq(3L), eq(7L), eq("뉴스공유러"), any());
    }

    private ManagedNewsArticle article(String key, String category, boolean featured, LocalDateTime publishedAt) {
        return ManagedNewsArticle.builder()
                .newsKey(key)
                .category(category)
                .featured(featured)
                .title(key + " 제목")
                .summary(key + " 요약")
                .sourceLabel("UniPort Markets")
                .publishedAt(publishedAt)
                .imageUrl(null)
                .content(key + " 본문")
                .build();
    }

    private FetchedNewsArticle fetched(String id, NewsCategory category, boolean featured, LocalDateTime publishedAt) {
        return fetchedWithTitle(id, category, id + " 제목", id + " 요약", "네이버 뉴스", featured, publishedAt);
    }

    private FetchedNewsArticle fetchedWithTitle(String id,
                                                NewsCategory category,
                                                String title,
                                                String summary,
                                                String sourceName,
                                                boolean featured,
                                                LocalDateTime publishedAt) {
        return FetchedNewsArticle.builder()
                .id(id)
                .category(category)
                .title(title)
                .summary(summary)
                .sourceName(sourceName)
                .publishedAt(publishedAt)
                .featured(featured)
                .externalUrl("https://example.com/" + id)
                .build();
    }
}
