package com.uniport.service;

import com.uniport.service.backtest.EtfAiFeedbackService;
import com.uniport.service.backtest.EtfNewsExposure;
import com.uniport.service.backtest.EtfRebalanceCandidate;
import com.uniport.service.backtest.FeedbackBullet;
import com.uniport.service.backtest.HoldingNewsExposure;
import com.uniport.service.backtest.InsightFacts;
import com.uniport.service.backtest.LlmFeedbackClient;
import com.uniport.service.backtest.RuleBasedFeedback;
import com.uniport.service.backtest.BacktestHolding;
import com.uniport.service.backtest.BacktestResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EtfAiFeedbackServiceTest {

    private final EtfAiFeedbackService service = new EtfAiFeedbackService();

    @Test
    void buildFeedback_includesConcentrationRiskWhenTopHoldingOrSectorIsHigh() {
        InsightFacts facts = baseFacts()
                .topHoldingWeightPercent(BigDecimal.valueOf(45.0))
                .dominantSector("기술")
                .dominantSectorWeightPercent(BigDecimal.valueOf(70.0))
                .build();

        RuleBasedFeedback feedback = service.buildFallbackFeedback(facts);

        assertTrue(feedback.summary().contains("기술"));
        assertTrue(feedback.summary().contains("70.0%"));
        assertTrue(feedback.bullets().stream().anyMatch(bullet -> bullet.message().contains("집중")));
    }

    @Test
    void buildFeedback_usesTopHoldingWeightWhenSectorIsMissing() {
        InsightFacts facts = baseFacts()
                .topHoldingWeightPercent(BigDecimal.valueOf(45.0))
                .dominantSector(null)
                .dominantSectorWeightPercent(null)
                .build();

        RuleBasedFeedback feedback = service.buildFallbackFeedback(facts);

        assertTrue(feedback.summary().contains("상위 종목"));
        assertTrue(feedback.summary().contains("45.0%"));
    }

    @Test
    void buildFeedback_prioritizesTopHoldingSpecificBullets() {
        InsightFacts facts = baseFacts()
                .holdings(List.of(
                        new BacktestHolding("US_NVDA", "NVIDIA Corp.", BigDecimal.valueOf(45.0), "반도체"),
                        new BacktestHolding("US_AAPL", "Apple Inc.", BigDecimal.valueOf(30.0), "빅테크"),
                        new BacktestHolding("US_MSFT", "Microsoft Corp.", BigDecimal.valueOf(25.0), "소프트웨어")
                ))
                .topHoldingName("NVIDIA Corp.")
                .topHoldingWeightPercent(BigDecimal.valueOf(45.0))
                .top3WeightPercent(BigDecimal.valueOf(100.0))
                .dominantSector("테크")
                .dominantSectorWeightPercent(BigDecimal.valueOf(100.0))
                .build();

        RuleBasedFeedback feedback = service.buildFallbackFeedback(facts);

        assertTrue(feedback.summary().contains("NVIDIA Corp."));
        assertEquals(3, feedback.bullets().size());
        assertEquals("RISK", feedback.bullets().get(0).type());
        assertTrue(feedback.bullets().get(0).message().contains("NVIDIA Corp. 45.0%"));
        assertTrue(feedback.bullets().get(1).message().contains("Apple Inc. 30.0%"));
        assertTrue(feedback.bullets().get(2).message().contains("Microsoft Corp. 25.0%"));
    }

    @Test
    void buildFeedback_includesPortfolioContextWhenRiskIsBalanced() {
        InsightFacts chipFacts = baseFacts()
                .holdings(List.of(
                        new BacktestHolding("US_NVDA", "NVIDIA Corp.", BigDecimal.valueOf(20.0), "반도체"),
                        new BacktestHolding("US_AMD", "AMD", BigDecimal.valueOf(20.0), "반도체")
                ))
                .topHoldingName("NVIDIA Corp.")
                .topHoldingWeightPercent(BigDecimal.valueOf(20.0))
                .top3WeightPercent(BigDecimal.valueOf(40.0))
                .dominantSector("반도체")
                .dominantSectorWeightPercent(BigDecimal.valueOf(40.0))
                .build();
        InsightFacts mobilityFacts = baseFacts()
                .holdings(List.of(
                        new BacktestHolding("US_TSLA", "Tesla", BigDecimal.valueOf(20.0), "전기차"),
                        new BacktestHolding("US_GM", "GM", BigDecimal.valueOf(20.0), "전기차")
                ))
                .topHoldingName("Tesla")
                .topHoldingWeightPercent(BigDecimal.valueOf(20.0))
                .top3WeightPercent(BigDecimal.valueOf(40.0))
                .dominantSector("전기차")
                .dominantSectorWeightPercent(BigDecimal.valueOf(40.0))
                .build();

        RuleBasedFeedback chipFeedback = service.buildFallbackFeedback(chipFacts);
        RuleBasedFeedback mobilityFeedback = service.buildFallbackFeedback(mobilityFacts);

        assertTrue(chipFeedback.summary().contains("NVIDIA Corp."));
        assertTrue(chipFeedback.summary().contains("반도체"));
        assertTrue(mobilityFeedback.summary().contains("Tesla"));
        assertTrue(mobilityFeedback.summary().contains("전기차"));
        assertTrue(!chipFeedback.summary().equals(mobilityFeedback.summary()));
    }

    @Test
    void buildFeedback_variesHoldingCommentsBySectorContext() {
        InsightFacts facts = baseFacts()
                .holdings(List.of(
                        new BacktestHolding("US_TSLA", "Tesla", BigDecimal.valueOf(20.0), "전기차"),
                        new BacktestHolding("US_JPM", "JPMorgan", BigDecimal.valueOf(20.0), "금융"),
                        new BacktestHolding("US_PFE", "Pfizer", BigDecimal.valueOf(20.0), "헬스케어")
                ))
                .topHoldingWeightPercent(BigDecimal.valueOf(20.0))
                .top3WeightPercent(BigDecimal.valueOf(60.0))
                .dominantSector("혼합")
                .dominantSectorWeightPercent(BigDecimal.valueOf(60.0))
                .build();

        RuleBasedFeedback feedback = service.buildFallbackFeedback(facts);

        assertTrue(feedback.bullets().get(0).message().contains("전기차"));
        assertTrue(feedback.bullets().get(1).message().contains("금융"));
        assertTrue(feedback.bullets().get(2).message().contains("헬스케어"));
    }

    @Test
    void buildInsightFacts_includesWeightedNewsExposureFacts() {
        BacktestResult result = backtestResult();
        EtfNewsExposure newsExposure = new EtfNewsExposure(
                BigDecimal.valueOf(40.0).setScale(1),
                BigDecimal.valueOf(60.0).setScale(1),
                2,
                List.of(new HoldingNewsExposure(
                        "US_TSLA",
                        "Tesla",
                        BigDecimal.valueOf(60.0).setScale(1),
                        "NEGATIVE",
                        0.80,
                        1,
                        "수요 둔화 우려가 커지고 있어요.",
                        "Tesla 수요 둔화 우려"
                )),
                List.of(new EtfRebalanceCandidate(
                        "US_TSLA",
                        "Tesla",
                        BigDecimal.valueOf(60.0).setScale(1),
                        "NEGATIVE",
                        "REDUCE_WATCH",
                        "Tesla는 악재 뉴스가 우세해 비중 축소 점검 후보입니다."
                )),
                List.of("Tesla 60.0%에 악재 뉴스가 우세해요: 수요 둔화 우려가 커지고 있어요.")
        );

        InsightFacts facts = service.buildInsightFacts(
                "전기차 ETF",
                "1년",
                "S&P 500",
                result,
                List.of(new BacktestHolding("US_TSLA", "Tesla", BigDecimal.valueOf(60.0), "전기차")),
                newsExposure
        );

        assertEquals(newsExposure, facts.newsExposure());
        assertTrue(facts.positiveFacts().stream().anyMatch(fact -> fact.contains("호재 뉴스 노출 비중은 40.0%")));
        assertTrue(facts.riskFacts().stream().anyMatch(fact -> fact.contains("악재 뉴스 노출 비중은 60.0%")));
        assertTrue(facts.riskFacts().stream().anyMatch(fact -> fact.contains("Tesla 60.0%")));
        assertTrue(facts.riskFacts().stream().anyMatch(fact -> fact.contains("비중 축소 점검 후보")));
    }

    @Test
    void validateLlmFeedback_rejectsProhibitedInvestmentGuaranteeWords() {
        InsightFacts facts = baseFacts().build();
        RuleBasedFeedback unsafe = new RuleBasedFeedback(
                "AI 리스크 진단",
                "이 포트폴리오는 확실히 수익을 보장합니다.",
                java.util.List.of(),
                "BALANCED",
                facts.disclaimer(),
                false
        );

        RuleBasedFeedback validated = service.validateOrFallback(unsafe, facts);

        assertEquals(true, validated.usedFallback());
        assertTrue(!validated.summary().contains("확실히"));
        assertTrue(!validated.summary().contains("수익을 보장"));
    }

    @Test
    void validateLlmFeedback_rejectsUnknownKrwNumbers() {
        InsightFacts facts = baseFacts().build();
        RuleBasedFeedback unsafe = new RuleBasedFeedback(
                "AI 리스크 진단",
                "예상 수익금은 999만원으로 볼 수 있어요.",
                java.util.List.of(),
                "BALANCED",
                facts.disclaimer(),
                false
        );

        RuleBasedFeedback validated = service.validateOrFallback(unsafe, facts);

        assertEquals(true, validated.usedFallback());
        assertTrue(!validated.summary().contains("999만원"));
    }

    @Test
    void buildFeedback_usesValidLlmOutput() {
        EtfAiFeedbackService llmService = new EtfAiFeedbackService(new LlmFeedbackClient() {
            @Override
            public Optional<RuleBasedFeedback> generate(InsightFacts facts) {
                return Optional.of(new RuleBasedFeedback(
                        "AI 리스크 진단",
                        "백테스트 기준 수익률은 14.7%였고 최대 낙폭은 -8.5%였습니다.",
                        java.util.List.of(new FeedbackBullet("STRENGTH", "벤치마크 대비 소폭 우위를 보였습니다.")),
                        "BALANCED",
                        facts.disclaimer(),
                        false
                ));
            }

            @Override
            public String modelName() {
                return "test-model";
            }

            @Override
            public String promptVersion() {
                return "test-prompt";
            }
        });

        RuleBasedFeedback feedback = llmService.buildFeedback(baseFacts().build());

        assertEquals(false, feedback.usedFallback());
        assertEquals("test-model", llmService.modelName());
        assertEquals("test-prompt", llmService.promptVersion());
    }

    private InsightFacts.InsightFactsBuilder baseFacts() {
        return InsightFacts.builder()
                .portfolioLabel("AI 테크 포트폴리오")
                .periodLabel("1년")
                .principalAmountKrw(BigDecimal.valueOf(100_000_000))
                .totalReturnPercent(BigDecimal.valueOf(14.7))
                .expectedProfitAmountKrw(BigDecimal.valueOf(14_700_000))
                .volatilityPercent(BigDecimal.valueOf(14.2))
                .maxDrawdownPercent(BigDecimal.valueOf(-8.5))
                .benchmarkName("S&P 500")
                .benchmarkReturnPercent(BigDecimal.valueOf(11.5))
                .excessReturnPercent(BigDecimal.valueOf(3.2))
                .riskGrade("LOW")
                .riskGradeLabel("낮음")
                .topHoldingName("엔비디아")
                .topHoldingWeightPercent(BigDecimal.valueOf(35.0))
                .top3WeightPercent(BigDecimal.valueOf(80.0))
                .dominantSector("기술")
                .dominantSectorWeightPercent(BigDecimal.valueOf(72.0))
                .disclaimer("과거 데이터 기반 백테스트이며 미래 수익을 보장하지 않습니다.");
    }

    private BacktestResult backtestResult() {
        return new BacktestResult(
                BigDecimal.valueOf(100_000_000),
                BigDecimal.valueOf(114_700_000),
                BigDecimal.valueOf(14_700_000),
                BigDecimal.valueOf(14.7),
                BigDecimal.valueOf(14.7),
                BigDecimal.valueOf(14.2),
                BigDecimal.valueOf(-8.5),
                BigDecimal.valueOf(11.5),
                BigDecimal.valueOf(3.2),
                BigDecimal.valueOf(0.82),
                BigDecimal.valueOf(1.0),
                "Tesla",
                BigDecimal.valueOf(60.0),
                BigDecimal.valueOf(100.0),
                "전기차",
                BigDecimal.valueOf(60.0),
                20,
                "LOW",
                "낮음",
                252,
                List.of()
        );
    }
}
