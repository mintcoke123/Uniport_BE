package com.uniport.service;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicWebIssueHtmlExtractorTest {

    private final PublicWebIssueHtmlExtractor extractor = new PublicWebIssueHtmlExtractor();

    @Test
    void extractArticles_readsPublicArticleCardsWithoutCopyingFullBody() {
        PublicWebIssueSource source = new PublicWebIssueSource(
                "Dell IR",
                URI.create("https://investors.delltechnologies.com/news"),
                NewsCategory.OVERSEAS_STOCK,
                "Dell Technologies IR",
                5
        );
        String html = """
                <!doctype html>
                <html>
                  <head>
                    <title>Dell Technologies News</title>
                    <meta name="description" content="Latest investor news from Dell Technologies">
                  </head>
                  <body>
                    <article>
                      <a href="/news-releases/news-release-details/dell-q1">
                        Dell 실적 예상 상회, AI 서버 수요 강세
                      </a>
                      <p>매출과 주당순이익이 시장 예상치를 웃돌고 AI 서버 주문이 확대됐어요.</p>
                      <time datetime="2026-05-29T07:15:00+09:00">May 29, 2026</time>
                      <div class="body">이 문단은 원문 본문이라 저장하면 안 됩니다.</div>
                    </article>
                    <article>
                      <a href="https://evil.example.com/story">광고성 외부 링크</a>
                      <p>허용되지 않은 호스트라 제외해야 합니다.</p>
                    </article>
                  </body>
                </html>
                """;

        List<FetchedNewsArticle> articles = extractor.extract(source, html);

        assertEquals(1, articles.size());
        FetchedNewsArticle article = articles.get(0);
        assertEquals(NewsCategory.OVERSEAS_STOCK, article.getCategory());
        assertEquals("Dell 실적 예상 상회, AI 서버 수요 강세", article.getTitle());
        assertEquals("매출과 주당순이익이 시장 예상치를 웃돌고 AI 서버 주문이 확대됐어요.", article.getSummary());
        assertEquals("Dell Technologies IR", article.getSourceName());
        assertEquals("https://investors.delltechnologies.com/news-releases/news-release-details/dell-q1",
                article.getExternalUrl());
        assertEquals(LocalDateTime.of(2026, 5, 29, 7, 15), article.getPublishedAt());
        assertEquals("", article.getContent());
        assertFalse(article.getId().isBlank());
        assertFalse(article.getSummary().contains("원문 본문"));
    }

    @Test
    void extractArticles_readsNewsroomDateLabels() {
        PublicWebIssueSource source = new PublicWebIssueSource(
                "NVIDIA Newsroom",
                URI.create("https://nvidianews.nvidia.com/news"),
                NewsCategory.OVERSEAS_STOCK,
                "NVIDIA Newsroom",
                5
        );
        String html = """
                <html>
                  <body>
                    <article class="index-item">
                      <span class="index-item-text-info-date">May 31, 2026</span>
                      <h3>
                        <a href="/news/nvidia-and-tsmc-bring-ai-into-fabs-to-advance-semiconductor-design-and-manufacturing">
                          NVIDIA and TSMC Bring AI Into Fabs to Advance Semiconductor Design and Manufacturing
                        </a>
                      </h3>
                      <div class="index-item-text-description">
                        NVIDIA announced that TSMC is using accelerated computing and AI in semiconductor fabs.
                      </div>
                    </article>
                  </body>
                </html>
                """;

        List<FetchedNewsArticle> articles = extractor.extract(source, html);

        assertEquals(1, articles.size());
        assertEquals(LocalDateTime.of(2026, 5, 31, 0, 0), articles.get(0).getPublishedAt());
    }

    @Test
    void extractArticles_skipsWeakNavigationLinksAndLimitsResults() {
        PublicWebIssueSource source = new PublicWebIssueSource(
                "Market calendar",
                URI.create("https://example.com/markets"),
                NewsCategory.MARKET,
                "Example Markets",
                1
        );
        String html = """
                <html>
                  <body>
                    <a href="/about">About</a>
                    <a href="/calendar/cpi">CPI 발표 앞두고 미국 금리와 환율 변동성 확대</a>
                    <a href="/calendar/fomc">FOMC 금리 동결 여부에 증시 관망</a>
                  </body>
                </html>
                """;

        List<FetchedNewsArticle> articles = extractor.extract(source, html);

        assertEquals(1, articles.size());
        assertEquals("CPI 발표 앞두고 미국 금리와 환율 변동성 확대", articles.get(0).getTitle());
        assertTrue(articles.get(0).getExternalUrl().endsWith("/calendar/cpi"));
    }

    @Test
    void extractArticles_skipsSiteNavigationWhenReadingPublicIndexPages() {
        PublicWebIssueSource source = new PublicWebIssueSource(
                "Federal Reserve Press Releases",
                URI.create("https://www.federalreserve.gov/newsevents/pressreleases.htm"),
                NewsCategory.MARKET,
                "Federal Reserve",
                10
        );
        String html = """
                <html>
                  <body>
                    <header>
                      <a href="/aboutthefed/advisorydefault.htm">Advisory Councils</a>
                      <a href="/aboutthefed/bios/board/default.htm">Board Members</a>
                    </header>
                    <nav>
                      <a href="/newsevents.htm">Expand sub-menu</a>
                      <a href="/aboutthefed/federal-reserve-system.htm">Federal Reserve Banks</a>
                    </nav>
                    <main>
                      <ul>
                        <li>
                          <a href="/newsevents/pressreleases/monetary20260529a.htm">
                            Federal Reserve issues FOMC statement after rate decision
                          </a>
                        </li>
                      </ul>
                    </main>
                  </body>
                </html>
                """;

        List<FetchedNewsArticle> articles = extractor.extract(source, html);

        assertEquals(1, articles.size());
        assertEquals("Federal Reserve issues FOMC statement after rate decision", articles.get(0).getTitle());
        assertTrue(articles.get(0).getExternalUrl().endsWith("/newsevents/pressreleases/monetary20260529a.htm"));
    }

    @Test
    void extractArticles_readsFedYearArchiveAsListInsteadOfSingleDocument() {
        PublicWebIssueSource source = new PublicWebIssueSource(
                "Federal Reserve 2026 FOMC Press Releases",
                URI.create("https://www.federalreserve.gov/newsevents/pressreleases/2026-press-fomc.htm"),
                NewsCategory.MARKET,
                "Federal Reserve",
                10
        );
        String html = """
                <html>
                  <head>
                    <title>Federal Reserve Board - 2026 FOMC Press Releases</title>
                    <meta name="description" content="The Federal Reserve Board of Governors in Washington DC.">
                  </head>
                  <body>
                    <main>
                      <h1>Board of Governors of the Federal Reserve System</h1>
                      <div class="row">
                        <div class="eventlist__time"><time>4/29/2026</time></div>
                        <div class="eventlist__event">
                          <p><a href="/newsevents/pressreleases/monetary20260429a.htm">
                            <em>Federal Reserve issues FOMC statement</em>
                          </a></p>
                        </div>
                      </div>
                    </main>
                  </body>
                </html>
                """;

        List<FetchedNewsArticle> articles = extractor.extract(source, html);

        assertEquals(1, articles.size());
        assertEquals("Federal Reserve issues FOMC statement", articles.get(0).getTitle());
        assertEquals(LocalDateTime.of(2026, 4, 29, 0, 0), articles.get(0).getPublishedAt());
        assertTrue(articles.get(0).getExternalUrl().endsWith("/newsevents/pressreleases/monetary20260429a.htm"));
    }

    @Test
    void extractArticles_keepsOnlyMarketRelevantFedPressReleaseLinks() {
        PublicWebIssueSource source = new PublicWebIssueSource(
                "Federal Reserve 2026 Press Releases",
                URI.create("https://www.federalreserve.gov/newsevents/pressreleases/2026-press.htm"),
                NewsCategory.MARKET,
                "Federal Reserve",
                10
        );
        String html = """
                <html>
                  <body>
                    <a href="#content">Skip to main content</a>
                    <main id="content">
                      <p><a href="/newsevents/pressreleases/enforcement20260527a.htm">
                        <em>Federal Reserve Board issues enforcement action with former employee of Commerce Bank</em>
                      </a></p>
                      <p><a href="/newsevents/pressreleases/orders20260515a.htm">
                        <em>Federal Reserve Board announces approval of application by the Stephen M. Calk 2025 Trust</em>
                      </a></p>
                      <p><a href="/newsevents/pressreleases/monetary20260520a.htm">
                        <em>Minutes of the Federal Open Market Committee, April 28-29, 2026</em>
                      </a></p>
                    </main>
                  </body>
                </html>
                """;

        List<FetchedNewsArticle> articles = extractor.extract(source, html);

        assertEquals(1, articles.size());
        assertEquals("Minutes of the Federal Open Market Committee, April 28-29, 2026",
                articles.get(0).getTitle());
    }

    @Test
    void extractArticles_readsSinglePublicArticlePageFromHeadingMetadata() {
        PublicWebIssueSource source = new PublicWebIssueSource(
                "Microsoft Investor Relations",
                URI.create("https://www.microsoft.com/en-us/Investor/earnings/FY-2026-Q3/press-release-webcast"),
                NewsCategory.OVERSEAS_STOCK,
                "Microsoft Investor Relations",
                5
        );
        String html = """
                <html>
                  <head>
                    <meta name="description" content="Microsoft Cloud revenue exceeded expectations and operating margin improved.">
                  </head>
                  <body>
                    <main>
                      <h1>Microsoft Cloud revenue beats expectations in quarterly earnings</h1>
                      <p>Revenue and operating income both topped analyst estimates.</p>
                    </main>
                  </body>
                </html>
                """;

        List<FetchedNewsArticle> articles = extractor.extract(source, html);

        assertEquals(1, articles.size());
        assertEquals("Microsoft Cloud revenue beats expectations in quarterly earnings", articles.get(0).getTitle());
        assertEquals("Microsoft Cloud revenue exceeded expectations and operating margin improved.",
                articles.get(0).getSummary());
        assertEquals("https://www.microsoft.com/en-us/Investor/earnings/FY-2026-Q3/press-release-webcast",
                articles.get(0).getExternalUrl());
    }

    @Test
    void extractArticles_readsPublicTelegramMessagesAsIssueCards() {
        PublicWebIssueSource source = new PublicWebIssueSource(
                "FastStockNews Telegram",
                URI.create("https://t.me/s/FastStockNews"),
                NewsCategory.DOMESTIC_STOCK,
                "주식 급등일보",
                5
        );
        String html = """
                <html>
                  <body>
                    <div class="tgme_widget_message js-widget_message" data-post="FastStockNews/118724">
                      <div class="tgme_widget_message_bubble">
                        <div class="tgme_widget_message_text js-message_text" dir="auto">
                          2026.06.01 18:40:51<br/>
                          기업명: 올릭스(시가총액: 3조 5,079억) A226950<br/>
                          보고서명: 주요사항보고서(유상증자결정)<br/>
                          공시링크: <a href="https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260601002040">dart</a>
                        </div>
                        <a class="tgme_widget_message_date" href="https://t.me/FastStockNews/118724">
                          <time datetime="2026-06-01T10:58:45+00:00" class="time">10:58</time>
                        </a>
                      </div>
                    </div>
                    <div class="tgme_widget_message js-widget_message" data-post="FastStockNews/118725">
                      <div class="tgme_widget_message_bubble">
                        <div class="tgme_widget_message_text js-message_text" dir="auto">
                          ✅ [특징주] 링네트, 엔비디아 주요 협력사 네이버클라우드 부각에 상승<br/>
                          <a href="https://www.cstimes.com/news/articleView.html?idxno=708056">기사</a>
                        </div>
                        <a class="tgme_widget_message_date" href="https://t.me/FastStockNews/118725">
                          <time datetime="2026-06-01T06:18:47+00:00" class="time">06:18</time>
                        </a>
                      </div>
                    </div>
                  </body>
                </html>
                """;

        List<FetchedNewsArticle> articles = extractor.extract(source, html);

        assertEquals(2, articles.size());
        assertEquals("올릭스 주요사항보고서(유상증자결정)", articles.get(0).getTitle());
        assertEquals("https://t.me/FastStockNews/118724", articles.get(0).getExternalUrl());
        assertEquals(LocalDateTime.of(2026, 6, 1, 19, 58, 45), articles.get(0).getPublishedAt());
        assertEquals("주식 급등일보", articles.get(0).getSourceName());
        assertEquals("[특징주] 링네트, 엔비디아 주요 협력사 네이버클라우드 부각에 상승",
                articles.get(1).getTitle());
    }
}
