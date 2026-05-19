package com.uniport.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RawNewsDeduplicatorTest {

    @Test
    void deduplicate_keepsOneArticleForSameExternalUrl() {
        RawNewsDeduplicator deduplicator = new RawNewsDeduplicator();

        List<FetchedNewsArticle> result = deduplicator.deduplicate(List.of(
                article("a", "https://example.com/news", "삼성전자 HBM 기대감 상승"),
                article("b", "https://example.com/news", "삼성전자 HBM 기대감 상승")
        ));

        assertEquals(1, result.size());
        assertEquals("a", result.get(0).getId());
    }

    @Test
    void deduplicate_prefersStockCategoryWhenSameUrlAppearsInMarketAndStockFeeds() {
        RawNewsDeduplicator deduplicator = new RawNewsDeduplicator();

        List<FetchedNewsArticle> result = deduplicator.deduplicate(List.of(
                article("market", NewsCategory.MARKET, "https://example.com/news", "삼성전자 HBM 기대감 상승"),
                article("stock", NewsCategory.DOMESTIC_STOCK, "https://example.com/news", "삼성전자 HBM 기대감 상승")
        ));

        assertEquals(1, result.size());
        assertEquals("stock", result.get(0).getId());
        assertEquals(NewsCategory.DOMESTIC_STOCK, result.get(0).getCategory());
    }

    @Test
    void deduplicate_usesNormalizedDisplayTitleWhenExternalUrlIsBlank() {
        RawNewsDeduplicator deduplicator = new RawNewsDeduplicator();

        List<FetchedNewsArticle> result = deduplicator.deduplicate(List.of(
                article("a", "", "코스피 <b>상승</b> 출발"),
                article("b", "  ", "코스피 상승   출발")
        ));

        assertEquals(1, result.size());
        assertEquals("a", result.get(0).getId());
    }

    private FetchedNewsArticle article(String id, String externalUrl, String title) {
        return article(id, NewsCategory.MARKET, externalUrl, title);
    }

    private FetchedNewsArticle article(String id, NewsCategory category, String externalUrl, String title) {
        return FetchedNewsArticle.builder()
                .id(id)
                .category(category)
                .title(title)
                .externalUrl(externalUrl)
                .build();
    }
}
