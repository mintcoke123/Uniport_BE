package com.uniport.service.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

final class EtfFeedbackMessageComposer {

    private EtfFeedbackMessageComposer() {
    }

    static RuleBasedFeedback compose(InsightFacts facts, boolean negativeSignal, boolean usedFallback) {
        String tone = negativeSignal || isCaution(facts) ? "CAUTION" : "BALANCED";
        return new RuleBasedFeedback(
                "AI 리스크 진단",
                conclusion(facts),
                bullets(facts),
                tone,
                facts.disclaimer(),
                usedFallback
        );
    }

    private static String conclusion(InsightFacts facts) {
        StringBuilder builder = new StringBuilder()
                .append("한 줄 결론: 이 포트폴리오는 \"")
                .append(portfolioProfile(facts))
                .append("\"에 가깝습니다.");
        List<String> context = new ArrayList<>();
        if (hasText(facts.topHoldingName()) && facts.topHoldingWeightPercent() != null) {
            context.add("최대 비중 " + facts.topHoldingName() + " " + formatPercent(facts.topHoldingWeightPercent()));
        }
        if (hasText(facts.dominantSector()) && facts.dominantSectorWeightPercent() != null) {
            context.add(facts.dominantSector() + " 비중 " + formatPercent(facts.dominantSectorWeightPercent()));
        }
        if (!context.isEmpty()) {
            builder.append(" ").append(String.join(", ", context)).append("입니다.");
        }
        return builder.toString();
    }

    private static String portfolioProfile(InsightFacts facts) {
        String sector = dominantSectorOrTop(facts);
        if (normalize(facts.topHoldingWeightPercent()).compareTo(BigDecimal.valueOf(60)) >= 0) {
            return sector + "에 크게 베팅한 공격형 ETF";
        }
        if (normalize(facts.dominantSectorWeightPercent()).compareTo(BigDecimal.valueOf(60)) >= 0 && isHighVolatility(facts)) {
            return sector + "에 크게 베팅한 공격형 ETF";
        }
        if (normalize(facts.dominantSectorWeightPercent()).compareTo(BigDecimal.valueOf(60)) >= 0) {
            return sector + " 집중형 ETF";
        }
        if (normalize(facts.topHoldingWeightPercent()).compareTo(BigDecimal.valueOf(40)) >= 0) {
            return "상위 종목 집중형 ETF";
        }
        if (facts.excessReturnPercent() != null && facts.excessReturnPercent().compareTo(BigDecimal.valueOf(-1)) <= 0) {
            return "벤치마크 대비 점검이 필요한 ETF";
        }
        if (isHighVolatility(facts)) {
            return "수익 기회와 하락 위험이 함께 큰 변동성 ETF";
        }
        if (normalize(facts.topHoldingWeightPercent()).compareTo(BigDecimal.valueOf(30)) <= 0
                && facts.top3WeightPercent() != null
                && facts.top3WeightPercent().compareTo(BigDecimal.valueOf(70)) <= 0) {
            return "종목 분산이 비교적 좋은 균형형 ETF";
        }
        return "성과와 리스크를 함께 확인해야 하는 균형형 ETF";
    }

    private static List<FeedbackBullet> bullets(InsightFacts facts) {
        return List.of(
                coreReasonBullet(facts),
                riskBullet(facts),
                checkpointBullet(facts)
        );
    }

    private static FeedbackBullet coreReasonBullet(InsightFacts facts) {
        String message;
        if (hasText(facts.topHoldingName()) && facts.topHoldingWeightPercent() != null) {
            message = "핵심 원인: " + facts.topHoldingName() + " " + formatPercent(facts.topHoldingWeightPercent())
                    + "가 ETF 흐름을 가장 크게 좌우합니다.";
            if (hasText(facts.dominantSector())
                    && normalize(facts.dominantSectorWeightPercent()).compareTo(BigDecimal.valueOf(60)) >= 0) {
                message += " " + facts.dominantSector() + " 비중이 "
                        + formatPercent(facts.dominantSectorWeightPercent()) + "로 집중된 구조라 업황 변화가 바로 반영됩니다.";
            } else if (facts.top3WeightPercent() != null) {
                message += " 상위 3개 종목 비중은 " + formatPercent(facts.top3WeightPercent()) + "입니다.";
            }
        } else if (facts.excessReturnPercent() != null) {
            message = "핵심 원인: " + facts.benchmarkName() + " 대비 "
                    + formatPercentPoint(facts.excessReturnPercent()) + " 차이가 성과를 설명합니다.";
        } else {
            message = "핵심 원인: 보유 종목별 비중과 변동성이 ETF 성격을 결정합니다.";
        }
        return new FeedbackBullet(isConcentrationRisk(facts) ? "RISK" : "INFO", message);
    }

    private static FeedbackBullet riskBullet(InsightFacts facts) {
        StringBuilder builder = new StringBuilder()
                .append("가장 큰 리스크: 최대 낙폭 ")
                .append(formatPercent(facts.maxDrawdownPercent()))
                .append("와 변동성 ")
                .append(formatPercent(facts.volatilityPercent()))
                .append("입니다.");
        if (facts.excessReturnPercent() != null && facts.excessReturnPercent().compareTo(BigDecimal.valueOf(-1)) <= 0) {
            builder.append(" ").append(facts.benchmarkName()).append("보다 ")
                    .append(formatPercentPoint(facts.excessReturnPercent().abs()))
                    .append(" 낮아 방어력과 성장 동력을 다시 봐야 합니다.");
        } else if (isConcentrationRisk(facts)) {
            builder.append(" 한 종목이나 한 섹터 충격이 전체 수익률로 빠르게 번질 수 있습니다.");
        } else if (isHighVolatility(facts)) {
            builder.append(" 하락장에서는 회복 시간이 길어질 수 있습니다.");
        } else {
            builder.append(" 현재 구조에서는 수익률보다 하락 폭 관리가 핵심입니다.");
        }
        return new FeedbackBullet("RISK", builder.toString());
    }

    private static FeedbackBullet checkpointBullet(InsightFacts facts) {
        String sector = hasText(facts.dominantSector()) && !"혼합".equals(facts.dominantSector())
                ? facts.dominantSector()
                : dominantSectorFromHoldings(facts.holdings());
        String checkpoints = checkpointText(sector);
        String holding = hasText(facts.topHoldingName()) ? facts.topHoldingName() + " 실적 발표" : "주요 보유 종목 실적";
        return new FeedbackBullet(
                "INFO",
                "확인할 것: " + checkpoints + ", " + holding + "를 함께 보세요."
        );
    }

    private static boolean isCaution(InsightFacts facts) {
        return isConcentrationRisk(facts)
                || isHighVolatility(facts)
                || (facts.excessReturnPercent() != null && facts.excessReturnPercent().compareTo(BigDecimal.valueOf(-1)) <= 0);
    }

    private static boolean isConcentrationRisk(InsightFacts facts) {
        return normalize(facts.topHoldingWeightPercent()).compareTo(BigDecimal.valueOf(40)) >= 0
                || normalize(facts.dominantSectorWeightPercent()).compareTo(BigDecimal.valueOf(60)) >= 0;
    }

    private static boolean isHighVolatility(InsightFacts facts) {
        return normalize(facts.volatilityPercent()).compareTo(BigDecimal.valueOf(20)) >= 0
                || normalize(facts.maxDrawdownPercent()).compareTo(BigDecimal.valueOf(-20)) <= 0;
    }

    private static String dominantSectorOrTop(InsightFacts facts) {
        if (hasText(facts.dominantSector())) {
            return facts.dominantSector();
        }
        return "상위 종목";
    }

    private static String dominantSectorFromHoldings(List<BacktestHolding> holdings) {
        if (holdings == null || holdings.isEmpty()) {
            return "";
        }
        return holdings.stream()
                .filter(holding -> holding != null && hasText(holding.sector()))
                .findFirst()
                .map(BacktestHolding::sector)
                .orElse("");
    }

    private static String checkpointText(String sector) {
        String normalized = sector == null ? "" : sector;
        if (normalized.contains("반도체")) {
            return "AI 칩 수요, 재고 사이클, 수출 규제";
        }
        if (normalized.contains("전기차") || normalized.contains("모빌리티")) {
            return "인도량, 배터리 원가, 보조금 정책";
        }
        if (normalized.contains("금융") || normalized.contains("은행")) {
            return "금리 방향, 대출 건전성, 경기 둔화";
        }
        if (normalized.contains("헬스") || normalized.contains("바이오") || normalized.contains("제약")) {
            return "신약 파이프라인, 특허 만료, 규제 이슈";
        }
        if (normalized.contains("빅테크") || normalized.contains("기술") || normalized.contains("테크")
                || normalized.contains("소프트웨어")) {
            return "실적 성장률, AI 투자 지출, 금리 변화";
        }
        return "실적 발표, 업종 뉴스, 금리 변화";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static String formatPercentPoint(BigDecimal value) {
        return formatDecimal(value) + "%p";
    }

    private static String formatPercent(BigDecimal value) {
        return formatDecimal(normalize(value)) + "%";
    }

    private static String formatDecimal(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }
}
