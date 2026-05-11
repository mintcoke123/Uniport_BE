package com.uniport.service.backtest;

import java.math.BigDecimal;
import java.util.List;

public record EtfNewsExposure(
        BigDecimal positiveExposurePercent,
        BigDecimal negativeExposurePercent,
        int matchedNewsCount,
        List<HoldingNewsExposure> keyContributors,
        List<EtfRebalanceCandidate> rebalanceCandidates,
        List<String> riskPoints
) {
    public static EtfNewsExposure empty() {
        return new EtfNewsExposure(
                BigDecimal.ZERO.setScale(1),
                BigDecimal.ZERO.setScale(1),
                0,
                List.of(),
                List.of(),
                List.of()
        );
    }
}
