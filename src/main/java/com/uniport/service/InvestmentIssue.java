package com.uniport.service;

import java.time.LocalDateTime;
import java.util.List;

public record InvestmentIssue(
        String clusterKey,
        InvestmentIssueCategory category,
        String mainEntity,
        String mainEvent,
        String title,
        InvestmentIssueLabel label,
        String summary,
        List<String> reasonBullets,
        List<String> watchPoints,
        List<MappedStock> relatedStocks,
        List<MappedEtf> relatedEtfs,
        List<FetchedNewsArticle> sourceArticles,
        int sourceCount,
        LocalDateTime publishedAt,
        LocalDateTime updatedAt
) {

    public InvestmentIssue {
        reasonBullets = reasonBullets == null ? List.of() : List.copyOf(reasonBullets);
        watchPoints = watchPoints == null ? List.of() : List.copyOf(watchPoints);
        relatedStocks = relatedStocks == null ? List.of() : List.copyOf(relatedStocks);
        relatedEtfs = relatedEtfs == null ? List.of() : List.copyOf(relatedEtfs);
        sourceArticles = sourceArticles == null ? List.of() : List.copyOf(sourceArticles);
        sourceCount = sourceCount < 0 ? sourceArticles.size() : sourceCount;
    }
}
