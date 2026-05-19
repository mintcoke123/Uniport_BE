package com.uniport.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RawNewsDeduplicator {

    private final RawNewsNormalizer normalizer;

    public RawNewsDeduplicator() {
        this(new RawNewsNormalizer());
    }

    public RawNewsDeduplicator(RawNewsNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public List<FetchedNewsArticle> deduplicate(List<FetchedNewsArticle> articles) {
        if (articles == null || articles.isEmpty()) {
            return List.of();
        }
        Map<String, FetchedNewsArticle> deduped = new LinkedHashMap<>();
        for (FetchedNewsArticle article : articles) {
            String key = dedupeKey(article);
            if (key.isBlank()) {
                continue;
            }
            FetchedNewsArticle existing = deduped.get(key);
            if (existing == null || shouldPreferArticle(article, existing)) {
                deduped.put(key, article);
            }
        }
        return List.copyOf(new ArrayList<>(deduped.values()));
    }

    private String dedupeKey(FetchedNewsArticle article) {
        if (article == null) {
            return "";
        }
        String externalUrl = article.getExternalUrl();
        if (externalUrl != null && !externalUrl.isBlank()) {
            return "url:" + externalUrl.trim();
        }
        return "title:" + normalizer.cleanDisplayText(article.getTitle());
    }

    private boolean shouldPreferArticle(FetchedNewsArticle candidate, FetchedNewsArticle existing) {
        return categoryPriority(candidate.getCategory()) > categoryPriority(existing.getCategory());
    }

    private int categoryPriority(NewsCategory category) {
        if (category == NewsCategory.DOMESTIC_STOCK || category == NewsCategory.OVERSEAS_STOCK) {
            return 2;
        }
        if (category == NewsCategory.MARKET) {
            return 1;
        }
        return 0;
    }
}
