package com.uniport.service.backtest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EtfAiFeedbackService {

    private static final String DEFAULT_DISCLAIMER = "과거 데이터 기반 백테스트이며 미래 수익을 보장하지 않습니다.";
    private static final List<String> PROHIBITED_WORDS = List.of(
            "무조건", "확실히", "보장", "매수", "매도", "추천 종목", "수익을 낼 수밖에"
    );
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d[\\d,]*(?:\\.\\d+)?(?:%p|%|원|만원|억원)?");

    private final List<LlmFeedbackClient> llmFeedbackClients;

    public EtfAiFeedbackService() {
        this(List.of());
    }

    @Autowired
    public EtfAiFeedbackService(List<LlmFeedbackClient> llmFeedbackClients) {
        this.llmFeedbackClients = llmFeedbackClients != null ? llmFeedbackClients : List.of();
    }

    public EtfAiFeedbackService(LlmFeedbackClient llmFeedbackClient) {
        this(llmFeedbackClient != null ? List.of(llmFeedbackClient) : List.of());
    }

    public InsightFacts buildInsightFacts(String portfolioLabel,
                                          String periodLabel,
                                          String benchmarkName,
                                          BacktestResult result) {
        return buildInsightFacts(portfolioLabel, periodLabel, benchmarkName, result, List.of());
    }

    public InsightFacts buildInsightFacts(String portfolioLabel,
                                          String periodLabel,
                                          String benchmarkName,
                                          BacktestResult result,
                                          List<BacktestHolding> holdings) {
        return buildInsightFacts(portfolioLabel, periodLabel, benchmarkName, result, holdings, EtfNewsExposure.empty());
    }

    public InsightFacts buildInsightFacts(String portfolioLabel,
                                          String periodLabel,
                                          String benchmarkName,
                                          BacktestResult result,
                                          List<BacktestHolding> holdings,
                                          EtfNewsExposure newsExposure) {
        List<String> positiveFacts = new ArrayList<>();
        List<String> riskFacts = new ArrayList<>();
        if (result.excessReturnPercent() != null) {
            if (result.excessReturnPercent().compareTo(BigDecimal.valueOf(5)) >= 0) {
                positiveFacts.add("벤치마크보다 " + formatPercentPoint(result.excessReturnPercent()) + " 높은 성과를 보였습니다.");
            } else if (result.excessReturnPercent().compareTo(BigDecimal.ONE) >= 0) {
                positiveFacts.add("벤치마크 대비 소폭 우위를 보였습니다.");
            } else if (result.excessReturnPercent().compareTo(BigDecimal.valueOf(-5)) <= 0) {
                riskFacts.add("벤치마크보다 " + formatPercentPoint(result.excessReturnPercent().abs()) + " 낮은 성과를 보였습니다.");
            }
        }
        if (result.maxDrawdownPercent().compareTo(BigDecimal.valueOf(-10)) > 0) {
            positiveFacts.add("최대 낙폭이 " + formatPercent(result.maxDrawdownPercent()) + "로 제한적이었습니다.");
        } else if (result.maxDrawdownPercent().compareTo(BigDecimal.valueOf(-25)) <= 0) {
            riskFacts.add("큰 하락 구간에서 " + formatPercent(result.maxDrawdownPercent()) + "까지 내려갔습니다.");
        }
        if (result.topHoldingWeightPercent().compareTo(BigDecimal.valueOf(40)) >= 0) {
            riskFacts.add("단일 종목 비중이 " + formatPercent(result.topHoldingWeightPercent()) + "로 높습니다.");
        }
        if (result.top3WeightPercent().compareTo(BigDecimal.valueOf(75)) >= 0) {
            riskFacts.add("상위 3개 종목 비중이 " + formatPercent(result.top3WeightPercent()) + "로 높습니다.");
        }
        if (result.dominantSectorWeightPercent() != null
                && result.dominantSectorWeightPercent().compareTo(BigDecimal.valueOf(60)) >= 0) {
            riskFacts.add(result.dominantSector() + " 비중이 " + formatPercent(result.dominantSectorWeightPercent()) + "로 한쪽에 집중되어 있습니다.");
        }
        if (result.topHoldingWeightPercent().compareTo(BigDecimal.valueOf(30)) <= 0
                && result.hhi().compareTo(BigDecimal.valueOf(0.18)) < 0) {
            positiveFacts.add("종목 분산이 비교적 양호합니다.");
        }
        if (result.benchmarkReturnPercent() == null) {
            riskFacts.add("벤치마크 과거 가격 데이터가 없어 초과 성과는 계산하지 않았습니다.");
        }
        EtfNewsExposure safeNewsExposure = newsExposure != null ? newsExposure : EtfNewsExposure.empty();
        if (safeNewsExposure.matchedNewsCount() > 0) {
            if (safeNewsExposure.positiveExposurePercent().compareTo(BigDecimal.ZERO) > 0) {
                positiveFacts.add("호재 뉴스 노출 비중은 "
                        + formatPercent(safeNewsExposure.positiveExposurePercent()) + "입니다.");
            }
            if (safeNewsExposure.negativeExposurePercent().compareTo(BigDecimal.ZERO) > 0) {
                riskFacts.add("악재 뉴스 노출 비중은 "
                        + formatPercent(safeNewsExposure.negativeExposurePercent()) + "입니다.");
            }
            for (EtfRebalanceCandidate candidate : safeNewsExposure.rebalanceCandidates()) {
                if ("INCREASE_WATCH".equals(candidate.action())) {
                    positiveFacts.add(candidate.reason());
                } else {
                    riskFacts.add(candidate.reason());
                }
            }
            riskFacts.addAll(safeNewsExposure.riskPoints());
        }

        return InsightFacts.builder()
                .portfolioLabel(portfolioLabel)
                .periodLabel(periodLabel)
                .principalAmountKrw(result.initialNavKrw())
                .totalReturnPercent(result.totalReturnPercent())
                .expectedProfitAmountKrw(result.profitAmountKrw())
                .volatilityPercent(result.volatilityPercent())
                .maxDrawdownPercent(result.maxDrawdownPercent())
                .benchmarkName(benchmarkName)
                .benchmarkReturnPercent(result.benchmarkReturnPercent())
                .excessReturnPercent(result.excessReturnPercent())
                .riskGrade(result.riskGrade())
                .riskGradeLabel(result.riskGradeLabel())
                .topHoldingName(result.topHoldingName())
                .topHoldingWeightPercent(result.topHoldingWeightPercent())
                .top3WeightPercent(result.top3WeightPercent())
                .dominantSector(result.dominantSector())
                .dominantSectorWeightPercent(result.dominantSectorWeightPercent())
                .holdings(sortedHoldings(holdings))
                .newsExposure(safeNewsExposure)
                .positiveFacts(positiveFacts)
                .riskFacts(riskFacts)
                .disclaimer(DEFAULT_DISCLAIMER)
                .build();
    }

    public RuleBasedFeedback buildFallbackFeedback(InsightFacts facts) {
        List<FeedbackBullet> bullets = bullets(facts);
        String summary;
        String tone = "BALANCED";
        if (isConcentrationRisk(facts)) {
            summary = "백테스트 수익률은 " + formatPercent(facts.totalReturnPercent())
                    + "이고 최대 낙폭은 " + formatPercent(facts.maxDrawdownPercent()) + "였습니다. "
                    + topHoldingPhrase(facts)
                    + fallbackSector(facts) + " 비중이 " + formatPercent(fallbackConcentrationWeight(facts))
                    + "라 관련 업황 변화에 민감할 수 있습니다.";
            tone = "CAUTION";
        } else if (facts.excessReturnPercent() != null && facts.excessReturnPercent().compareTo(BigDecimal.valueOf(-1)) <= 0) {
            summary = "백테스트 기준 포트폴리오 수익률은 " + formatPercent(facts.totalReturnPercent())
                    + "였지만 " + facts.benchmarkName() + "보다 "
                    + formatPercentPoint(facts.excessReturnPercent().abs()) + " 낮았습니다. "
                    + portfolioContextPhrase(facts) + " 비중 조정이나 종목 분산을 다시 확인해보는 편이 좋아요.";
            tone = "CAUTION";
        } else if (facts.volatilityPercent().compareTo(BigDecimal.valueOf(20)) >= 0
                || facts.maxDrawdownPercent().compareTo(BigDecimal.valueOf(-20)) <= 0) {
            summary = "수익 기회는 있었지만 가격 변동도 큰 구성이에요. "
                    + facts.periodLabel() + " 기준 수익률은 " + formatPercent(facts.totalReturnPercent())
                    + "였고, 가장 큰 하락 구간에서는 " + formatPercent(facts.maxDrawdownPercent())
                    + "까지 내려갔습니다. " + portfolioContextPhrase(facts);
            tone = "CAUTION";
        } else if (facts.excessReturnPercent() != null) {
            summary = "백테스트 기준 " + facts.periodLabel() + " 동안 원금 대비 "
                    + formatPercent(facts.totalReturnPercent()) + "의 수익 구간이 관찰됐어요. "
                    + facts.benchmarkName() + "보다 " + formatPercentPoint(facts.excessReturnPercent())
                    + " 높았고, 최대 낙폭은 " + formatPercent(facts.maxDrawdownPercent())
                    + "였습니다. " + portfolioContextPhrase(facts);
        } else {
            summary = "백테스트 기준 " + facts.periodLabel() + " 동안 원금 대비 "
                    + formatPercent(facts.totalReturnPercent()) + "의 수익 구간이 관찰됐어요. "
                    + "벤치마크 데이터는 아직 연결되지 않아 포트폴리오 자체의 변동성과 낙폭을 중심으로 확인해주세요. "
                    + portfolioContextPhrase(facts);
        }
        return new RuleBasedFeedback("AI 리스크 진단", summary, bullets, tone, facts.disclaimer(), true);
    }

    public RuleBasedFeedback buildFeedback(InsightFacts facts) {
        for (LlmFeedbackClient client : llmFeedbackClients) {
            Optional<RuleBasedFeedback> generated = client.generate(facts);
            if (generated.isPresent()) {
                return validateOrFallback(generated.get(), facts);
            }
        }
        return buildFallbackFeedback(facts);
    }

    public String modelName() {
        return llmFeedbackClients.stream()
                .map(LlmFeedbackClient::modelName)
                .filter(name -> name != null && !name.isBlank() && !"none".equals(name))
                .findFirst()
                .orElse("none");
    }

    public String promptVersion() {
        return llmFeedbackClients.stream()
                .map(LlmFeedbackClient::promptVersion)
                .filter(version -> version != null && !version.isBlank() && !"none".equals(version))
                .findFirst()
                .orElse("none");
    }

    public RuleBasedFeedback validateOrFallback(RuleBasedFeedback generated, InsightFacts facts) {
        if (generated == null || containsProhibitedExpression(generated) || containsUnknownNumbers(generated, facts)) {
            return buildFallbackFeedback(facts);
        }
        if (generated.summary() != null && generated.summary().length() > 120) {
            return buildFallbackFeedback(facts);
        }
        if (generated.bullets() != null && generated.bullets().size() > 3) {
            return buildFallbackFeedback(facts);
        }
        return generated;
    }

    private List<FeedbackBullet> bullets(InsightFacts facts) {
        List<FeedbackBullet> holdingBullets = holdingBullets(facts);
        if (!holdingBullets.isEmpty()) {
            return holdingBullets;
        }
        List<FeedbackBullet> bullets = new ArrayList<>();
        if (facts.positiveFacts() != null && !facts.positiveFacts().isEmpty()) {
            bullets.add(new FeedbackBullet("STRENGTH", facts.positiveFacts().get(0)));
        }
        if (facts.riskFacts() != null && !facts.riskFacts().isEmpty()) {
            bullets.add(new FeedbackBullet("RISK", facts.riskFacts().get(0)));
        } else if (isConcentrationRisk(facts)) {
            bullets.add(new FeedbackBullet("RISK", "특정 종목이나 섹터 집중을 함께 확인해야 합니다."));
        }
        if (bullets.isEmpty()) {
            bullets.add(new FeedbackBullet("INFO", "수익률, 변동성, 최대 낙폭을 함께 확인해보세요."));
        }
        return bullets.size() > 3 ? bullets.subList(0, 3) : bullets;
    }

    private List<FeedbackBullet> holdingBullets(InsightFacts facts) {
        List<BacktestHolding> holdings = sortedHoldings(facts.holdings());
        if (holdings.isEmpty()) {
            return List.of();
        }
        return holdings.stream()
                .limit(3)
                .map(this::holdingBullet)
                .toList();
    }

    private FeedbackBullet holdingBullet(BacktestHolding holding) {
        BigDecimal weight = holding.weightPercent() != null ? holding.weightPercent() : BigDecimal.ZERO;
        String name = holding.name() != null && !holding.name().isBlank()
                ? holding.name()
                : holding.securityId();
        if (weight.compareTo(BigDecimal.valueOf(40)) >= 0) {
            return new FeedbackBullet(
                    "RISK",
                    name + " " + formatPercent(weight) + ": "
                            + sectorLabel(holding) + " 노출의 핵심 비중입니다. 이 종목 하락 시 전체 ETF 변동성이 커질 수 있습니다."
            );
        }
        if (weight.compareTo(BigDecimal.valueOf(25)) >= 0) {
            return new FeedbackBullet(
                    "INFO",
                    name + " " + formatPercent(weight) + ": "
                            + sectorLabel(holding) + " 흐름을 대표하는 핵심 비중입니다. 실적, 제품 사이클, 업종 뉴스가 ETF 흐름에 크게 반영됩니다."
            );
        }
        return new FeedbackBullet(
                "STRENGTH",
                name + " " + formatPercent(weight) + ": "
                        + sectorLabel(holding) + " 노출을 보조하며 포트폴리오 분산에 기여합니다."
        );
    }

    private String portfolioContextPhrase(InsightFacts facts) {
        List<String> parts = new ArrayList<>();
        if (facts.topHoldingName() != null && !facts.topHoldingName().isBlank()
                && facts.topHoldingWeightPercent() != null) {
            parts.add("최대 비중은 " + facts.topHoldingName() + " "
                    + formatPercent(facts.topHoldingWeightPercent()) + "입니다");
        }
        if (facts.dominantSector() != null && !facts.dominantSector().isBlank()
                && facts.dominantSectorWeightPercent() != null) {
            parts.add(facts.dominantSector() + " 비중은 "
                    + formatPercent(facts.dominantSectorWeightPercent()) + "입니다");
        }
        if (parts.isEmpty()) {
            return "보유 종목별 비중을 함께 확인해주세요.";
        }
        return String.join(", ", parts) + ".";
    }

    private String sectorLabel(BacktestHolding holding) {
        return holding.sector() != null && !holding.sector().isBlank()
                ? holding.sector()
                : "해당 종목";
    }

    private List<BacktestHolding> sortedHoldings(List<BacktestHolding> holdings) {
        if (holdings == null || holdings.isEmpty()) {
            return List.of();
        }
        return holdings.stream()
                .filter(Objects::nonNull)
                .sorted((left, right) -> normalizePercent(right.weightPercent()).compareTo(normalizePercent(left.weightPercent())))
                .toList();
    }

    private boolean containsProhibitedExpression(RuleBasedFeedback feedback) {
        String text = ((feedback.title() != null ? feedback.title() : "") + " "
                + (feedback.summary() != null ? feedback.summary() : "") + " "
                + (feedback.bullets() != null ? feedback.bullets().stream().map(FeedbackBullet::message).reduce("", (a, b) -> a + " " + b) : ""))
                .toLowerCase(Locale.ROOT);
        return PROHIBITED_WORDS.stream().anyMatch(word -> text.contains(word.toLowerCase(Locale.ROOT)));
    }

    private boolean containsUnknownNumbers(RuleBasedFeedback feedback, InsightFacts facts) {
        List<String> allowed = allowedNumberStrings(facts);
        String text = (feedback.summary() != null ? feedback.summary() : "") + " "
                + (feedback.bullets() != null ? feedback.bullets().stream().map(FeedbackBullet::message).reduce("", (a, b) -> a + " " + b) : "");
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        while (matcher.find()) {
            if (!allowed.contains(matcher.group())) {
                return true;
            }
        }
        return false;
    }

    private List<String> allowedNumberStrings(InsightFacts facts) {
        List<String> allowed = new ArrayList<>();
        for (BigDecimal value : percentValues(facts)) {
            allowed.add(formatPercent(value));
            allowed.add(formatPercent(value.abs()));
            allowed.add(formatPercentPoint(value));
            allowed.add(formatPercentPoint(value.abs()));
        }
        if (facts.expectedProfitAmountKrw() != null) {
            BigDecimal won = facts.expectedProfitAmountKrw().setScale(0, RoundingMode.HALF_UP);
            allowed.add(won.toPlainString() + "원");
            allowed.add(won.abs().toPlainString() + "원");
        }
        if (facts.holdings() != null) {
            for (BacktestHolding holding : facts.holdings()) {
                if (holding != null && holding.weightPercent() != null) {
                    allowed.add(formatPercent(holding.weightPercent()));
                }
            }
        }
        return allowed;
    }

    private List<BigDecimal> percentValues(InsightFacts facts) {
        List<BigDecimal> values = new ArrayList<>();
        values.add(facts.totalReturnPercent());
        values.add(facts.volatilityPercent());
        values.add(facts.maxDrawdownPercent());
        values.add(facts.benchmarkReturnPercent());
        values.add(facts.excessReturnPercent());
        values.add(facts.topHoldingWeightPercent());
        values.add(facts.top3WeightPercent());
        values.add(facts.dominantSectorWeightPercent());
        return values.stream().filter(Objects::nonNull).toList();
    }

    private boolean isConcentrationRisk(InsightFacts facts) {
        return (facts.topHoldingWeightPercent() != null && facts.topHoldingWeightPercent().compareTo(BigDecimal.valueOf(40)) >= 0)
                || (facts.dominantSectorWeightPercent() != null && facts.dominantSectorWeightPercent().compareTo(BigDecimal.valueOf(60)) >= 0);
    }

    private String fallbackSector(InsightFacts facts) {
        if (facts.dominantSector() != null && !facts.dominantSector().isBlank()) {
            return facts.dominantSector();
        }
        return "상위 종목";
    }

    private BigDecimal fallbackConcentrationWeight(InsightFacts facts) {
        if (facts.dominantSectorWeightPercent() != null) {
            return facts.dominantSectorWeightPercent();
        }
        return facts.topHoldingWeightPercent();
    }

    private String topHoldingPhrase(InsightFacts facts) {
        if (facts.topHoldingName() == null || facts.topHoldingName().isBlank()
                || facts.topHoldingWeightPercent() == null) {
            return "";
        }
        return "최대 비중은 " + facts.topHoldingName() + " "
                + formatPercent(facts.topHoldingWeightPercent()) + "이고, ";
    }

    private BigDecimal normalizePercent(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String formatPercentPoint(BigDecimal value) {
        return formatDecimal(value) + "%p";
    }

    private String formatPercent(BigDecimal value) {
        if (value == null) {
            return "0.0%";
        }
        return formatDecimal(value) + "%";
    }

    private String formatDecimal(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }
}
