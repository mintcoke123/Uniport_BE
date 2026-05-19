package com.uniport.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeywordNewsSentimentAnalyzerTest {

    private final KeywordNewsSentimentAnalyzer analyzer = new KeywordNewsSentimentAnalyzer();

    @Test
    void analyze_prefersNegativeWhenPositiveAndNegativeSignalsTie() {
        NewsSentimentAnalysis result = analyzer.analyze(new NewsSentimentInput(
                "naver_market_drop",
                "[마감시황] 코스피, 3% 넘게 하락",
                "코스닥은 상승 출발했으나 이후 지수가 밀렸어요.",
                "",
                "네이버 뉴스"
        ));

        assertEquals(NewsSentimentType.NEGATIVE, result.type());
    }

    @Test
    void analyze_treatsBearishMarketWordsAsNegativeSignals() {
        NewsSentimentAnalysis result = analyzer.analyze(new NewsSentimentInput(
                "naver_market_selloff",
                "코스피 7,200대로 폭삭. 외국인 투매 지속",
                "대형주는 전장보다 내린 가격에 거래를 마쳤고 약세 흐름이 이어졌어요.",
                "",
                "네이버 뉴스"
        ));

        assertEquals(NewsSentimentType.NEGATIVE, result.type());
    }
}
