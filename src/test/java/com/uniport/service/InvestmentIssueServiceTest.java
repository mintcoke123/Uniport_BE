package com.uniport.service;

import com.uniport.dto.InvestmentIssueDetailResponseDTO;
import com.uniport.dto.InvestmentIssueItemDTO;
import com.uniport.dto.InvestmentIssueListResponseDTO;
import com.uniport.dto.InvestmentIssueShareRequestDTO;
import com.uniport.dto.InvestmentIssueShareResponseDTO;
import com.uniport.entity.ChatMessage;
import com.uniport.entity.MatchingRoom;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestmentIssueServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 5, 19, 9, 0);

    @Test
    void getIssueList_acceptsAllSupportedCategories() {
        InvestmentIssueService investmentIssueService = service(new FakeNewsFeedClient(List.of(List.of())));

        for (String category : List.of("ALL", "MARKET", "THEME", "COMPANY", "OVERSEAS")) {
            InvestmentIssueListResponseDTO response = investmentIssueService.getIssueList(category, null, 20);

            assertEquals(category, response.getSelectedCategory());
        }
    }

    @Test
    void getIssueList_defaultsNullAndBlankCategoryToAll() {
        InvestmentIssueService investmentIssueService = service(new FakeNewsFeedClient(List.of(List.of())));

        assertEquals("ALL", investmentIssueService.getIssueList(null, null, null).getSelectedCategory());
        assertEquals("ALL", investmentIssueService.getIssueList("   ", null, null).getSelectedCategory());
    }

    @Test
    void getIssueList_rejectsUnsupportedCategoryWithBadRequest() {
        InvestmentIssueService investmentIssueService = service(new FakeNewsFeedClient(List.of(List.of())));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> investmentIssueService.getIssueList("CRYPTO", null, null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("BAD_REQUEST", exception.getErrorCode());
        assertEquals("unsupported investment issue category: CRYPTO", exception.getMessage());
    }

    @Test
    void getIssueList_returnsIssueFeedWithHeroAndSourceCount() {
        InvestmentIssueService investmentIssueService = service(new FakeNewsFeedClient(List.of(hbmArticles())));

        InvestmentIssueListResponseDTO response = investmentIssueService.getIssueList("THEME", null, 20);

        assertEquals(List.of("ALL", "MARKET", "THEME", "COMPANY", "OVERSEAS"),
                response.getCategories().stream().map(category -> category.getCategory()).toList());
        assertEquals(List.of("전체", "시황", "테마", "종목", "해외"),
                response.getCategories().stream().map(category -> category.getLabel()).toList());
        assertEquals("THEME", response.getSelectedCategory());
        assertNotNull(response.getHeroIssue());
        assertTrue(response.getHeroIssue().getIssueId().startsWith("issue_"));
        assertEquals("positive", response.getHeroIssue().getLabel());
        assertEquals("호재", response.getHeroIssue().getLabelText());
        assertEquals(2, response.getHeroIssue().getSourceCount());
        assertEquals(List.of(), response.getItems());
        assertNull(response.getNextCursor());
        assertFalse(response.getHasNext());
    }

    @Test
    void getIssueDetail_returnsMultipleSourceArticlesWithoutBodyCopy() {
        InvestmentIssueService investmentIssueService = service(new FakeNewsFeedClient(List.of(hbmArticles())));
        String issueId = investmentIssueService.getIssueList("THEME", null, 20)
                .getHeroIssue()
                .getIssueId();

        InvestmentIssueDetailResponseDTO detail = investmentIssueService.getIssueDetail(issueId);

        assertEquals(issueId, detail.getIssueId());
        assertEquals(2, detail.getSourceArticles().size());
        assertTrue(detail.getSourceArticles().stream()
                .allMatch(article -> article.getExternalUrl() != null && !article.getExternalUrl().isBlank()));
        assertFalse(detail.getBody().contains("첫 번째 본문 문단"));
        assertFalse(detail.getBody().contains("두 번째 본문 문단"));
    }

    @Test
    void getIssueDetail_keepsListAndDetailLabelsConsistentForNegativeIssue() {
        InvestmentIssueService investmentIssueService = service(new FakeNewsFeedClient(List.of(List.of(
                article("shock-1", NewsCategory.DOMESTIC_STOCK,
                        "삼성전자 실적 쇼크에 급락",
                        "영업이익 감소와 비용 부담 우려가 커졌다는 요약",
                        "실적 쇼크 본문",
                        BASE_TIME),
                article("shock-2", NewsCategory.DOMESTIC_STOCK,
                        "삼성전자 실적 쇼크 우려 지속",
                        "실적 부진과 비용 부담이 이어진다는 요약",
                        "추가 본문",
                        BASE_TIME.plusMinutes(5))
        ))));

        InvestmentIssueItemDTO listItem = investmentIssueService.getIssueList("COMPANY", null, 20).getHeroIssue();
        InvestmentIssueDetailResponseDTO detail = investmentIssueService.getIssueDetail(listItem.getIssueId());

        assertEquals("negative", listItem.getLabel());
        assertEquals("악재", listItem.getLabelText());
        assertEquals(listItem.getLabel(), detail.getLabel());
        assertEquals(listItem.getLabelText(), detail.getLabelText());
    }

    @Test
    void getIssueDetail_bodyIsGeneratedExplanationNotRawSnippetJoinOrPlaceholder() {
        InvestmentIssueService investmentIssueService = service(new FakeNewsFeedClient(List.of(hbmArticles())));
        String issueId = investmentIssueService.getIssueList("THEME", null, 20)
                .getHeroIssue()
                .getIssueId();

        InvestmentIssueDetailResponseDTO detail = investmentIssueService.getIssueDetail(issueId);

        assertNotNull(detail.getBody());
        assertFalse(detail.getBody().isBlank());
        assertFalse(detail.getBody().contains("..."));
        assertNotEquals(
                "AI 서버 투자 확대와 HBM 수요 증가 기대 요약\nHBM 장비 투자 확대와 반도체주 강세 요약",
                detail.getBody()
        );
    }

    @Test
    void getIssueList_filtersCategoryAndPaginatesItemsAfterCursor() {
        InvestmentIssueService investmentIssueService = service(new FakeNewsFeedClient(List.of(List.of(
                article("hbm-newest", NewsCategory.DOMESTIC_STOCK,
                        "HBM 수요 폭발에 AI 반도체주 상승",
                        "AI 서버 투자 확대와 HBM 수요 증가 기대 요약",
                        "본문",
                        BASE_TIME.plusHours(3)),
                article("fx-middle", NewsCategory.MARKET,
                        "환율 상승 전망에 증시 관망",
                        "원달러 환율 상승과 업종별 영향이 엇갈린다는 요약",
                        "본문",
                        BASE_TIME.plusHours(2)),
                article("nvidia-oldest", NewsCategory.OVERSEAS_STOCK,
                        "엔비디아 실적 쇼크 우려에 급락",
                        "엔비디아 실적 둔화 우려가 부각됐다는 요약",
                        "본문",
                        BASE_TIME.plusHours(1))
        ))));

        InvestmentIssueListResponseDTO marketResponse = investmentIssueService.getIssueList("MARKET", null, 20);
        assertEquals("MARKET", marketResponse.getSelectedCategory());
        assertNotNull(marketResponse.getHeroIssue());
        assertEquals("MARKET", marketResponse.getHeroIssue().getCategory());

        InvestmentIssueListResponseDTO firstPage = investmentIssueService.getIssueList("ALL", null, 1);
        assertEquals("ALL", firstPage.getSelectedCategory());
        assertNotNull(firstPage.getHeroIssue());
        assertEquals("THEME", firstPage.getHeroIssue().getCategory());
        assertEquals(1, firstPage.getItems().size());
        assertEquals("MARKET", firstPage.getItems().get(0).getCategory());
        assertTrue(firstPage.getHasNext());
        assertEquals(firstPage.getItems().get(0).getIssueId(), firstPage.getNextCursor());

        InvestmentIssueListResponseDTO secondPage = investmentIssueService.getIssueList(
                "ALL",
                firstPage.getNextCursor(),
                1
        );
        assertEquals(firstPage.getHeroIssue().getIssueId(), secondPage.getHeroIssue().getIssueId());
        assertEquals(1, secondPage.getItems().size());
        assertEquals("OVERSEAS", secondPage.getItems().get(0).getCategory());
        assertFalse(secondPage.getHasNext());
        assertNull(secondPage.getNextCursor());
    }

    @Test
    void getIssueList_usesCacheWithinTtlAndRefetchesAfterExpiry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-19T00:00:00Z"), ZoneOffset.UTC);
        FakeNewsFeedClient feedClient = new FakeNewsFeedClient(List.of(
                hbmArticles(),
                List.of(article("fx-refetched", NewsCategory.MARKET,
                        "환율 상승 전망",
                        "환율 상승에 업종별 영향이 엇갈린다는 요약",
                        "새 본문",
                        BASE_TIME.plusHours(4)))
        ));
        InvestmentIssueService investmentIssueService = service(feedClient, Duration.ofSeconds(60), clock);

        InvestmentIssueListResponseDTO first = investmentIssueService.getIssueList("ALL", null, 20);
        InvestmentIssueListResponseDTO cached = investmentIssueService.getIssueList("ALL", null, 20);
        assertEquals(1, feedClient.fetchCount());
        assertEquals(first.getHeroIssue().getIssueId(), cached.getHeroIssue().getIssueId());

        clock.advance(Duration.ofSeconds(61));
        InvestmentIssueListResponseDTO refetched = investmentIssueService.getIssueList("ALL", null, 20);

        assertEquals(2, feedClient.fetchCount());
        assertEquals("MARKET", refetched.getHeroIssue().getCategory());
    }

    @Test
    void getIssueDetail_usesStaleCacheWhenRefreshMissesPriorIssueAfterTtl() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-19T00:00:00Z"), ZoneOffset.UTC);
        FakeNewsFeedClient feedClient = new FakeNewsFeedClient(List.of(
                hbmArticles(),
                List.of()
        ));
        InvestmentIssueService investmentIssueService = service(feedClient, Duration.ofSeconds(60), clock);
        String issueId = investmentIssueService.getIssueList("ALL", null, 20)
                .getHeroIssue()
                .getIssueId();

        clock.advance(Duration.ofSeconds(61));
        InvestmentIssueListResponseDTO refreshedList = investmentIssueService.getIssueList("ALL", null, 20);
        InvestmentIssueDetailResponseDTO detail = investmentIssueService.getIssueDetail(issueId);

        assertEquals(2, feedClient.fetchCount());
        assertNull(refreshedList.getHeroIssue());
        assertEquals(issueId, detail.getIssueId());
        assertEquals(2, detail.getSourceArticles().size());
    }

    @Test
    void getIssueDetail_synchronizesCacheLookupWithRefreshMutation() throws NoSuchMethodException {
        int modifiers = InvestmentIssueService.class
                .getMethod("getIssueDetail", String.class)
                .getModifiers();

        assertTrue(Modifier.isSynchronized(modifiers));
    }

    @Test
    void getIssueList_keepsIssueIdDateStableWhenClusterGrowsAcrossMidnight() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-19T14:50:00Z"), ZoneOffset.UTC);
        FakeNewsFeedClient feedClient = new FakeNewsFeedClient(List.of(
                List.of(article("hbm-night-1", NewsCategory.DOMESTIC_STOCK,
                        "HBM 수요 폭발에 AI 반도체주 상승",
                        "AI 서버 투자 확대와 HBM 수요 증가 기대 요약",
                        "첫 번째 본문",
                        LocalDateTime.of(2026, 5, 19, 23, 50))),
                List.of(
                        article("hbm-night-1", NewsCategory.DOMESTIC_STOCK,
                                "HBM 수요 폭발에 AI 반도체주 상승",
                                "AI 서버 투자 확대와 HBM 수요 증가 기대 요약",
                                "첫 번째 본문",
                                LocalDateTime.of(2026, 5, 19, 23, 50)),
                        article("hbm-night-2", NewsCategory.MARKET,
                                "AI 반도체 ETF, HBM 기대감에 강세",
                                "HBM 장비 투자 확대와 반도체주 강세 요약",
                                "두 번째 본문",
                                LocalDateTime.of(2026, 5, 20, 0, 10))
                )
        ));
        InvestmentIssueService investmentIssueService = service(feedClient, Duration.ofSeconds(60), clock);

        String initialIssueId = investmentIssueService.getIssueList("ALL", null, 20)
                .getHeroIssue()
                .getIssueId();
        clock.advance(Duration.ofSeconds(61));
        String refreshedIssueId = investmentIssueService.getIssueList("ALL", null, 20)
                .getHeroIssue()
                .getIssueId();

        assertTrue(initialIssueId.startsWith("issue_20260519_"));
        assertEquals(initialIssueId, refreshedIssueId);
    }

    @Test
    void getIssueListAndDetail_formatTimestampsWithKstOffset() {
        InvestmentIssueService investmentIssueService = service(new FakeNewsFeedClient(List.of(hbmArticles())));

        InvestmentIssueItemDTO heroIssue = investmentIssueService.getIssueList("THEME", null, 20).getHeroIssue();
        InvestmentIssueDetailResponseDTO detail = investmentIssueService.getIssueDetail(heroIssue.getIssueId());

        assertEquals("2026-05-19T09:00:00+09:00", heroIssue.getPublishedAt());
        assertEquals("2026-05-19T09:05:00+09:00", heroIssue.getUpdatedAt());
        assertEquals("2026-05-19T09:00:00+09:00", detail.getPublishedAt());
        assertEquals("2026-05-19T09:05:00+09:00", detail.getUpdatedAt());
        assertEquals("2026-05-19T09:00:00+09:00", detail.getSourceArticles().get(0).getPublishedAt());
    }

    @Test
    void getIssueList_appliesConfiguredDisplayLimits() {
        InvestmentIssueService investmentIssueService = service(
                new FakeNewsFeedClient(List.of(hbmArticles())),
                Duration.ofSeconds(300),
                Clock.fixed(Instant.parse("2026-05-19T00:00:00Z"), KST),
                1,
                1,
                1,
                1
        );

        InvestmentIssueItemDTO heroIssue = investmentIssueService.getIssueList("THEME", null, 20).getHeroIssue();

        assertEquals(1, heroIssue.getReasonBullets().size());
        assertEquals(1, heroIssue.getWatchPoints().size());
        assertEquals(1, heroIssue.getRelatedStocks().size());
        assertEquals(1, heroIssue.getRelatedEtfs().size());
    }

    @Test
    void getIssueDetail_throwsNotFoundWhenIssueIdIsNotInCurrentCache() {
        InvestmentIssueService investmentIssueService = service(new FakeNewsFeedClient(List.of(hbmArticles())));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> investmentIssueService.getIssueDetail("issue_missing")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("NOT_FOUND", exception.getErrorCode());
        assertEquals("investment issue not found", exception.getMessage());
    }

    @Test
    void shareInvestmentIssue_savesIssueShareMessageOnlyWhenUserBelongsToChatRoom() {
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        ChatService chatService = mock(ChatService.class);
        InvestmentIssueService investmentIssueService = service(
                new FakeNewsFeedClient(List.of(hbmArticles())),
                matchingRoomMemberRepository,
                matchingRoomRepository,
                chatService
        );
        User user = User.builder().nickname("이슈공유러").build();
        user.setId(7L);
        String issueId = investmentIssueService.getIssueList("THEME", null, 20)
                .getHeroIssue()
                .getIssueId();
        when(matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(3L, 7L)).thenReturn(true);
        when(chatService.saveInvestmentIssueShareMessage(eq(3L), eq(7L), eq("이슈공유러"), any()))
                .thenReturn(ChatMessage.builder()
                        .id(99L)
                        .roomId(3L)
                        .userId(7L)
                        .userNickname("이슈공유러")
                        .createdAt(Instant.parse("2026-05-19T03:00:00Z"))
                        .build());

        InvestmentIssueShareResponseDTO response = investmentIssueService.shareInvestmentIssue(
                3L,
                user,
                InvestmentIssueShareRequestDTO.builder().issueId(issueId).build()
        );

        assertEquals(99L, response.getMessageId());
        assertEquals(3L, response.getChatRoomId());
        assertEquals(ChatService.TYPE_INVESTMENT_ISSUE_SHARE, response.getType());
        assertEquals(issueId, response.getIssue().getIssueId());
        assertEquals("positive", response.getIssue().getLabel());
        assertEquals("호재", response.getIssue().getLabelText());
        assertTrue(response.getIssue().getRelatedStocks().contains("삼성전자"));
        assertEquals(2, response.getIssue().getSourceCount());
        verify(chatService).saveInvestmentIssueShareMessage(eq(3L), eq(7L), eq("이슈공유러"), any());
    }

    @Test
    void shareInvestmentIssue_rejectsUserOutsideChatRoom() {
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        ChatService chatService = mock(ChatService.class);
        InvestmentIssueService investmentIssueService = service(
                new FakeNewsFeedClient(List.of(hbmArticles())),
                matchingRoomMemberRepository,
                matchingRoomRepository,
                chatService
        );
        User user = User.builder().nickname("비회원").build();
        user.setId(7L);
        when(matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(3L, 7L)).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class,
                () -> investmentIssueService.shareInvestmentIssue(
                        3L,
                        user,
                        InvestmentIssueShareRequestDTO.builder()
                                .issueId("issue_20260519_hbm_semiconductor_8f3a12")
                                .build()
                ));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verify(chatService, never()).saveInvestmentIssueShareMessage(eq(3L), eq(7L), eq("비회원"), any());
    }

    @Test
    void shareInvestmentIssue_rejectsEndedChatRoomBeforeSavingMessage() {
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        ChatService chatService = mock(ChatService.class);
        InvestmentIssueService investmentIssueService = service(
                new FakeNewsFeedClient(List.of(hbmArticles())),
                matchingRoomMemberRepository,
                matchingRoomRepository,
                chatService
        );
        User user = User.builder().nickname("이슈공유러").build();
        user.setId(7L);
        MatchingRoom endedRoom = MatchingRoom.builder()
                .id(3L)
                .capacity(3)
                .status("ended")
                .build();
        when(matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(3L, 7L)).thenReturn(true);
        when(matchingRoomRepository.findById(3L)).thenReturn(java.util.Optional.of(endedRoom));

        ApiException exception = assertThrows(ApiException.class,
                () -> investmentIssueService.shareInvestmentIssue(
                        3L,
                        user,
                        InvestmentIssueShareRequestDTO.builder()
                                .issueId("issue_20260519_hbm_semiconductor_8f3a12")
                                .build()
                ));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("종료된 채팅방은 보기만 할 수 있습니다.", exception.getMessage());
        verify(chatService, never()).saveInvestmentIssueShareMessage(eq(3L), eq(7L), eq("이슈공유러"), any());
    }

    private InvestmentIssueService service(FakeNewsFeedClient newsFeedClient) {
        return service(newsFeedClient, Duration.ofSeconds(300), Clock.fixed(
                Instant.parse("2026-05-19T00:00:00Z"),
                KST
        ));
    }

    private InvestmentIssueService service(FakeNewsFeedClient newsFeedClient, Duration ttl, Clock clock) {
        return service(newsFeedClient, ttl, clock, 3, 2, 5, 3);
    }

    private InvestmentIssueService service(FakeNewsFeedClient newsFeedClient,
                                           MatchingRoomMemberRepository matchingRoomMemberRepository,
                                           MatchingRoomRepository matchingRoomRepository,
                                           ChatService chatService) {
        return new InvestmentIssueService(
                newsFeedClient,
                new RawNewsDeduplicator(),
                new IssueClusterService(new RawNewsNormalizer()),
                new InvestmentIssueAnalyzer(new StockMappingService(), new EtfMappingService()),
                matchingRoomMemberRepository,
                matchingRoomRepository,
                chatService,
                Duration.ofSeconds(300),
                Clock.fixed(Instant.parse("2026-05-19T00:00:00Z"), KST),
                3,
                2,
                5,
                3
        );
    }

    private InvestmentIssueService service(FakeNewsFeedClient newsFeedClient,
                                           Duration ttl,
                                           Clock clock,
                                           int maxReasonBullets,
                                           int maxWatchPoints,
                                           int maxRelatedStocks,
                                           int maxRelatedEtfs) {
        return new InvestmentIssueService(
                newsFeedClient,
                new RawNewsDeduplicator(),
                new IssueClusterService(new RawNewsNormalizer()),
                new InvestmentIssueAnalyzer(new StockMappingService(), new EtfMappingService()),
                ttl,
                clock,
                maxReasonBullets,
                maxWatchPoints,
                maxRelatedStocks,
                maxRelatedEtfs
        );
    }

    private static List<FetchedNewsArticle> hbmArticles() {
        return List.of(
                article("hbm-1", NewsCategory.DOMESTIC_STOCK,
                        "HBM 수요 폭발에 AI 반도체주 상승",
                        "AI 서버 투자 확대와 HBM 수요 증가 기대 요약",
                        "첫 번째 본문 문단",
                        BASE_TIME),
                article("hbm-2", NewsCategory.MARKET,
                        "AI 반도체 ETF, HBM 기대감에 강세",
                        "HBM 장비 투자 확대와 반도체주 강세 요약",
                        "두 번째 본문 문단",
                        BASE_TIME.plusMinutes(5))
        );
    }

    private static FetchedNewsArticle article(String id,
                                             NewsCategory category,
                                             String title,
                                             String summary,
                                             String content,
                                             LocalDateTime publishedAt) {
        return FetchedNewsArticle.builder()
                .id(id)
                .category(category)
                .title(title)
                .summary(summary)
                .content(content)
                .sourceName("테스트뉴스")
                .publishedAt(publishedAt)
                .externalUrl("https://example.com/news/" + id)
                .build();
    }

    private static final class FakeNewsFeedClient implements NewsFeedClient {

        private final List<List<FetchedNewsArticle>> responses;
        private int fetchCount;

        private FakeNewsFeedClient(List<List<FetchedNewsArticle>> responses) {
            this.responses = new ArrayList<>(responses);
        }

        @Override
        public List<FetchedNewsArticle> fetchLatest() {
            int index = Math.min(fetchCount, responses.size() - 1);
            fetchCount++;
            return responses.get(index);
        }

        private int fetchCount() {
            return fetchCount;
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
