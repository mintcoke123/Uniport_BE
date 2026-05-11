package com.uniport.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class DefaultNewsSentimentAnalyzer implements NewsSentimentAnalyzer {

    private final FinbertSentimentClient finbertSentimentClient;
    private final KeywordNewsSentimentAnalyzer fallbackAnalyzer;
    private final double minConfidence;

    DefaultNewsSentimentAnalyzer(FinbertSentimentClient finbertSentimentClient,
                                 @Value("${finbert.sentiment.min-confidence:0.60}") double minConfidence) {
        this.finbertSentimentClient = finbertSentimentClient;
        this.fallbackAnalyzer = new KeywordNewsSentimentAnalyzer();
        this.minConfidence = minConfidence;
    }

    @Override
    public NewsSentimentAnalysis analyze(NewsSentimentInput input) {
        return finbertSentimentClient.analyze(input)
                .filter(result -> result.score() >= minConfidence)
                .orElseGet(() -> fallbackAnalyzer.analyze(input));
    }
}
