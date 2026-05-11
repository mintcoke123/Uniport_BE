package com.uniport.service.backtest;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder(toBuilder = true)
public record InsightFacts(
        String portfolioLabel,
        String periodLabel,
        BigDecimal principalAmountKrw,
        BigDecimal totalReturnPercent,
        BigDecimal expectedProfitAmountKrw,
        BigDecimal volatilityPercent,
        BigDecimal maxDrawdownPercent,
        String benchmarkName,
        BigDecimal benchmarkReturnPercent,
        BigDecimal excessReturnPercent,
        String riskGrade,
        String riskGradeLabel,
        String topHoldingName,
        BigDecimal topHoldingWeightPercent,
        BigDecimal top3WeightPercent,
        String dominantSector,
        BigDecimal dominantSectorWeightPercent,
        List<BacktestHolding> holdings,
        List<String> positiveFacts,
        List<String> riskFacts,
        String disclaimer
) {
}
