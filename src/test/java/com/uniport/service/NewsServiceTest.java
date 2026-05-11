package com.uniport.service;

import com.uniport.dto.NewsListResponseDTO;
import com.uniport.dto.NewsShareRequestDTO;
import com.uniport.dto.NewsShareResponseDTO;
import com.uniport.dto.NewsItemResponseDTO;
import com.uniport.entity.ChatMessage;
import com.uniport.entity.ManagedNewsArticle;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.ManagedNewsArticleRepository;
import com.uniport.repository.MatchingRoomMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsServiceTest {

    private ManagedNewsArticleRepository newsRepository;
    private MatchingRoomMemberRepository matchingRoomMemberRepository;
    private ChatService chatService;
    private NewsFeedClient newsFeedClient;
    private NewsService newsService;

    @BeforeEach
    void setUp() {
        newsRepository = mock(ManagedNewsArticleRepository.class);
        matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        chatService = mock(ChatService.class);
        newsFeedClient = mock(NewsFeedClient.class);
        when(newsFeedClient.fetchLatest()).thenReturn(List.of());
        newsService = new NewsService(newsRepository, matchingRoomMemberRepository, chatService, newsFeedClient);
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
        return FetchedNewsArticle.builder()
                .id(id)
                .category(category)
                .title(id + " 제목")
                .summary(id + " 요약")
                .sourceName("네이버 뉴스")
                .publishedAt(publishedAt)
                .featured(featured)
                .externalUrl("https://example.com/" + id)
                .build();
    }
}
