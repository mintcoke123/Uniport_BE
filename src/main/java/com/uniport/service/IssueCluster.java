package com.uniport.service;

import java.util.List;

public record IssueCluster(
        String clusterKey,
        InvestmentIssueCategory category,
        String mainEntity,
        String mainEvent,
        List<FetchedNewsArticle> articles
) {

    public IssueCluster {
        articles = articles == null ? List.of() : List.copyOf(articles);
    }
}
