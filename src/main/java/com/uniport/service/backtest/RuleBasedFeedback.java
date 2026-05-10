package com.uniport.service.backtest;

import java.util.List;

public record RuleBasedFeedback(
        String title,
        String summary,
        List<FeedbackBullet> bullets,
        String tone,
        String disclaimer,
        boolean usedFallback
) {
}
