package com.uniport.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLDecoder;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NaverNewsFeedClientTest {

    @Test
    void fetchLatest_parsesNaverNewsSearchApiItems() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        NaverNewsFeedClient client = new NaverNewsFeedClient(
                restTemplate,
                true,
                "client-id",
                "client-secret",
                300,
                10,
                List.of(NaverNewsFeedClient.FeedDefinition.market("코스피 시황"))
        );
        when(restTemplate.exchange(
                eq(URI.create("https://openapi.naver.com/v1/search/news.json?query=%EC%BD%94%EC%8A%A4%ED%94%BC%20%EC%8B%9C%ED%99%A9&display=10&start=1&sort=date")),
                eq(HttpMethod.GET),
                org.mockito.ArgumentMatchers.<HttpEntity<Void>>any(),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("""
                {
                  "items": [
                    {
                      "title": "코스피, &lt;b&gt;반도체&lt;/b&gt; 강세에 상승 출발 - 테스트경제",
                      "originallink": "https://example.com/original",
                      "link": "https://n.news.naver.com/article/001/0000000001",
                      "description": "외국인 &lt;b&gt;순매수&lt;/b&gt;가 지수 흐름을 이끌고 있어요.",
                      "pubDate": "Mon, 11 May 2026 03:20:00 GMT"
                    }
                  ]
                }
                """));

        List<FetchedNewsArticle> articles = client.fetchLatest();

        assertEquals(1, articles.size());
        FetchedNewsArticle article = articles.get(0);
        assertEquals(NewsCategory.MARKET, article.getCategory());
        assertEquals("코스피, 반도체 강세에 상승 출발", article.getTitle());
        assertEquals("테스트경제", article.getSourceName());
        assertEquals("외국인 순매수가 지수 흐름을 이끌고 있어요.", article.getSummary());
        assertEquals("https://example.com/original", article.getExternalUrl());
        assertEquals(LocalDateTime.of(2026, 5, 11, 12, 20), article.getPublishedAt());
        assertFalse(article.getId().isBlank());
    }

    @Test
    void fetchLatest_returnsEmptyWhenCredentialsAreMissing() {
        NaverNewsFeedClient client = new NaverNewsFeedClient(
                mock(RestTemplate.class),
                true,
                "",
                "",
                300,
                10,
                List.of(NaverNewsFeedClient.FeedDefinition.market("코스피 시황"))
        );

        assertEquals(List.of(), client.fetchLatest());
    }

    @Test
    void fetchLatest_returnsEmptyAndSkipsHttpCallWhenDisabled() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        NaverNewsFeedClient client = new NaverNewsFeedClient(
                restTemplate,
                false,
                "client-id",
                "client-secret",
                300,
                10,
                List.of(NaverNewsFeedClient.FeedDefinition.market("코스피 시황"))
        );

        assertEquals(List.of(), client.fetchLatest());
        verify(restTemplate, never()).exchange(
                any(URI.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        );
    }

    @Test
    void fetchLatest_skipsOriginalArticleContentDuringListRefresh() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        NaverNewsFeedClient client = new NaverNewsFeedClient(
                restTemplate,
                true,
                "client-id",
                "client-secret",
                300,
                10,
                List.of(NaverNewsFeedClient.FeedDefinition.market("코스피 시황"))
        );
        when(restTemplate.exchange(
                eq(URI.create("https://openapi.naver.com/v1/search/news.json?query=%EC%BD%94%EC%8A%A4%ED%94%BC%20%EC%8B%9C%ED%99%A9&display=10&start=1&sort=date")),
                eq(HttpMethod.GET),
                org.mockito.ArgumentMatchers.<HttpEntity<Void>>any(),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("""
                {
                  "items": [
                    {
                      "title": "코스피, 반도체 강세에 상승 출발 - 테스트경제",
                      "originallink": "https://example.com/full-article",
                      "link": "https://n.news.naver.com/article/001/0000000001",
                      "description": "검색 API 요약입니다.",
                      "pubDate": "Mon, 11 May 2026 03:20:00 GMT"
                    }
                  ]
                }
                """));

        List<FetchedNewsArticle> articles = client.fetchLatest();

        assertEquals("", articles.get(0).getContent());
        verify(restTemplate, never()).exchange(
                eq(URI.create("https://example.com/full-article")),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        );
    }

    @Test
    void fetchLatest_prefersStockCategoryWhenSameArticleAppearsInMarketAndStockFeeds() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        NaverNewsFeedClient client = new NaverNewsFeedClient(
                restTemplate,
                true,
                "client-id",
                "client-secret",
                300,
                10,
                List.of(
                        NaverNewsFeedClient.FeedDefinition.market("삼성전자 시황"),
                        NaverNewsFeedClient.FeedDefinition.domesticStock("삼성전자")
                )
        );
        String responseBody = """
                {
                  "items": [
                    {
                      "title": "삼성전자, 실적 우려에 약세 - 테스트경제",
                      "originallink": "https://example.com/samsung",
                      "link": "https://n.news.naver.com/article/001/0000000002",
                      "description": "삼성전자 실적 우려가 투자심리에 부담을 주고 있어요.",
                      "pubDate": "Mon, 11 May 2026 03:30:00 GMT"
                    }
                  ]
                }
                """;
        when(restTemplate.exchange(
                eq(URI.create("https://openapi.naver.com/v1/search/news.json?query=%EC%82%BC%EC%84%B1%EC%A0%84%EC%9E%90%20%EC%8B%9C%ED%99%A9&display=10&start=1&sort=date")),
                eq(HttpMethod.GET),
                org.mockito.ArgumentMatchers.<HttpEntity<Void>>any(),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(responseBody));
        when(restTemplate.exchange(
                eq(URI.create("https://openapi.naver.com/v1/search/news.json?query=%EC%82%BC%EC%84%B1%EC%A0%84%EC%9E%90&display=10&start=1&sort=date")),
                eq(HttpMethod.GET),
                org.mockito.ArgumentMatchers.<HttpEntity<Void>>any(),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(responseBody));

        List<FetchedNewsArticle> articles = client.fetchLatest();

        assertEquals(1, articles.size());
        assertEquals(NewsCategory.DOMESTIC_STOCK, articles.get(0).getCategory());
    }

    @Test
    void fetchLatest_defaultFeedsAreBroadInsteadOfSamsungAndSkHynixOnly() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        NaverNewsFeedClient client = new NaverNewsFeedClient(
                restTemplate,
                true,
                "client-id",
                "client-secret",
                300,
                10
        );

        client.fetchLatest();

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate, org.mockito.Mockito.atLeastOnce()).exchange(
                uriCaptor.capture(),
                eq(HttpMethod.GET),
                org.mockito.ArgumentMatchers.<HttpEntity<Void>>any(),
                eq(String.class)
        );
        List<String> queries = uriCaptor.getAllValues().stream()
                .map(URI::toString)
                .map(value -> URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8))
                .toList();
        assertTrue(queries.stream().anyMatch(value -> value.contains("코스피") && value.contains("환율")));
        assertTrue(queries.stream().anyMatch(value -> value.contains("반도체") && value.contains("바이오")));
        assertFalse(queries.stream().anyMatch(value -> value.contains("삼성전자 SK하이닉스")));
    }

    @Test
    void fetchLatest_usesConfiguredQueriesByGroup() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("naver.news.queries.market[0]", "시장 설정")
                .withProperty("naver.news.queries.theme[0]", "테마 설정")
                .withProperty("naver.news.queries.company[0]", "기업 설정")
                .withProperty("naver.news.queries.overseas[0]", "해외 설정");
        NaverNewsFeedClient client = new NaverNewsFeedClient(
                restTemplate,
                true,
                "client-id",
                "client-secret",
                300,
                10,
                environment
        );

        client.fetchLatest();

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate, org.mockito.Mockito.times(4)).exchange(
                uriCaptor.capture(),
                eq(HttpMethod.GET),
                org.mockito.ArgumentMatchers.<HttpEntity<Void>>any(),
                eq(String.class)
        );
        List<String> queries = uriCaptor.getAllValues().stream()
                .map(URI::toString)
                .map(value -> URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8))
                .toList();
        assertTrue(queries.stream().anyMatch(value -> value.contains("시장 설정")));
        assertTrue(queries.stream().anyMatch(value -> value.contains("테마 설정")));
        assertTrue(queries.stream().anyMatch(value -> value.contains("기업 설정")));
        assertTrue(queries.stream().anyMatch(value -> value.contains("해외 설정")));
    }
}
