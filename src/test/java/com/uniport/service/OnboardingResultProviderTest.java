package com.uniport.service;

import com.uniport.dto.OnboardingSurveyResultDTO;
import com.uniport.dto.SurveyResultSectionDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OnboardingResultProviderTest {

    private final OnboardingResultProvider onboardingResultProvider = OnboardingResultProviderTestFactory.create();

    @Test
    void classify_returnsOneOfEightCharactersForEveryAxisCombination() {
        for (int risk = 1; risk <= 3; risk++) {
            for (int term = 1; term <= 3; term++) {
                for (int style = 1; style <= 3; style++) {
                    for (int involvement = 1; involvement <= 3; involvement++) {
                        OnboardingSurveyResultDTO result = onboardingResultProvider.classify(
                                risk,
                                term,
                                style,
                                involvement,
                                "입문",
                                "AI 반도체");

                        assertNotNull(result);
                        assertNotNull(result.getCharacterId());
                        assertNotNull(result.getCharacterName());
                    }
                }
            }
        }
    }

    @Test
    void classify_usesRiskTermStyleInvolvementTieBreakOrder() {
        OnboardingSurveyResultDTO result = onboardingResultProvider.classify(
                2,
                1,
                1,
                1,
                "기본",
                "방산");

        assertEquals("균형잡힌 판다형", result.getCharacterName());
    }

    @Test
    void getByCharacterName_returnsFigmaBlueTextHighlightsForStrategyItems() {
        OnboardingSurveyResultDTO result = onboardingResultProvider.getByCharacterName(
                "균형잡힌 판다",
                "기본",
                "방산");

        SurveyResultSectionDTO strategySection = result.getFeatures().stream()
                .filter(section -> "추천 전략".equals(section.getTitle()))
                .findFirst()
                .orElseThrow();

        assertEquals(List.of("코어 70/위성 30"), strategySection.getItems().get(0).getNameHighlights());
        assertEquals(List.of("포트폴리오 점검 기준"), strategySection.getItems().get(3).getNameHighlights());
    }
}
