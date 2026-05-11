package com.uniport.service;

import java.util.List;

public record TradeNewsContext(
        int beforeNewsCount,
        int afterNewsCount,
        String beforeSentiment,
        String afterSentiment,
        String feedbackHint,
        List<String> beforeHeadlines,
        List<String> afterHeadlines
) {
    public static TradeNewsContext empty() {
        return new TradeNewsContext(0, 0, "UNKNOWN", "UNKNOWN", "", List.of(), List.of());
    }
}
