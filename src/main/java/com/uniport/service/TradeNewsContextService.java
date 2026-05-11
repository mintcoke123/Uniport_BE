package com.uniport.service;

import com.uniport.entity.ManagedNewsArticle;
import com.uniport.repository.ManagedNewsArticleRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class TradeNewsContextService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final int WINDOW_HOURS = 24;

    private final ManagedNewsArticleRepository managedNewsArticleRepository;
    private final NewsSentimentAnalyzer newsSentimentAnalyzer;

    public TradeNewsContextService(ManagedNewsArticleRepository managedNewsArticleRepository,
                                   NewsSentimentAnalyzer newsSentimentAnalyzer) {
        this.managedNewsArticleRepository = managedNewsArticleRepository;
        this.newsSentimentAnalyzer = newsSentimentAnalyzer;
    }

    public TradeNewsContext summarize(String stockCode, String stockName, Instant executedAt) {
        if (executedAt == null) {
            return TradeNewsContext.empty();
        }
        LocalDateTime executedAtSeoul = LocalDateTime.ofInstant(executedAt, SEOUL_ZONE);
        LocalDateTime from = executedAtSeoul.minusHours(WINDOW_HOURS);
        LocalDateTime to = executedAtSeoul.plusHours(WINDOW_HOURS);
        List<AnalyzedArticle> articles = managedNewsArticleRepository.searchByStock(stockCode, stockName).stream()
                .filter(article -> article.getPublishedAt() != null)
                .filter(article -> !article.getPublishedAt().isBefore(from) && !article.getPublishedAt().isAfter(to))
                .map(this::analyze)
                .toList();
        List<AnalyzedArticle> before = articles.stream()
                .filter(article -> !article.publishedAt().isAfter(executedAtSeoul))
                .toList();
        List<AnalyzedArticle> after = articles.stream()
                .filter(article -> article.publishedAt().isAfter(executedAtSeoul))
                .toList();

        String beforeSentiment = dominantSentiment(before);
        String afterSentiment = dominantSentiment(after);
        return new TradeNewsContext(
                before.size(),
                after.size(),
                beforeSentiment,
                afterSentiment,
                feedbackHint(beforeSentiment, afterSentiment),
                before.stream().map(AnalyzedArticle::headline).limit(3).toList(),
                after.stream().map(AnalyzedArticle::headline).limit(3).toList()
        );
    }

    private AnalyzedArticle analyze(ManagedNewsArticle article) {
        NewsSentimentAnalysis analysis = newsSentimentAnalyzer.analyze(new NewsSentimentInput(
                article.getNewsKey() != null ? article.getNewsKey() : String.valueOf(article.getId()),
                article.getTitle(),
                article.getSummary(),
                article.getContent(),
                article.getSourceLabel()
        ));
        return new AnalyzedArticle(article.getPublishedAt(), article.getTitle(), analysis);
    }

    private String dominantSentiment(List<AnalyzedArticle> articles) {
        if (articles.isEmpty()) {
            return "UNKNOWN";
        }
        double score = articles.stream()
                .mapToDouble(article -> article.analysis().type() == NewsSentimentType.POSITIVE
                        ? article.analysis().score()
                        : -article.analysis().score())
                .average()
                .orElse(0.0);
        return score >= 0 ? NewsSentimentType.POSITIVE.name() : NewsSentimentType.NEGATIVE.name();
    }

    private String feedbackHint(String beforeSentiment, String afterSentiment) {
        if ("NEGATIVE".equals(beforeSentiment) && "POSITIVE".equals(afterSentiment)) {
            return "매수 당시 관련 뉴스는 악재가 우세했지만 이후 호재 뉴스가 늘었어요.";
        }
        if ("POSITIVE".equals(beforeSentiment) && "NEGATIVE".equals(afterSentiment)) {
            return "체결 전에는 호재 뉴스가 우세했지만 이후 악재 뉴스가 늘었어요.";
        }
        if ("NEGATIVE".equals(beforeSentiment)) {
            return "체결 당시 관련 뉴스는 악재가 우세했어요.";
        }
        if ("POSITIVE".equals(beforeSentiment)) {
            return "체결 당시 관련 뉴스는 호재가 우세했어요.";
        }
        return "";
    }

    private record AnalyzedArticle(
            LocalDateTime publishedAt,
            String headline,
            NewsSentimentAnalysis analysis
    ) {
    }
}
