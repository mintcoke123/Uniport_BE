package com.uniport.service;

import com.uniport.service.backtest.InsightFacts;
import com.uniport.service.backtest.OpenAiFeedbackClient;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiFeedbackClientTest {

    @Test
    void generate_returnsEmptyWhenApiKeyIsMissing() {
        OpenAiFeedbackClient client = new OpenAiFeedbackClient(
                new RestTemplate(),
                "",
                "https://api.openai.com",
                "gpt-4.1-mini",
                false
        );

        assertEquals(Optional.empty(), client.generate(baseFacts()));
    }

    private InsightFacts baseFacts() {
        return InsightFacts.builder()
                .portfolioLabel("테스트 ETF")
                .periodLabel("1년")
                .principalAmountKrw(BigDecimal.valueOf(100_000_000))
                .totalReturnPercent(BigDecimal.valueOf(8.4))
                .expectedProfitAmountKrw(BigDecimal.valueOf(8_400_000))
                .volatilityPercent(BigDecimal.valueOf(14.2))
                .maxDrawdownPercent(BigDecimal.valueOf(-8.5))
                .benchmarkName("S&P 500")
                .benchmarkReturnPercent(BigDecimal.valueOf(7.1))
                .excessReturnPercent(BigDecimal.valueOf(1.3))
                .riskGrade("LOW")
                .riskGradeLabel("낮음")
                .topHoldingName("Apple")
                .topHoldingWeightPercent(BigDecimal.valueOf(30.0))
                .top3WeightPercent(BigDecimal.valueOf(70.0))
                .positiveFacts(List.of("벤치마크 대비 소폭 우위를 보였습니다."))
                .riskFacts(List.of())
                .disclaimer("과거 데이터 기반 백테스트이며 미래 수익을 보장하지 않습니다.")
                .build();
    }
}
