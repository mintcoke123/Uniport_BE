package com.uniport.service;

import com.uniport.service.backtest.EtfAiFeedbackService;
import com.uniport.service.backtest.FeedbackBullet;
import com.uniport.service.backtest.InsightFacts;
import com.uniport.service.backtest.LlmFeedbackClient;
import com.uniport.service.backtest.RuleBasedFeedback;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
}
