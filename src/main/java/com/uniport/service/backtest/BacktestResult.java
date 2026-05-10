package com.uniport.service.backtest;

import java.math.BigDecimal;
import java.util.List;

public record BacktestResult(
        BigDecimal initialNavKrw,
        BigDecimal finalNavKrw,
        BigDecimal profitAmountKrw,
        BigDecimal totalReturnPercent,
        BigDecimal annualizedReturnPercent,
        BigDecimal volatilityPercent,
        BigDecimal maxDrawdownPercent,
        BigDecimal benchmarkReturnPercent,
        BigDecimal excessReturnPercent,
        BigDecimal sharpeRatio,
        BigDecimal hhi,
        String topHoldingName,
        BigDecimal topHoldingWeightPercent,
        BigDecimal top3WeightPercent,
        String dominantSector,
        BigDecimal dominantSectorWeightPercent,
        Integer riskScore,
        String riskGrade,
        String riskGradeLabel,
        Integer tradingDays,
        List<BacktestNavPoint> navSeries
) {
}
