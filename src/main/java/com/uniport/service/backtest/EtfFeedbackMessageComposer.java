package com.uniport.service.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        String profile = portfolioProfile(facts);
        String top = hasText(facts.topHoldingName()) ? facts.topHoldingName() : dominantSectorOrTop(facts);
        String lead = leadHoldingMix(facts);
        String leadContext = lead + holdingMixContext(facts);
        if (hasText(lead)) {
            if (isConcentrationRisk(facts)) {
                return "한 줄 결론: 방어력보다 " + leadContext + " 성과에 크게 기댄 \"" + profile + "\"입니다.";
            }
            if (facts.excessReturnPercent() != null && facts.excessReturnPercent().compareTo(BigDecimal.valueOf(-1)) <= 0) {
                return "한 줄 결론: " + leadContext + "가 기대만큼 시장을 이기지 못한 \"" + profile + "\"입니다.";
            }
            if (isHighVolatility(facts)) {
                return "한 줄 결론: " + leadContext + "가 상승 탄력과 흔들림을 함께 키우는 \"" + profile + "\"입니다.";
            }
            return "한 줄 결론: " + leadContext + "가 성장축인 \"" + profile + "\"입니다.";
        }
        if (isConcentrationRisk(facts)) {
            return "한 줄 결론: " + top + " 쪽으로 기울어 방어가 약한 \"" + profile + "\"입니다.";
        }
        if (facts.excessReturnPercent() != null && facts.excessReturnPercent().compareTo(BigDecimal.valueOf(-1)) <= 0) {
            return "한 줄 결론: 시장보다 힘이 약했던 \"" + profile + "\"입니다.";
        }
        if (isHighVolatility(facts)) {
            return "한 줄 결론: 상승 탄력만큼 흔들림도 큰 \"" + profile + "\"입니다.";
        }
        return "한 줄 결론: 큰 한 방보다 꾸준함을 기대하는 \"" + profile + "\"입니다.";
    }

    private static String portfolioProfile(InsightFacts facts) {
        String sector = dominantSectorOrTop(facts);
        if (normalize(facts.topHoldingWeightPercent()).compareTo(BigDecimal.valueOf(60)) >= 0) {
            return sector + "에 크게 베팅한 공격형 ETF";
        }
        if (normalize(facts.dominantSectorWeightPercent()).compareTo(BigDecimal.valueOf(75)) >= 0) {
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
        List<BacktestHolding> holdings = sortedHoldings(facts);
        if (!holdings.isEmpty()) {
            BacktestHolding top = holdings.get(0);
            message = "핵심 원인: " + holdingLabel(top);
            if (holdings.size() >= 2) {
                message += ", " + holdingLabel(holdings.get(1));
            }
            message += "가 ETF 성격을 정합니다.";
            if (hasText(facts.dominantSector())
                    && normalize(facts.dominantSectorWeightPercent()).compareTo(BigDecimal.valueOf(60)) >= 0) {
                message += " " + facts.dominantSector() + " 집중도 큽니다.";
            }
        } else if (hasText(facts.topHoldingName()) && facts.topHoldingWeightPercent() != null) {
            message = "핵심 원인: " + facts.topHoldingName()
                    + " 비중이 ETF 방향을 크게 좌우합니다.";
            if (hasText(facts.dominantSector())
                    && normalize(facts.dominantSectorWeightPercent()).compareTo(BigDecimal.valueOf(60)) >= 0) {
                message += " " + facts.dominantSector() + " 집중도 큽니다.";
            }
        } else if (facts.excessReturnPercent() != null) {
            message = "핵심 원인: " + facts.benchmarkName()
                    + "와 다른 종목 구성이 성과 차이를 만들었습니다.";
        } else {
            message = "핵심 원인: 보유 종목별 비중과 변동성이 ETF 성격을 결정합니다.";
        }
        return new FeedbackBullet(isConcentrationRisk(facts) ? "RISK" : "INFO", message);
    }

    private static FeedbackBullet riskBullet(InsightFacts facts) {
        List<BacktestHolding> holdings = sortedHoldings(facts);
        StringBuilder builder = new StringBuilder("최대 리스크 요인: ");
        if (facts.excessReturnPercent() != null && facts.excessReturnPercent().compareTo(BigDecimal.valueOf(-1)) <= 0) {
            builder.append(facts.benchmarkName())
                    .append("보다 약했던 구간이 있어 방어력과 성장 동력을 다시 봐야 합니다.");
        } else if (!holdings.isEmpty()) {
            builder.append(holdingName(holdings.get(0)))
                    .append(" 쪽 재료가 흔들리면 전체 인상이 먼저 흔들립니다.");
            if (holdings.size() >= 2) {
                builder.append(" ")
                        .append(holdingName(holdings.get(1)));
                if (holdings.size() >= 3) {
                    builder.append(", ").append(holdingName(holdings.get(2)));
                }
                if (holdings.size() >= 4) {
                    builder.append(", ").append(holdingName(holdings.get(3)));
                }
                builder.append("까지 같이 보면 성장주 편중과 방어력 부족 여부가 드러납니다.");
            }
        } else if (isConcentrationRisk(facts)) {
            builder.append("한 종목이나 섹터가 흔들리면 포트폴리오 전체 방어가 약해질 수 있습니다.");
        } else if (isHighVolatility(facts)) {
            builder.append("좋을 때는 빠르지만 하락장에서는 체감 손실이 커질 수 있습니다.");
        } else {
            builder.append("수익률을 더 키우기보다 하락 폭을 얼마나 안정적으로 관리하는지가 핵심입니다.");
        }
        return new FeedbackBullet("RISK", builder.toString());
    }

    private static FeedbackBullet checkpointBullet(InsightFacts facts) {
        String sector = hasText(facts.dominantSector()) && !"혼합".equals(facts.dominantSector())
                ? facts.dominantSector()
                : dominantSectorFromHoldings(facts.holdings());
        String checkpoints = checkpointText(sector);
        String holding = focusedHoldingText(facts);
        String newsRisk = newsRiskText(facts);
        String suffix = hasText(newsRisk) ? ", " + newsRisk : "";
        return new FeedbackBullet(
                "INFO",
                "조정 방향: 리밸런싱을 한다면 " + checkpoints + ", " + holding
                        + "를 확인하고 방어 역할을 해줄 노출이 부족한지 보세요" + suffix + "."
        );
    }

    private static String focusedHoldingText(InsightFacts facts) {
        List<BacktestHolding> holdings = sortedHoldings(facts);
        if (!holdings.isEmpty()) {
            List<String> names = holdings.stream()
                    .map(EtfFeedbackMessageComposer::holdingName)
                    .filter(EtfFeedbackMessageComposer::hasText)
                    .limit(2)
                    .toList();
            if (names.size() == 1) {
                return names.get(0) + " 실적 발표";
            }
            if (names.size() >= 2) {
                return names.get(0) + "와 " + names.get(1) + " 실적 발표";
            }
        }
        return hasText(facts.topHoldingName()) ? facts.topHoldingName() + " 실적 발표" : "주요 보유 종목 실적";
    }

    private static String leadHoldingMix(InsightFacts facts) {
        List<BacktestHolding> holdings = sortedHoldings(facts);
        if (holdings.isEmpty()) {
            return "";
        }
        if (holdings.size() == 1) {
            return holdingLabel(holdings.get(0));
        }
        return holdingLabel(holdings.get(0)) + "와 " + holdingLabel(holdings.get(1));
    }

    private static String holdingMixContext(InsightFacts facts) {
        if (!hasText(facts.dominantSector()) || "혼합".equals(facts.dominantSector())) {
            return "";
        }
        return " " + facts.dominantSector() + " 축";
    }

    private static List<BacktestHolding> sortedHoldings(InsightFacts facts) {
        if (facts.holdings() == null || facts.holdings().isEmpty()) {
            return List.of();
        }
        return facts.holdings().stream()
                .filter(holding -> holding != null && hasText(holdingName(holding)))
                .sorted((left, right) -> normalize(right.weightPercent()).compareTo(normalize(left.weightPercent())))
                .toList();
    }

    private static String holdingLabel(BacktestHolding holding) {
        return holdingName(holding) + " " + formatPercent(holding.weightPercent());
    }

    private static String holdingName(BacktestHolding holding) {
        if (holding == null) {
            return "";
        }
        if (hasText(holding.name())) {
            return holding.name();
        }
        return holding.securityId();
    }

    private static String newsRiskText(InsightFacts facts) {
        EtfNewsExposure newsExposure = facts.newsExposure();
        if (newsExposure != null && newsExposure.riskPoints() != null && !newsExposure.riskPoints().isEmpty()) {
            return compactRiskPoint(newsExposure.riskPoints().get(0));
        }
        if (facts.riskFacts() != null) {
            return facts.riskFacts().stream()
                    .filter(fact -> fact != null && fact.contains("뉴스"))
                    .findFirst()
                    .map(EtfFeedbackMessageComposer::compactRiskPoint)
                    .orElse("");
        }
        return "";
    }

    private static String compactRiskPoint(String value) {
        if (!hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        int colonIndex = normalized.indexOf(":");
        if (colonIndex >= 0 && colonIndex + 1 < normalized.length()) {
            normalized = normalized.substring(colonIndex + 1).trim();
        }
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
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
