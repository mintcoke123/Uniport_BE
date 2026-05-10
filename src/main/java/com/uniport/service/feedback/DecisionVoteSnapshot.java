package com.uniport.service.feedback;

import java.util.Set;

public record DecisionVoteSnapshot(
        Long decisionId,
        Long proposerId,
        TradeSide side,
        String stockName,
        Set<Long> approveVoterIds,
        Set<Long> participantIds
) {
}
