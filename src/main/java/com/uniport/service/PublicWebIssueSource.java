package com.uniport.service;

import java.net.URI;

public record PublicWebIssueSource(
        String name,
        URI url,
        NewsCategory category,
        String sourceName,
        int maxItems
) {

    public PublicWebIssueSource {
        if (url == null) {
            throw new IllegalArgumentException("url must not be null");
        }
        name = blankToDefault(name, url.getHost());
        category = category == null ? NewsCategory.OVERSEAS_STOCK : category;
        sourceName = blankToDefault(sourceName, name);
        maxItems = Math.max(1, maxItems);
    }

    private static String blankToDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback == null ? "Public web source" : fallback;
        }
        return value.trim();
    }
}
