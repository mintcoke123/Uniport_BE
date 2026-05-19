package com.uniport.service;

import java.util.List;
import java.util.Locale;

class KeywordNewsSentimentAnalyzer implements NewsSentimentAnalyzer {

    private static final List<String> NEGATIVE_KEYWORDS = List.of(
            "급락",
            "하락",
            "우려",
            "리스크",
            "차익실현",
            "손실",
            "둔화",
            "악화",
            "매도",
            "약세",
            "내린",
            "밀렸",
            "투매",
            "폭삭",
            "후퇴",
            "부진",
            "쇼크",
            "적자",
            "감소"
    );
    private static final List<String> POSITIVE_KEYWORDS = List.of(
            "상승",
            "반등",
            "회복",
            "개선",
            "기대",
            "서프라이즈",
            "수혜",
            "강세",
            "호조",
            "상향",
            "증가",
            "흑자"
    );

    @Override
    public NewsSentimentAnalysis analyze(NewsSentimentInput input) {
        String text = input.textForAnalysis().toUpperCase(Locale.ROOT);
        int negativeScore = countMatches(text, NEGATIVE_KEYWORDS);
        int positiveScore = countMatches(text, POSITIVE_KEYWORDS);
        if (negativeScore > 0 && negativeScore >= positiveScore) {
            return NewsSentimentAnalysis.negative(
                    confidence(negativeScore, positiveScore),
                    "키워드 기반 부정 신호가 긍정 신호 이상으로 확인되어 악재로 분류했어요."
            );
        }
        return NewsSentimentAnalysis.positive(
                confidence(positiveScore, negativeScore),
                positiveScore > 0
                        ? "키워드 기반 긍정 신호가 부정 신호보다 강해 호재로 분류했어요."
                        : "뚜렷한 부정 신호가 없어 긍정 흐름의 호재로 분류했어요."
        );
    }

    private int countMatches(String text, List<String> keywords) {
        int count = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword.toUpperCase(Locale.ROOT))) {
                count++;
            }
        }
        return count;
    }

    private double confidence(int primaryScore, int secondaryScore) {
        int diff = Math.max(0, primaryScore - secondaryScore);
        return Math.min(0.95, 0.6 + (diff * 0.1));
    }
}
