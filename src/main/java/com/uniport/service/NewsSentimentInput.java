package com.uniport.service;

record NewsSentimentInput(
        String newsId,
        String title,
        String summary,
        String body,
        String sourceName
) {
    String textForAnalysis() {
        return String.join(" ",
                valueOrEmpty(title),
                valueOrEmpty(summary),
                valueOrEmpty(body),
                valueOrEmpty(sourceName)
        ).trim();
    }

    private static String valueOrEmpty(String value) {
        return value != null ? value : "";
    }
}
