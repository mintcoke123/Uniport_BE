package com.uniport.service;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaveTickerNewsDomExtractorTest {

    @Test
    void extract_readsListAndDetailTextFromPlaywrightDomWithoutSerializingHtml() {
        Page page = mock(Page.class);
        when(page.evaluate(argThat(script -> containsAll(script, "querySelectorAll")), eq(2)))
                .thenReturn(List.of(
                        Map.of(
                                "url", "https://www.saveticker.com/news/139026",
                                "path", "/news/139026",
                                "title", "시장조사기관 알파센스, 신규 자금 조달 라운드서 기업가치 상승",
                                "listText", """
                                        Reuters
                                        시장조사기관 알파센스, 신규 자금 조달 라운드서 기업가치 상승
                                        $NVDA
                                        2026-06-03T20:03:19+09:00
                                        """,
                                "datetime", "2026-06-03T20:03:19+09:00",
                                "timeText", "2026-06-03 20:03"
                        )
                ));
        when(page.evaluate(argThat(script -> containsAll(script, "bodyText"))))
                .thenReturn(Map.of(
                        "url", "https://www.saveticker.com/news/139026",
                        "title", "시장조사기관 알파센스, 신규 자금 조달 라운드서 기업가치 75억 달러로 상승",
                        "bodyText", """
                                SaveTicker
                                시장조사기관 알파센스, 신규 자금 조달 라운드서 기업가치 75억 달러로 상승
                                6월 3일 (로이터) - 시장 인텔리전스 플랫폼 알파센스는 3억 5천만 달러를 조달했다고 밝혔다.
                                이번 라운드는 비트루비안 파트너스와 J.P. 모건 자산운용이 주도했다.
                                $NVDA
                                """,
                        "datetime", "2026-06-03T20:03:19+09:00",
                        "timeText", "2026-06-03 20:03"
                ));
        PublicWebIssueSource source = new PublicWebIssueSource(
                "SaveTicker News",
                URI.create("https://www.saveticker.com/news"),
                NewsCategory.MARKET,
                "세이브티커",
                2
        );

        List<FetchedNewsArticle> articles = new SaveTickerNewsDomExtractor().extract(source, page, 6000);

        assertEquals(1, articles.size());
        FetchedNewsArticle article = articles.get(0);
        assertEquals("saveticker_139026", article.getId());
        assertEquals(NewsCategory.MARKET, article.getCategory());
        assertEquals("시장조사기관 알파센스, 신규 자금 조달 라운드서 기업가치 75억 달러로 상승", article.getTitle());
        assertTrue(article.getSummary().contains("시장 인텔리전스 플랫폼 알파센스는 3억 5천만 달러를 조달"));
        assertTrue(article.getSummary().contains("J.P. 모건 자산운용이 주도"));
        assertFalse(article.getSummary().contains("SaveTicker"));
        assertEquals("Reuters", article.getSourceName());
        assertEquals(LocalDateTime.of(2026, 6, 3, 20, 3, 19), article.getPublishedAt());
        assertEquals("$NVDA NVDA", article.getContent());
        assertEquals("https://www.saveticker.com/news/139026", article.getExternalUrl());
        verify(page, never()).content();
    }

    @Test
    void extract_clicksFullBodyTabBeforeReadingDetailDomText() {
        Page page = mock(Page.class);
        when(page.evaluate(argThat(script -> containsAll(script, "querySelectorAll")), eq(1)))
                .thenReturn(List.of(
                        Map.of(
                                "url", "https://www.saveticker.com/news/140003",
                                "path", "/news/140003",
                                "title", "본문 탭 뒤에 숨겨진 원문 기사",
                                "listText", "본문 탭 뒤에 숨겨진 원문 기사",
                                "datetime", "",
                                "timeText", ""
                        )
                ));
        when(page.evaluate(argThat(script -> containsAll(script, "본문", "click"))))
                .thenReturn(true);
        when(page.evaluate(argThat(script -> containsAll(script, "bodyText"))))
                .thenReturn(Map.of(
                        "url", "https://www.saveticker.com/news/140003",
                        "title", "본문 탭 뒤에 숨겨진 원문 기사",
                        "bodyText", "본문 탭을 누른 뒤 노출되는 전체 기사 문장입니다.",
                        "datetime", "",
                        "timeText", ""
                ));
        PublicWebIssueSource source = new PublicWebIssueSource(
                "SaveTicker News",
                URI.create("https://www.saveticker.com/news"),
                NewsCategory.MARKET,
                "세이브티커",
                1
        );

        List<FetchedNewsArticle> articles = new SaveTickerNewsDomExtractor().extract(source, page, 6000);

        assertEquals(1, articles.size());
        assertTrue(articles.get(0).getSummary().contains("전체 기사 문장"));
        InOrder inOrder = inOrder(page);
        inOrder.verify(page).evaluate(argThat(script -> containsAll(script, "본문", "click")));
        inOrder.verify(page).evaluate(argThat(script -> containsAll(script, "bodyText")));
    }

    @Test
    void extract_fallsBackToListTitleWhenDetailTitleIsSiteChrome() {
        Page page = mock(Page.class);
        when(page.evaluate(argThat(script -> containsAll(script, "querySelectorAll")), eq(1)))
                .thenReturn(List.of(
                        Map.of(
                                "url", "https://www.saveticker.com/news/140001",
                                "path", "/news/140001",
                                "title", "엔비디아 차세대 칩 수요 전망에 반도체 공급망 주목",
                                "listText", "엔비디아 차세대 칩 수요 전망에 반도체 공급망 주목\n$NVDA",
                                "datetime", "",
                                "timeText", ""
                        )
                ));
        when(page.evaluate(argThat(script -> containsAll(script, "bodyText"))))
                .thenReturn(Map.of(
                        "url", "https://www.saveticker.com/news/140001",
                        "title", "SaveTicker",
                        "bodyText", """
                                SaveTicker
                                엔비디아 차세대 칩 수요 전망에 반도체 공급망 주목
                                반도체 공급망 기업들이 AI 칩 주문 확대 기대를 반영하고 있다.
                                """,
                        "datetime", "",
                        "timeText", ""
                ));
        PublicWebIssueSource source = new PublicWebIssueSource(
                "SaveTicker News",
                URI.create("https://www.saveticker.com/news"),
                NewsCategory.MARKET,
                "세이브티커",
                1
        );

        List<FetchedNewsArticle> articles = new SaveTickerNewsDomExtractor().extract(source, page, 6000);

        assertEquals(1, articles.size());
        assertEquals("엔비디아 차세대 칩 수요 전망에 반도체 공급망 주목", articles.get(0).getTitle());
        assertTrue(articles.get(0).getSummary().contains("반도체 공급망 기업들이 AI 칩 주문 확대 기대"));
    }

    @Test
    void extract_keepsListArticleWhenDetailPageFails() {
        Page page = mock(Page.class);
        when(page.evaluate(argThat(script -> containsAll(script, "querySelectorAll")), eq(1)))
                .thenReturn(List.of(
                        Map.of(
                                "url", "https://www.saveticker.com/news/140002",
                                "path", "/news/140002",
                                "title", "미국 고용지표 발표 앞두고 금리 인하 기대 재조정",
                                "listText", """
                                        미국 고용지표 발표 앞두고 금리 인하 기대 재조정
                                        투자자들은 신규 고용과 임금 상승률을 확인하며 연준 경로를 다시 가격에 반영하고 있다.
                                        2026-06-04T08:30:00+09:00
                                        """,
                                "datetime", "2026-06-04T08:30:00+09:00",
                                "timeText", ""
                        )
                ));
        when(page.navigate(eq("https://www.saveticker.com/news/140002"), any(Page.NavigateOptions.class)))
                .thenThrow(new RuntimeException("detail blocked"));
        PublicWebIssueSource source = new PublicWebIssueSource(
                "SaveTicker News",
                URI.create("https://www.saveticker.com/news"),
                NewsCategory.MARKET,
                "세이브티커",
                1
        );

        List<FetchedNewsArticle> articles = new SaveTickerNewsDomExtractor().extract(source, page, 6000);

        assertEquals(1, articles.size());
        assertEquals("미국 고용지표 발표 앞두고 금리 인하 기대 재조정", articles.get(0).getTitle());
        assertTrue(articles.get(0).getSummary().contains("투자자들은 신규 고용과 임금 상승률을 확인"));
        assertEquals(LocalDateTime.of(2026, 6, 4, 8, 30), articles.get(0).getPublishedAt());
    }

    private static boolean containsAll(String value, String... needles) {
        if (value == null) {
            return false;
        }
        for (String needle : needles) {
            if (!value.contains(needle)) {
                return false;
            }
        }
        return true;
    }
}
