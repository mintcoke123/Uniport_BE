package com.uniport.service;

import com.uniport.entity.ManagedNewsArticle;
import com.uniport.repository.ManagedNewsArticleRepository;
import com.uniport.service.backtest.BacktestHolding;
import com.uniport.service.backtest.EtfNewsExposure;
import com.uniport.service.backtest.EtfRebalanceCandidate;
import com.uniport.service.backtest.HoldingNewsExposure;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EtfNewsExposureService {

    private static final int MAX_NEWS_PER_HOLDING = 5;

    private final ManagedNewsArticleRepository managedNewsArticleRepository;
    private final NewsSentimentAnalyzer newsSentimentAnalyzer;
    private final Map<String, NewsSentimentAnalysis> sentimentCache = new ConcurrentHashMap<>();

    public EtfNewsExposureService(ManagedNewsArticleRepository managedNewsArticleRepository,
                                  NewsSentimentAnalyzer newsSentimentAnalyzer) {
        this.managedNewsArticleRepository = managedNewsArticleRepository;
        this.newsSentimentAnalyzer = newsSentimentAnalyzer;
    }

    public EtfNewsExposure summarize(List<BacktestHolding> holdings) {
        if (holdings == null || holdings.isEmpty()) {
            return EtfNewsExposure.empty();
        }

        List<HoldingAggregate> aggregates = holdings.stream()
                .filter(holding -> holding != null && normalizedWeight(holding).compareTo(BigDecimal.ZERO) > 0)
                .map(this::summarizeHolding)
                .filter(aggregate -> aggregate.newsCount() > 0)
                .toList();
        if (aggregates.isEmpty()) {
            return EtfNewsExposure.empty();
        }

        BigDecimal positiveExposure = aggregates.stream()
                .filter(HoldingAggregate::positive)
                .map(HoldingAggregate::weightPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal negativeExposure = aggregates.stream()
                .filter(aggregate -> !aggregate.positive())
                .map(HoldingAggregate::weightPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int matchedNewsCount = aggregates.stream().mapToInt(HoldingAggregate::newsCount).sum();
        List<HoldingNewsExposure> keyContributors = aggregates.stream()
                .sorted(Comparator.comparing(HoldingAggregate::impactScore).reversed())
                .limit(3)
                .map(HoldingAggregate::toExposure)
                .toList();
        List<EtfRebalanceCandidate> rebalanceCandidates = aggregates.stream()
                .sorted(Comparator.comparing(HoldingAggregate::impactScore).reversed())
                .limit(3)
                .map(this::rebalanceCandidate)
                .toList();
        List<String> riskPoints = aggregates.stream()
                .filter(aggregate -> !aggregate.positive())
                .sorted(Comparator.comparing(HoldingAggregate::impactScore).reversed())
                .limit(3)
                .map(this::riskPoint)
                .toList();

        return new EtfNewsExposure(
                scalePercent(positiveExposure),
                scalePercent(negativeExposure),
                matchedNewsCount,
                keyContributors,
                rebalanceCandidates,
                riskPoints
        );
    }

    private HoldingAggregate summarizeHolding(BacktestHolding holding) {
        String stockCode = stockCode(holding.securityId());
        List<ManagedNewsArticle> articles = managedNewsArticleRepository.searchByStock(stockCode, holding.name());
        if (articles == null || articles.isEmpty()) {
            return HoldingAggregate.empty(holding);
        }

        List<NewsSentimentAnalysis> analyses = new ArrayList<>();
        List<ManagedNewsArticle> limitedArticles = articles.stream().limit(MAX_NEWS_PER_HOLDING).toList();
        for (ManagedNewsArticle article : limitedArticles) {
            analyses.add(sentimentFor(article));
        }

        double signedScore = analyses.stream()
                .mapToDouble(analysis -> analysis.type() == NewsSentimentType.POSITIVE ? analysis.score() : -analysis.score())
                .average()
                .orElse(0.0);
        NewsSentimentAnalysis latestAnalysis = analyses.get(0);
        ManagedNewsArticle latestArticle = limitedArticles.get(0);
        return new HoldingAggregate(
                holding.securityId(),
                holding.name(),
                scalePercent(normalizedWeight(holding)),
                signedScore >= 0,
                Math.abs(signedScore),
                analyses.size(),
                latestAnalysis.reason(),
                latestArticle.getTitle()
        );
    }

    private NewsSentimentInput toInput(ManagedNewsArticle article) {
        String newsId = article.getNewsKey() != null ? article.getNewsKey() : String.valueOf(article.getId());
        return new NewsSentimentInput(
                newsId,
                article.getTitle(),
                article.getSummary(),
                article.getContent(),
                article.getSourceLabel()
        );
    }

    private NewsSentimentAnalysis sentimentFor(ManagedNewsArticle article) {
        return sentimentCache.computeIfAbsent(cacheKey(article), ignored -> newsSentimentAnalyzer.analyze(toInput(article)));
    }

    private String cacheKey(ManagedNewsArticle article) {
        String newsId = article.getNewsKey() != null ? article.getNewsKey() : String.valueOf(article.getId());
        String stockCode = article.getStockCode() != null ? article.getStockCode() : "";
        String publishedAt = article.getPublishedAt() != null ? article.getPublishedAt().toString() : "";
        return newsId + "|" + stockCode + "|" + publishedAt;
    }

    private String riskPoint(HoldingAggregate aggregate) {
        return aggregate.name() + " " + formatPercent(aggregate.weightPercent())
                + "에 악재 뉴스가 우세해요: " + aggregate.reason();
    }

    private EtfRebalanceCandidate rebalanceCandidate(HoldingAggregate aggregate) {
        String sentiment = aggregate.positive() ? NewsSentimentType.POSITIVE.name() : NewsSentimentType.NEGATIVE.name();
        String action;
        String reason;
        if (!aggregate.positive()) {
            action = "REDUCE_WATCH";
            reason = aggregate.name() + "는 악재 뉴스가 우세해 비중 축소 점검 후보입니다.";
        } else if (aggregate.weightPercent().compareTo(BigDecimal.valueOf(20)) < 0) {
            action = "INCREASE_WATCH";
            reason = aggregate.name() + "는 호재 뉴스가 우세하지만 ETF 내 비중이 낮아 비중 확대 점검 후보입니다.";
        } else {
            action = "HOLD_WATCH";
            reason = aggregate.name() + "는 호재 뉴스가 우세하지만 이미 의미 있는 비중이라 유지 점검 후보입니다.";
        }
        return new EtfRebalanceCandidate(
                aggregate.securityId(),
                aggregate.name(),
                aggregate.weightPercent(),
                sentiment,
                action,
                reason
        );
    }

    private String stockCode(String securityId) {
        if (securityId == null || securityId.isBlank()) {
            return null;
        }
        String normalized = securityId.trim().toUpperCase(Locale.ROOT);
        int separator = normalized.lastIndexOf('_');
        return separator >= 0 ? normalized.substring(separator + 1) : normalized;
    }

    private BigDecimal normalizedWeight(BacktestHolding holding) {
        return holding.weightPercent() != null ? holding.weightPercent() : BigDecimal.ZERO;
    }

    private BigDecimal scalePercent(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP);
    }

    private String formatPercent(BigDecimal value) {
        return scalePercent(value).toPlainString() + "%";
    }

    private record HoldingAggregate(
            String securityId,
            String name,
            BigDecimal weightPercent,
            boolean positive,
            double sentimentScore,
            int newsCount,
            String reason,
            String latestHeadline
    ) {
        static HoldingAggregate empty(BacktestHolding holding) {
            return new HoldingAggregate(
                    holding.securityId(),
                    holding.name(),
                    holding.weightPercent(),
                    true,
                    0.0,
                    0,
                    "",
                    ""
            );
        }

        double impactScore() {
            return weightPercent.doubleValue() * sentimentScore;
        }

        HoldingNewsExposure toExposure() {
            return new HoldingNewsExposure(
                    securityId,
                    name,
                    weightPercent,
                    positive ? NewsSentimentType.POSITIVE.name() : NewsSentimentType.NEGATIVE.name(),
                    sentimentScore,
                    newsCount,
                    reason,
                    latestHeadline
            );
        }
    }
}
