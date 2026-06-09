package com.uniport.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaywrightPublicWebIssueSourceProviderTest {

    @Test
    void fetchSource_readsSaveTickerThroughBrowserDomBeforeApiFallback() {
        PublicWebIssueHtmlExtractor htmlExtractor = mock(PublicWebIssueHtmlExtractor.class);
        SaveTickerNewsDomExtractor domExtractor = mock(SaveTickerNewsDomExtractor.class);
        SaveTickerNewsJsonMapper jsonMapper = mock(SaveTickerNewsJsonMapper.class);
        PlaywrightPublicWebIssueSourceProvider provider = new PlaywrightPublicWebIssueSourceProvider(
                new MockEnvironment()
                        .withProperty("uniport.investment-issue.public-web.timeout-ms", "6000"),
                htmlExtractor,
                domExtractor,
                jsonMapper
        );
        PublicWebIssueSource source = new PublicWebIssueSource(
                "SaveTicker News",
                URI.create("https://www.saveticker.com/news"),
                NewsCategory.MARKET,
                "세이브티커",
                1
        );
        Browser browser = mock(Browser.class);
        Page page = mock(Page.class);
        FetchedNewsArticle article = FetchedNewsArticle.builder()
                .id("saveticker_140003")
                .category(NewsCategory.MARKET)
                .title("SaveTicker 상세 클릭 원문")
                .summary("목록 요약")
                .content("$NVDA NVDA")
                .fullBody("상세 페이지에서 본문 탭을 클릭해서 가져온 전체 본문입니다.")
                .sourceName("세이브티커")
                .publishedAt(LocalDateTime.of(2026, 6, 9, 10, 0))
                .externalUrl("https://www.saveticker.com/news/140003")
                .build();
        when(browser.newPage()).thenReturn(page);
        when(domExtractor.extract(source, page, 6000)).thenReturn(List.of(article));

        List<FetchedNewsArticle> articles = provider.fetchSource(browser, source);

        assertEquals(1, articles.size());
        assertEquals("상세 페이지에서 본문 탭을 클릭해서 가져온 전체 본문입니다.", articles.get(0).getFullBody());
        verify(domExtractor).extract(source, page, 6000);
        verify(htmlExtractor, never()).extract(eq(source), org.mockito.ArgumentMatchers.anyString());
        verify(jsonMapper, never()).extract(eq(source), org.mockito.ArgumentMatchers.anyString());
    }
}
