package com.uniport.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RawNewsNormalizerTest {

    @Test
    void normalizeTitle_canonicalizesMarketNewsTokens() {
        RawNewsNormalizer normalizer = new RawNewsNormalizer();

        List<String> tokens = normalizer.titleTokens("[마감시황] 外人 6兆 순매도에 코스피 ↓");

        assertEquals(List.of("외국인", "6조", "매도", "코스피", "하락"), tokens);
    }

    @Test
    void canonicalToken_appliesRequiredSingleTokenMappings() {
        RawNewsNormalizer normalizer = new RawNewsNormalizer();

        assertEquals("외국인", normalizer.canonicalToken("외인"));
        assertEquals("외국인", normalizer.canonicalToken("外人"));
        assertEquals("6조", normalizer.canonicalToken("6兆"));
        assertEquals("하락", normalizer.canonicalToken("↓"));
        assertEquals("상승", normalizer.canonicalToken("↑"));
    }

    @Test
    void cleanDisplayText_removesHtmlTagsAndCollapsesWhitespace() {
        RawNewsNormalizer normalizer = new RawNewsNormalizer();

        String result = normalizer.cleanDisplayText("  코스피&nbsp; <b>상승</b>\n\t<em>출발</em>  ");

        assertEquals("코스피 상승 출발", result);
    }
}
