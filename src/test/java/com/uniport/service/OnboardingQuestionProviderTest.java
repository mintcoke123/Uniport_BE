package com.uniport.service;

import com.uniport.dto.OnboardingSurveyQuestionDTO;
import com.uniport.entity.OnboardingSurveyOptionEntity;
import com.uniport.entity.OnboardingSurveyQuestionEntity;
import com.uniport.repository.OnboardingSurveyOptionRepository;
import com.uniport.repository.OnboardingSurveyQuestionRepository;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnboardingQuestionProviderTest {

    @Test
    void getQuestions_returnsFigmaOnboardingSurveyCopy() {
        OnboardingQuestionProvider provider = new OnboardingQuestionProvider();

        List<OnboardingSurveyQuestionDTO> questions = provider.getQuestions();

        assertEquals(6, questions.size());
        assertEquals("1주일 사이 100만원이\n95만원(-5%) 으로 떨어졌어", questions.get(0).getTitle());
        assertEquals("바로 팔고 다시는 안 산다", questions.get(0).getOptions().get(0).getLabel());
        assertEquals("스트레스 받아서 못견디겠어", questions.get(0).getOptions().get(0).getSublabel());

        assertEquals("가격 변동을\n얼마나 자주 확인하는 편이야?", questions.get(1).getTitle());
        assertEquals("가끔 확인하는게 마음 편해요", questions.get(1).getOptions().get(0).getSublabel());

        assertEquals("이 앱에서 투자기간를\n어느 정도 기간 가져가고 싶어?", questions.get(2).getTitle());
        assertEquals("10년 이상 길게도 OK", questions.get(2).getOptions().get(2).getLabel());

        assertEquals("나는 이런 식으로\n투자 결정을 내리고 싶어", questions.get(3).getTitle());
        assertEquals("나의 투자 스타일은?", questions.get(3).getSubtitle());

        assertEquals("투자 해본 경험은\n어느 정도야?", questions.get(4).getTitle());
        assertEquals("가끔 해봄", questions.get(4).getOptions().get(1).getLabel());

        assertEquals("현재 가장 관심 있는\n투자분야가 있다면?", questions.get(5).getTitle());
        assertEquals("관심 있는 키워드를 모두 선택해주세요. (최대 2개)", questions.get(5).getSubtitle());
        assertEquals(
                List.of("AI 반도체", "로봇", "방산", "자율주행", "양자컴퓨터", "2차전지", "전력기기", "바이오", "원전", "우주/로켓"),
                questions.get(5).getOptions().stream()
                        .map(option -> option.getLabel())
                        .toList()
        );
    }

    @Test
    void getQuestions_readsActiveQuestionsAndOptionsFromRepositories() {
        OnboardingSurveyQuestionRepository questionRepository = mock(OnboardingSurveyQuestionRepository.class);
        OnboardingSurveyOptionRepository optionRepository = mock(OnboardingSurveyOptionRepository.class);
        OnboardingQuestionProvider provider = new OnboardingQuestionProvider(questionRepository, optionRepository);

        OnboardingSurveyQuestionEntity question = OnboardingSurveyQuestionEntity.builder()
                .id(99L)
                .questionOrder(1)
                .type("SINGLE_SELECT")
                .title("DB 질문")
                .subtitle("DB 부제")
                .minSelection(1)
                .maxSelection(1)
                .active(Boolean.TRUE)
                .build();
        OnboardingSurveyOptionEntity option = OnboardingSurveyOptionEntity.builder()
                .id(101L)
                .question(question)
                .optionOrder(1)
                .label("DB 선택지")
                .sublabel("DB 설명")
                .active(Boolean.TRUE)
                .build();

        when(questionRepository.findByActiveTrueOrderByQuestionOrderAsc()).thenReturn(List.of(question));
        when(optionRepository.findActiveByQuestionIds(List.of(99L))).thenReturn(List.of(option));

        List<OnboardingSurveyQuestionDTO> questions = provider.getQuestions();

        assertEquals(1, questions.size());
        assertEquals(99L, questions.getFirst().getId());
        assertEquals("DB 질문", questions.getFirst().getTitle());
        assertEquals("DB 선택지", questions.getFirst().getOptions().getFirst().getLabel());
        verify(questionRepository).findByActiveTrueOrderByQuestionOrderAsc();
        verify(optionRepository).findActiveByQuestionIds(List.of(99L));
    }

    @Test
    void getQuestions_throwsWhenDatabaseQuestionsAreMissing() {
        OnboardingSurveyQuestionRepository questionRepository = mock(OnboardingSurveyQuestionRepository.class);
        OnboardingSurveyOptionRepository optionRepository = mock(OnboardingSurveyOptionRepository.class);
        OnboardingQuestionProvider provider = new OnboardingQuestionProvider(questionRepository, optionRepository);

        when(questionRepository.findByActiveTrueOrderByQuestionOrderAsc()).thenReturn(Collections.emptyList());

        IllegalStateException exception = assertThrows(IllegalStateException.class, provider::getQuestions);

        assertEquals("Onboarding survey questions are not seeded", exception.getMessage());
    }
}
