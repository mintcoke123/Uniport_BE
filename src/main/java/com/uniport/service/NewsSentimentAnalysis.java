package com.uniport.service;

record NewsSentimentAnalysis(
        NewsSentimentType type,
        double score,
        String reason
) {
    String sentiment() {
        return type.name();
    }

    String label() {
        return type.label();
    }

    static NewsSentimentAnalysis positive(double score, String reason) {
        return new NewsSentimentAnalysis(NewsSentimentType.POSITIVE, score, reason);
    }

    static NewsSentimentAnalysis negative(double score, String reason) {
        return new NewsSentimentAnalysis(NewsSentimentType.NEGATIVE, score, reason);
    }
}
