package com.uniport.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EducationGlossaryClassifierTest {

    @Test
    void classifyReturnsBeginnerTermsWithExactTextOffsets() {
        String text = "보통주는 의결권이 있고 우선주는 배당을 먼저 받을 수 있어요.";

        List<Map<String, Object>> terms = EducationGlossaryClassifier.classify(text);

        assertFalse(terms.isEmpty());
        assertTrue(terms.stream().anyMatch(term ->
                "보통주".equals(term.get("term"))
                        && Integer.valueOf(text.indexOf("보통주")).equals(term.get("start"))
                        && Integer.valueOf(text.indexOf("보통주") + "보통주".length()).equals(term.get("end"))
                        && ((String) term.get("example")).contains("친구들과 카페")));
        assertTrue(terms.stream().anyMatch(term -> "의결권".equals(term.get("term"))));
        assertTrue(terms.stream().anyMatch(term -> "우선주".equals(term.get("term"))));
        terms.forEach(term -> assertEquals(
                text.substring((Integer) term.get("start"), (Integer) term.get("end")),
                term.get("term")));
    }

    @Test
    void classifyReturnsPrioritizedAdditionalBeginnerTerms() {
        String text = "주가 추세와 수급을 보고 매수와 매도를 판단해요. 공시와 영업이익률도 리스크를 줄이는 데 중요해요.";

        List<Map<String, Object>> terms = EducationGlossaryClassifier.classify(text);

        assertTrue(terms.stream().anyMatch(term ->
                "주가".equals(term.get("term"))
                        && ((String) term.get("example")).contains("사과 한 개 가격")));
        assertTrue(terms.stream().anyMatch(term -> "추세".equals(term.get("term"))));
        assertTrue(terms.stream().anyMatch(term -> "수급".equals(term.get("term"))));
        assertTrue(terms.stream().anyMatch(term -> "매수".equals(term.get("term"))));
        assertTrue(terms.stream().anyMatch(term -> "매도".equals(term.get("term"))));
        assertTrue(terms.stream().anyMatch(term -> "공시".equals(term.get("term"))));
        assertTrue(terms.stream().anyMatch(term -> "영업이익률".equals(term.get("term"))));
        assertTrue(terms.stream().anyMatch(term -> "리스크".equals(term.get("term"))));
        assertFalse(terms.stream().anyMatch(term ->
                "영업이익".equals(term.get("term"))
                        && Integer.valueOf(text.indexOf("영업이익")).equals(term.get("start"))));
    }
}
