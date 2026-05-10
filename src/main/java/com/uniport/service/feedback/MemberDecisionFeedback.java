package com.uniport.service.feedback;

import java.math.BigDecimal;

public record MemberDecisionFeedback(
        Long memberId,
        String nickname,
        String avatarUrl,
        String representativeDecision,
        String level,
        BigDecimal contributionAmount,
        BigDecimal contributionRate,
        int participatedDecisionCount,
        int totalDecisionCount,
        BigDecimal participationRate,
        int sortOrder
) {
}
