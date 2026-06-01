package com.uniport.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestmentIssueAnalyzerTest {

    private static final List<String> FORBIDDEN_WORDS = List.of(
            "매수",
            "매도",
            "사라",
            "팔아라",
            "추천한다",
            "비중을 늘려라",
            "비중을 줄여라"
    );

    private final InvestmentIssueAnalyzer analyzer = new InvestmentIssueAnalyzer(
            new StockMappingService(),
            new EtfMappingService()
    );

    @Test
    void analyze_hbmDemandIssueIsPositive() {
        IssueCluster cluster = cluster(
                "20260519|THEME|HBM|상승",
                InvestmentIssueCategory.THEME,
                "HBM",
                "상승",
                article("hbm-demand", "HBM AI 수요 증가에 반도체 강세", "수주 확대와 실적 개선 기대")
        );

        InvestmentIssue issue = analyzer.analyze(cluster);

        assertEquals(InvestmentIssueLabel.POSITIVE, issue.label());
        assertEquals("호재", issue.label().labelText());
        assertFalse(issue.reasonBullets().isEmpty());
        assertFalse(issue.watchPoints().isEmpty());
    }

    @Test
    void analyze_doesNotUseBuyOrSellRecommendationWords() {
        IssueCluster cluster = cluster(
                "20260519|COMPANY|삼성전자|급락",
                InvestmentIssueCategory.COMPANY,
                "삼성전자",
                "급락",
                article("samsung-shock", "삼성전자 실적 쇼크 우려에 급락", "순매도와 적자 가능성 부각")
        );

        InvestmentIssue issue = analyzer.analyze(cluster);

        assertEquals(InvestmentIssueLabel.NEGATIVE, issue.label());
        assertGeneratedTextHasNoForbiddenWords(issue);
    }

    @Test
    void analyze_negativeCuesDominateWeakPositiveTerms() {
        IssueCluster cluster = cluster(
                "20260519|COMPANY|삼성전자|급락",
                InvestmentIssueCategory.COMPANY,
                "삼성전자",
                "급락",
                article("mixed-samsung", "삼성전자 HBM 기대에도 실적 쇼크 우려에 급락", "AI 반도체 기대보다 적자 우려가 커졌어요")
        );

        InvestmentIssue issue = analyzer.analyze(cluster);

        assertEquals(InvestmentIssueLabel.NEGATIVE, issue.label());
    }

    @Test
    void analyze_severeNegativeCueDominatesWeakPositiveTerms() {
        IssueCluster cluster = cluster(
                "20260519|THEME|HBM|급락",
                InvestmentIssueCategory.THEME,
                "HBM",
                "급락",
                article("hbm-crash", "HBM AI 상승 기대에도 급락", "반도체 기대보다 변동성이 커졌어요")
        );

        InvestmentIssue issue = analyzer.analyze(cluster);

        assertEquals(InvestmentIssueLabel.NEGATIVE, issue.label());
    }

    @Test
    void analyze_mixedMacroIssueIsMixed() {
        IssueCluster cluster = cluster(
                "20260519|MARKET|환율|상승",
                InvestmentIssueCategory.MARKET,
                "환율",
                "상승",
                article("fx-rise", "환율 상승", "수혜와 비용 부담 업종이 갈려요")
        );

        InvestmentIssue issue = analyzer.analyze(cluster);

        assertEquals(InvestmentIssueLabel.MIXED, issue.label());
    }

    @Test
    void analyze_neutralFomcWaitAndSeeIssueIsNeutral() {
        IssueCluster cluster = cluster(
                "20260519|MARKET|FOMC|관망",
                InvestmentIssueCategory.MARKET,
                "FOMC",
                "관망",
                article("fomc-wait", "FOMC 발표 앞두고 관망", "동결 여부 대기 속 방향성 탐색")
        );

        InvestmentIssue issue = analyzer.analyze(cluster);

        assertEquals(InvestmentIssueLabel.NEUTRAL, issue.label());
    }

    @Test
    void analyze_includesRelatedStocksAndEtfsForHbmSemiconductorIssue() {
        IssueCluster cluster = cluster(
                "20260519|THEME|HBM|상승",
                InvestmentIssueCategory.THEME,
                "HBM",
                "상승",
                article("hbm-demand", "HBM 수요 증가에 AI 반도체주 상승", "반도체 장비 수주 기대")
        );

        InvestmentIssue issue = analyzer.analyze(cluster);

        assertTrue(issue.relatedStocks().stream().anyMatch(stock -> stock.name().equals("SK하이닉스")));
        assertTrue(issue.relatedStocks().stream().anyMatch(stock -> stock.name().equals("한미반도체")));
        assertTrue(issue.relatedEtfs().stream().anyMatch(etf -> etf.name().equals("KODEX 반도체")));
        assertEquals(1, issue.sourceCount());
        assertEquals(List.of("hbm-demand"), issue.sourceArticles().stream().map(FetchedNewsArticle::getId).toList());
    }

    @Test
    void analyze_sanitizesForbiddenWordsFromGeneratedTitle() {
        IssueCluster cluster = cluster(
                "20260519|MARKET|외국인|순매도",
                InvestmentIssueCategory.MARKET,
                "외국인",
                "순매도",
                article("foreign-outflow", "외국인 순매도", "시장 수급 약세")
        );

        InvestmentIssue issue = analyzer.analyze(cluster);

        assertFalse(issue.title().contains("매도"));
    }

    @Test
    void analyze_usesFallbackTitleWhenClusterAndArticleTitlesAreBlank() {
        IssueCluster cluster = cluster(
                "20260519|MARKET|시장|기타",
                InvestmentIssueCategory.MARKET,
                " ",
                null,
                article("blank-title", " ", "시장 일정 브리핑")
        );

        InvestmentIssue issue = analyzer.analyze(cluster);

        assertEquals("시장 이슈", issue.title());
    }

    @Test
    void analyze_usesSpecificArticleTitleWhenClusterEntityAndEventAreGeneric() {
        IssueCluster cluster = cluster(
                "20260519|MARKET|시장|기타",
                InvestmentIssueCategory.MARKET,
                "시장",
                "기타",
                article(
                        "kospi-rebound",
                        "코스피, 외국인 순매수에 2,750선 회복",
                        "대형주 중심으로 위험자산 선호가 살아났다는 분석"
                )
        );

        InvestmentIssue issue = analyzer.analyze(cluster);

        assertFalse(issue.title().equals("시장 기타"));
        assertTrue(issue.title().contains("코스피"));
        assertTrue(issue.title().contains("2,750선"));
    }

    @Test
    void analyze_expandsCompanyEarningsTitleAndSummaryWithArticleEvidence() {
        IssueCluster cluster = cluster(
                "20260519|OVERSEAS|엔비디아|실적",
                InvestmentIssueCategory.OVERSEAS,
                "엔비디아",
                "실적",
                article(
                        "nvidia-earnings-risk",
                        "엔비디아 실적 쇼크 우려에 AI 반도체주 약세",
                        "데이터센터 매출 둔화와 마진 압박 우려가 커졌어요"
                )
        );

        InvestmentIssue issue = analyzer.analyze(cluster);

        assertFalse(issue.title().equals("엔비디아 실적"));
        assertTrue(issue.title().contains("실적 쇼크 우려"));
        assertTrue(issue.summary().contains("데이터센터 매출 둔화"));
        assertTrue(issue.summary().contains("마진 압박 우려"));
    }

    @Test
    void analyze_dailyTextDoesNotMatchAiAcronymCueOrTheme() {
        IssueCluster cluster = cluster(
                "20260519|MARKET|시장|기타",
                InvestmentIssueCategory.MARKET,
                " ",
                null,
                article("daily-note", "Daily market note", "Broad market update")
        );

        InvestmentIssue issue = analyzer.analyze(cluster);

        assertEquals(InvestmentIssueLabel.NEUTRAL, issue.label());
        assertFalse(issue.relatedEtfs().stream().anyMatch(etf -> etf.theme().equals("AI/빅테크")));
    }

    @Test
    void analyze_usEarningsBeatIssueIsPositiveAndKeepsInvestmentEvidence() {
        IssueCluster cluster = cluster(
                "20260529|OVERSEAS|Dell|실적",
                InvestmentIssueCategory.OVERSEAS,
                "Dell",
                "실적",
                article(
                        "dell-earnings",
                        "Dell 실적 예상 상회, AI 서버 수요 강세",
                        "매출과 주당순이익이 시장 예상치를 웃돌고 AI 서버 주문이 확대됐어요"
                ),
                article(
                        "dell-guidance",
                        "델, 가이던스 상향에 장외 상승",
                        "데이터센터 인프라 수요와 마진 개선 기대가 함께 반영됐어요"
                )
        );

        InvestmentIssue issue = analyzer.analyze(cluster);

        assertEquals(InvestmentIssueLabel.POSITIVE, issue.label());
        assertTrue(issue.title().contains("실적 예상 상회") || issue.title().contains("가이던스 상향"));
        assertTrue(issue.summary().contains("AI 서버 주문") || issue.summary().contains("데이터센터 인프라 수요"));
        assertTrue(issue.reasonBullets().stream().anyMatch(reason -> reason.contains("예상 상회")
                || reason.contains("가이던스 상향")
                || reason.contains("AI 서버")));
        assertTrue(issue.relatedStocks().stream().anyMatch(stock -> stock.symbol().equals("DELL")));
        assertGeneratedTextHasNoForbiddenWords(issue);
    }

    private void assertGeneratedTextHasNoForbiddenWords(InvestmentIssue issue) {
        List<String> generatedTexts = Stream.concat(
                        Stream.of(issue.title(), issue.summary()),
                        Stream.concat(issue.reasonBullets().stream(), issue.watchPoints().stream())
                )
                .toList();

        for (String text : generatedTexts) {
            for (String forbiddenWord : FORBIDDEN_WORDS) {
                assertFalse(text.contains(forbiddenWord), () -> text + " contains " + forbiddenWord);
            }
        }
    }

    private IssueCluster cluster(String clusterKey,
                                 InvestmentIssueCategory category,
                                 String mainEntity,
                                 String mainEvent,
                                 FetchedNewsArticle... articles) {
        return new IssueCluster(clusterKey, category, mainEntity, mainEvent, List.of(articles));
    }

    private FetchedNewsArticle article(String id, String title, String summary) {
        return FetchedNewsArticle.builder()
                .id(id)
                .category(NewsCategory.DOMESTIC_STOCK)
                .title(title)
                .summary(summary)
                .sourceName("테스트뉴스")
                .publishedAt(LocalDateTime.of(2026, 5, 19, 9, 0))
                .externalUrl("https://example.com/" + id)
                .build();
    }
}
