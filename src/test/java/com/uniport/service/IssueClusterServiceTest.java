package com.uniport.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IssueClusterServiceTest {

    private final IssueClusterService issueClusterService = new IssueClusterService(new RawNewsNormalizer());

    @Test
    void cluster_groupsHbmSemiconductorArticlesIntoOneIssue() {
        LocalDateTime publishedAt = LocalDateTime.of(2026, 5, 19, 9, 0);

        List<IssueCluster> clusters = issueClusterService.cluster(List.of(
                article("hbm-demand", NewsCategory.DOMESTIC_STOCK, "HBM 수요 폭발에 AI 반도체주 상승", publishedAt),
                article("hbm-etf", NewsCategory.MARKET, "AI 반도체 ETF, HBM 기대감에 강세", publishedAt.plusMinutes(5)),
                article("hbm-foreign-buying", NewsCategory.DOMESTIC_STOCK,
                        "외국인 반도체 대형주 순매수, HBM 모멘텀", publishedAt.plusMinutes(10)),
                article("hbm-equipment", NewsCategory.DOMESTIC_STOCK,
                        "HBM 장비주, AI 서버 투자 확대 수혜", publishedAt.plusMinutes(15))
        ));

        assertEquals(1, clusters.size());
        IssueCluster cluster = clusters.get(0);
        assertEquals("20260519|THEME|HBM|상승", cluster.clusterKey());
        assertEquals(InvestmentIssueCategory.THEME, cluster.category());
        assertEquals("HBM", cluster.mainEntity());
        assertEquals("상승", cluster.mainEvent());
        assertEquals(List.of("hbm-demand", "hbm-etf", "hbm-foreign-buying", "hbm-equipment"),
                cluster.articles().stream().map(FetchedNewsArticle::getId).toList());
    }

    @Test
    void cluster_keepsDifferentSemiconductorEventsSeparate() {
        LocalDateTime publishedAt = LocalDateTime.of(2026, 5, 19, 10, 0);

        List<IssueCluster> clusters = issueClusterService.cluster(List.of(
                article("hbm-demand", NewsCategory.DOMESTIC_STOCK, "HBM 수요 폭발에 AI 반도체주 상승", publishedAt),
                article("samsung-strike", NewsCategory.DOMESTIC_STOCK,
                        "삼성전자 노조 파업 장기화 우려", publishedAt.plusMinutes(3)),
                article("us-export-rule", NewsCategory.MARKET,
                        "미국 반도체 수출 규제 강화에 업계 긴장", publishedAt.plusMinutes(6))
        ));

        assertEquals(3, clusters.size());
        assertEquals(List.of(
                        "20260519|THEME|HBM|상승",
                        "20260519|COMPANY|삼성전자|파업",
                        "20260519|OVERSEAS|미국|규제"
                ),
                clusters.stream().map(IssueCluster::clusterKey).toList());
    }

    @Test
    void cluster_splitsHighlySimilarArticlesOutsideTwentyFourHourWindow() {
        LocalDateTime publishedAt = LocalDateTime.of(2026, 5, 19, 9, 0);

        List<IssueCluster> clusters = issueClusterService.cluster(List.of(
                article("first", NewsCategory.DOMESTIC_STOCK, "HBM 수요 폭발에 AI 반도체주 상승", publishedAt),
                article("next-day", NewsCategory.DOMESTIC_STOCK, "HBM 수요 폭발에 AI 반도체주 상승",
                        publishedAt.plusHours(25))
        ));

        assertEquals(2, clusters.size());
        assertEquals(List.of("20260519|THEME|HBM|상승", "20260520|THEME|HBM|상승"),
                clusters.stream().map(IssueCluster::clusterKey).toList());
        assertEquals(List.of(1, 1), clusters.stream().map(cluster -> cluster.articles().size()).toList());
    }

    @Test
    void cluster_derivesStableClusterKeyFromCanonicalClusterDataAcrossInputPermutations() {
        LocalDateTime publishedAt = LocalDateTime.of(2026, 5, 19, 9, 0);
        FetchedNewsArticle forecast = article("fx-forecast", NewsCategory.MARKET, "환율 전망", publishedAt);
        FetchedNewsArticle rise = article("fx-rise", NewsCategory.MARKET, "환율 상승 전망", publishedAt);

        List<IssueCluster> forecastFirst = issueClusterService.cluster(List.of(forecast, rise));
        List<IssueCluster> riseFirst = issueClusterService.cluster(List.of(rise, forecast));

        assertEquals(1, forecastFirst.size());
        assertEquals(1, riseFirst.size());
        assertEquals("20260519|MARKET|환율|상승", forecastFirst.get(0).clusterKey());
        assertEquals(forecastFirst.get(0).clusterKey(), riseFirst.get(0).clusterKey());
    }

    @Test
    void cluster_mergesBridgeArticlesIntoStableClustersAcrossInputPermutations() {
        LocalDateTime publishedAt = LocalDateTime.of(2026, 5, 19, 9, 0);
        FetchedNewsArticle hbmRise = article("a-hbm-rise", NewsCategory.DOMESTIC_STOCK,
                "HBM 반도체 상승", publishedAt);
        FetchedNewsArticle hbmRegulationRise = article("b-hbm-regulation-rise", NewsCategory.DOMESTIC_STOCK,
                "HBM 반도체 규제 상승", publishedAt);
        FetchedNewsArticle semiconductorRegulation = article("c-semiconductor-regulation", NewsCategory.DOMESTIC_STOCK,
                "반도체 규제", publishedAt);

        List<IssueCluster> abc = issueClusterService.cluster(List.of(
                hbmRise,
                hbmRegulationRise,
                semiconductorRegulation
        ));
        List<IssueCluster> acb = issueClusterService.cluster(List.of(
                hbmRise,
                semiconductorRegulation,
                hbmRegulationRise
        ));
        List<IssueCluster> cab = issueClusterService.cluster(List.of(
                semiconductorRegulation,
                hbmRise,
                hbmRegulationRise
        ));

        Set<String> expectedKeys = Set.of("20260519|THEME|HBM|규제");
        assertEquals(1, abc.size());
        assertEquals(1, acb.size());
        assertEquals(1, cab.size());
        assertEquals(expectedKeys, clusterKeys(abc));
        assertEquals(expectedKeys, clusterKeys(acb));
        assertEquals(expectedKeys, clusterKeys(cab));
    }

    @Test
    void cluster_doesNotMergeBlankArticlesUsingFallbackEntityAndEventEvidence() {
        LocalDateTime publishedAt = LocalDateTime.of(2026, 5, 19, 9, 0);

        List<IssueCluster> clusters = issueClusterService.cluster(List.of(
                article("blank", NewsCategory.MARKET, "", publishedAt),
                article("space", NewsCategory.MARKET, "   ", publishedAt.plusMinutes(1))
        ));

        assertEquals(2, clusters.size());
    }

    @Test
    void cluster_assignsUniqueKeysWhenFallbackDisplayClustersAreSplit() {
        LocalDateTime publishedAt = LocalDateTime.of(2026, 5, 19, 9, 0);

        List<IssueCluster> clusters = issueClusterService.cluster(List.of(
                article("blank", NewsCategory.MARKET, "", publishedAt),
                article("space", NewsCategory.MARKET, "   ", publishedAt.plusMinutes(1))
        ));

        String baseKey = "20260519|MARKET|시장|기타";
        assertEquals(2, clusters.size());
        assertEquals(2, clusterKeys(clusters).size());
        assertEquals(2L, clusters.stream()
                .map(IssueCluster::clusterKey)
                .filter(key -> key.startsWith(baseKey))
                .count());
    }

    @Test
    void cluster_assignsUniqueKeysWhenFallbackClustersHaveIdenticalBlankArticleIdentity() {
        LocalDateTime publishedAt = LocalDateTime.of(2026, 5, 19, 9, 0);

        List<IssueCluster> clusters = issueClusterService.cluster(List.of(
                articleWithoutIdentity("", NewsCategory.MARKET, "", publishedAt),
                articleWithoutIdentity("", NewsCategory.MARKET, "   ", publishedAt.plusMinutes(1))
        ));

        String baseKey = "20260519|MARKET|시장|기타";
        assertEquals(2, clusters.size());
        assertEquals(2, clusterKeys(clusters).size());
        assertEquals(2L, clusters.stream()
                .map(IssueCluster::clusterKey)
                .filter(key -> key.startsWith(baseKey))
                .count());
    }

    @Test
    void cluster_assignsFallbackKeySuffixesToSameArticlesAcrossInputOrder() {
        LocalDateTime publishedAt = LocalDateTime.of(2026, 5, 19, 9, 0);
        FetchedNewsArticle first = articleWithoutIdentity("", NewsCategory.MARKET, "", publishedAt,
                "알파뉴스", "장전 주요 일정");
        FetchedNewsArticle second = articleWithoutIdentity("", NewsCategory.MARKET, "   ", publishedAt,
                "베타뉴스", "오전 브리핑");

        List<IssueCluster> firstOrder = issueClusterService.cluster(List.of(first, second));
        List<IssueCluster> reverseOrder = issueClusterService.cluster(List.of(second, first));

        assertEquals(2, firstOrder.size());
        assertEquals(2, reverseOrder.size());
        assertEquals(clusterKeyToArticleSourceAndSummary(firstOrder), clusterKeyToArticleSourceAndSummary(reverseOrder));
    }

    @Test
    void cluster_doesNotMergeWeakArticlesUsingFallbackEntityAndEventEvidence() {
        LocalDateTime publishedAt = LocalDateTime.of(2026, 5, 19, 9, 0);

        List<IssueCluster> clusters = issueClusterService.cluster(List.of(
                article("briefing", NewsCategory.MARKET, "오늘 브리핑", publishedAt),
                article("schedule", NewsCategory.MARKET, "오늘 일정", publishedAt.plusMinutes(1))
        ));

        assertEquals(2, clusters.size());
    }

    @Test
    void cluster_classifiesAiInfrastructureArticleAsThemeIssue() {
        LocalDateTime publishedAt = LocalDateTime.of(2026, 5, 19, 9, 0);

        List<IssueCluster> clusters = issueClusterService.cluster(List.of(
                article("ai-infra", NewsCategory.DOMESTIC_STOCK, "AI 인프라 투자 확대 수혜", publishedAt)
        ));

        assertEquals(1, clusters.size());
        IssueCluster cluster = clusters.get(0);
        assertEquals("20260519|THEME|AI|상승", cluster.clusterKey());
        assertEquals(InvestmentIssueCategory.THEME, cluster.category());
        assertEquals("AI", cluster.mainEntity());
        assertEquals("상승", cluster.mainEvent());
    }

    private FetchedNewsArticle article(String id, NewsCategory category, String title, LocalDateTime publishedAt) {
        return FetchedNewsArticle.builder()
                .id(id)
                .category(category)
                .title(title)
                .publishedAt(publishedAt)
                .externalUrl("https://example.com/" + id)
                .build();
    }

    private FetchedNewsArticle articleWithoutIdentity(String id,
                                                     NewsCategory category,
                                                     String title,
                                                     LocalDateTime publishedAt) {
        return articleWithoutIdentity(id, category, title, publishedAt, null, null);
    }

    private FetchedNewsArticle articleWithoutIdentity(String id,
                                                     NewsCategory category,
                                                     String title,
                                                     LocalDateTime publishedAt,
                                                     String sourceName,
                                                     String summary) {
        return FetchedNewsArticle.builder()
                .id(id)
                .category(category)
                .title(title)
                .summary(summary)
                .sourceName(sourceName)
                .publishedAt(publishedAt)
                .build();
    }

    private Map<String, String> clusterKeyToArticleSourceAndSummary(List<IssueCluster> clusters) {
        return clusters.stream()
                .collect(java.util.stream.Collectors.toMap(
                        IssueCluster::clusterKey,
                        cluster -> {
                            FetchedNewsArticle article = cluster.articles().get(0);
                            return article.getSourceName() + "|" + article.getSummary();
                        }
                ));
    }

    private Set<String> clusterKeys(List<IssueCluster> clusters) {
        return clusters.stream()
                .map(IssueCluster::clusterKey)
                .collect(java.util.stream.Collectors.toSet());
    }
}
