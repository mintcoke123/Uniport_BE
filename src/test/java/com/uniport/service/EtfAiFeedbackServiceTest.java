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
        assertTrue(feedback.summary().contains("기울어"));
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
        assertTrue(!feedback.summary().contains("45.0%"));
    }

    @Test
    void buildFeedback_prioritizesTopHoldingSpecificReason() {
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
        assertTrue(feedback.bullets().get(0).message().startsWith("핵심 원인:"));
        assertTrue(feedback.bullets().get(0).message().contains("NVIDIA Corp."));
        assertTrue(feedback.bullets().get(0).message().contains("45.0%"));
        assertTrue(feedback.bullets().get(0).message().contains("Apple Inc. 30.0%"));
        assertTrue(feedback.bullets().get(1).message().startsWith("가장 큰 리스크:"));
        assertTrue(feedback.bullets().get(2).message().startsWith("조정 방향:"));
    }

    @Test
    void buildFallbackFeedback_comparesActualHoldingMixWithWeights() {
        InsightFacts facts = baseFacts()
                .holdings(List.of(
                        new BacktestHolding("US_NVDA", "NVIDIA", BigDecimal.valueOf(35.0), "반도체"),
                        new BacktestHolding("US_AAPL", "Apple", BigDecimal.valueOf(25.0), "빅테크"),
                        new BacktestHolding("US_MSFT", "Microsoft", BigDecimal.valueOf(20.0), "소프트웨어"),
                        new BacktestHolding("US_JPM", "JPMorgan", BigDecimal.valueOf(20.0), "금융")
                ))
                .topHoldingName("NVIDIA")
                .topHoldingWeightPercent(BigDecimal.valueOf(35.0))
                .top3WeightPercent(BigDecimal.valueOf(80.0))
                .dominantSector("혼합")
                .dominantSectorWeightPercent(BigDecimal.valueOf(80.0))
                .build();

        RuleBasedFeedback feedback = service.buildFallbackFeedback(facts);

        assertTrue(feedback.summary().contains("NVIDIA 35.0%"));
        assertTrue(feedback.summary().contains("Apple 25.0%"));
        assertTrue(feedback.bullets().get(0).message().contains("Microsoft 20.0%"));
        assertTrue(feedback.bullets().get(1).message().contains("JPMorgan"));
        assertTrue(!feedback.bullets().get(0).message().contains("상위 종목 몇 개"));
    }

    @Test
    void buildFallbackFeedback_givesPortfolioCheckupStyleJudgmentAndRebalanceDirection() {
        InsightFacts facts = baseFacts()
                .holdings(List.of(
                        new BacktestHolding("US_NVDA", "NVIDIA", BigDecimal.valueOf(55.0), "반도체"),
                        new BacktestHolding("US_AMD", "AMD", BigDecimal.valueOf(25.0), "반도체"),
                        new BacktestHolding("US_JPM", "JPMorgan", BigDecimal.valueOf(20.0), "금융")
                ))
                .topHoldingName("NVIDIA")
                .topHoldingWeightPercent(BigDecimal.valueOf(55.0))
                .top3WeightPercent(BigDecimal.valueOf(100.0))
                .dominantSector("반도체")
                .dominantSectorWeightPercent(BigDecimal.valueOf(80.0))
                .build();

        RuleBasedFeedback feedback = service.buildFallbackFeedback(facts);

        assertTrue(feedback.summary().contains("포트폴리오"));
        assertTrue(feedback.summary().contains("방어"));
        assertTrue(feedback.bullets().get(1).message().contains("성장주 편중"));
        assertTrue(feedback.bullets().get(2).message().startsWith("조정 방향:"));
        assertTrue(feedback.bullets().get(2).message().contains("리밸런싱"));
    }

    @Test
    void buildFallbackFeedback_returnsActionableStructuredFeedback() {
        InsightFacts facts = baseFacts()
                .holdings(List.of(
                        new BacktestHolding("US_NVDA", "NVIDIA", BigDecimal.valueOf(70.0), "반도체"),
                        new BacktestHolding("US_AAPL", "Apple", BigDecimal.valueOf(30.0), "빅테크")
                ))
                .topHoldingName("NVIDIA")
                .topHoldingWeightPercent(BigDecimal.valueOf(70.0))
                .top3WeightPercent(BigDecimal.valueOf(100.0))
                .dominantSector("반도체")
                .dominantSectorWeightPercent(BigDecimal.valueOf(70.0))
                .volatilityPercent(BigDecimal.valueOf(22.3))
                .maxDrawdownPercent(BigDecimal.valueOf(-18.4))
                .build();

        RuleBasedFeedback feedback = service.buildFallbackFeedback(facts);

        assertTrue(feedback.summary().startsWith("한 줄 결론:"));
        assertTrue(feedback.summary().contains("공격형"));
        assertEquals(3, feedback.bullets().size());
        assertTrue(feedback.bullets().get(0).message().startsWith("핵심 원인:"));
        assertTrue(feedback.bullets().get(0).message().contains("NVIDIA"));
        assertTrue(feedback.bullets().get(0).message().contains("70.0%"));
        assertTrue(feedback.bullets().get(0).message().contains("Apple 30.0%"));
        assertTrue(feedback.bullets().get(1).message().startsWith("가장 큰 리스크:"));
        assertTrue(feedback.bullets().get(1).message().contains("방어"));
        assertTrue(feedback.bullets().get(2).message().startsWith("조정 방향:"));
        assertTrue(feedback.bullets().get(2).message().contains("AI 칩 수요"));
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
    void buildFeedback_variesCheckpointsBySectorContext() {
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

        assertTrue(feedback.bullets().get(2).message().contains("인도량"));
    }

    @Test
    void buildFallbackFeedback_usesHoldingNamesAndNewsRiskInCheckpoint() {
        EtfNewsExposure newsExposure = new EtfNewsExposure(
                BigDecimal.valueOf(10.0).setScale(1),
                BigDecimal.valueOf(90.0).setScale(1),
                3,
                List.of(new HoldingNewsExposure(
                        "US_NVDA",
                        "NVIDIA",
                        BigDecimal.valueOf(55.0).setScale(1),
                        "NEGATIVE",
                        0.86,
                        2,
                        "데이터센터 수요 둔화 우려가 커지고 있어요.",
                        "NVIDIA 데이터센터 수요 둔화"
                )),
                List.of(),
                List.of("NVIDIA 55.0%에 악재 뉴스가 우세해요: 데이터센터 수요 둔화 우려가 커지고 있어요.")
        );
        InsightFacts facts = baseFacts()
                .holdings(List.of(
                        new BacktestHolding("US_NVDA", "NVIDIA", BigDecimal.valueOf(55.0), "반도체"),
                        new BacktestHolding("US_AMD", "AMD", BigDecimal.valueOf(25.0), "반도체"),
                        new BacktestHolding("US_MSFT", "Microsoft", BigDecimal.valueOf(20.0), "소프트웨어")
                ))
                .topHoldingName("NVIDIA")
                .topHoldingWeightPercent(BigDecimal.valueOf(55.0))
                .top3WeightPercent(BigDecimal.valueOf(100.0))
                .dominantSector("반도체")
                .dominantSectorWeightPercent(BigDecimal.valueOf(80.0))
                .newsExposure(newsExposure)
                .riskFacts(List.of("NVIDIA 55.0%에 악재 뉴스가 우세해요: 데이터센터 수요 둔화 우려가 커지고 있어요."))
                .build();

        RuleBasedFeedback feedback = service.buildFallbackFeedback(facts);

        assertTrue(feedback.bullets().get(2).message().contains("NVIDIA와 AMD"));
        assertTrue(feedback.bullets().get(2).message().contains("데이터센터 수요 둔화"));
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
    void validateLlmFeedback_acceptsKnownPeriodAndHoldingCountContext() {
        InsightFacts facts = baseFacts()
                .holdings(List.of(
                        new BacktestHolding("US_NVDA", "엔비디아", BigDecimal.valueOf(35.0), "기술"),
                        new BacktestHolding("US_AAPL", "Apple", BigDecimal.valueOf(25.0), "빅테크"),
                        new BacktestHolding("US_MSFT", "Microsoft", BigDecimal.valueOf(20.0), "소프트웨어")
                ))
                .build();
        RuleBasedFeedback generated = new RuleBasedFeedback(
                "AI 리스크 진단",
                "1년 기준 수익률은 14.7%였고, 상위 3개 종목 비중이 80.0%라 기술 섹터 집중도를 함께 봐야 해요.",
                java.util.List.of(new FeedbackBullet("RISK", "엔비디아 35.0%가 가장 큰 비중이라 실적 발표와 AI 반도체 수요에 민감합니다.")),
                "CAUTION",
                facts.disclaimer(),
                false
        );

        RuleBasedFeedback validated = service.validateOrFallback(generated, facts);

        assertEquals(false, validated.usedFallback());
        assertEquals(generated.summary(), validated.summary());
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

    @Test
    void buildFeedbackResult_reportsAcceptedLlmStatus() {
        EtfAiFeedbackService llmService = new EtfAiFeedbackService(new LlmFeedbackClient() {
            @Override
            public Optional<RuleBasedFeedback> generate(InsightFacts facts) {
                return Optional.of(new RuleBasedFeedback(
                        "AI 리스크 진단",
                        "한 줄 결론: 입력된 지표 안에서 균형이 좋습니다.",
                        List.of(new FeedbackBullet("INFO", "핵심 원인: 엔비디아 35.0%가 성장축입니다.")),
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

            @Override
            public String lastAttemptStatus() {
                return "success:json_schema";
            }
        });

        EtfAiFeedbackService.FeedbackBuildResult result = llmService.buildFeedbackResult(baseFacts().build());

        assertEquals(false, result.feedback().usedFallback());
        assertEquals("test-model:success:json_schema", result.llmStatus());
        assertEquals("accepted", result.fallbackReason());
    }

    @Test
    void validateLlmFeedback_trimsLongSummaryAndLimitsBulletsInsteadOfFallback() {
        InsightFacts facts = baseFacts().build();
        RuleBasedFeedback generated = new RuleBasedFeedback(
                "AI 리스크 진단",
                "한 줄 결론: " + "균형을 확인해야 합니다. ".repeat(20),
                List.of(
                        new FeedbackBullet("INFO", "핵심 원인: 엔비디아 35.0%가 성장축입니다."),
                        new FeedbackBullet("RISK", "가장 큰 리스크: 기술주 변동성입니다."),
                        new FeedbackBullet("INFO", "조정 방향: 실적 발표를 보세요."),
                        new FeedbackBullet("INFO", "추가 문장은 잘립니다.")
                ),
                "BALANCED",
                facts.disclaimer(),
                false
        );

        RuleBasedFeedback validated = service.validateOrFallback(generated, facts);

        assertEquals(false, validated.usedFallback());
        assertEquals(3, validated.bullets().size());
        assertTrue(validated.summary().length() <= 220);
    }

    @Test
    void buildFeedbackResult_reportsRejectedLlmReason() {
        EtfAiFeedbackService llmService = new EtfAiFeedbackService(new LlmFeedbackClient() {
            @Override
            public Optional<RuleBasedFeedback> generate(InsightFacts facts) {
                return Optional.of(new RuleBasedFeedback(
                        "AI 리스크 진단",
                        "예상 수익금은 999만원으로 볼 수 있어요.",
                        List.of(),
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

            @Override
            public String lastAttemptStatus() {
                return "success:json_object";
            }
        });

        EtfAiFeedbackService.FeedbackBuildResult result = llmService.buildFeedbackResult(baseFacts().build());

        assertEquals(true, result.feedback().usedFallback());
        assertEquals("test-model:success:json_object", result.llmStatus());
        assertEquals("rejected:unknown_number:999만원", result.fallbackReason());
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
