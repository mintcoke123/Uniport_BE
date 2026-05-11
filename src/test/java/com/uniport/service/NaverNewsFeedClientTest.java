package com.uniport.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
}
