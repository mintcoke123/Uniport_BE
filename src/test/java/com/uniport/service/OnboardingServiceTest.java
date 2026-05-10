package com.uniport.service;

import com.uniport.dto.OnboardingSurveyAnswerDTO;
import com.uniport.dto.OnboardingSurveyResultDTO;
import com.uniport.dto.OnboardingSurveySubmitRequestDTO;
import com.uniport.entity.LearningUserStateEntity;
import com.uniport.entity.User;
import com.uniport.repository.LearningUserStateRepository;
import com.uniport.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LearningUserStateRepository learningUserStateRepository;

    @Test
    void submitSurvey_persistsCharacterLevelAndTwoSectorsForEducationRoadmap() {
        OnboardingService onboardingService = new OnboardingService(
                new OnboardingQuestionProvider(),
                new OnboardingResultProvider(),
                userRepository,
                learningUserStateRepository);

        User user = User.builder()
                .id(1L)
                .studentId("20240001")
                .nickname("tester")
                .build();

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(learningUserStateRepository.findById(1L)).thenReturn(Optional.empty());
        when(learningUserStateRepository.save(any(LearningUserStateEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingSurveySubmitRequestDTO request = new OnboardingSurveySubmitRequestDTO(List.of(
                new OnboardingSurveyAnswerDTO(1L, List.of(1L)),
                new OnboardingSurveyAnswerDTO(2L, List.of(6L)),
                new OnboardingSurveyAnswerDTO(3L, List.of(7L)),
                new OnboardingSurveyAnswerDTO(4L, List.of(10L)),
                new OnboardingSurveyAnswerDTO(5L, List.of(13L)),
                new OnboardingSurveyAnswerDTO(6L, List.of(16L, 24L))
        ));

        OnboardingSurveyResultDTO result = onboardingService.submitSurvey(user, request);

        assertEquals("조심스러운 거북이", result.getCharacterName());
        assertEquals("입문", result.getInvestmentLevel());
        assertEquals("AI 반도체, 양자컴퓨터", result.getInterestSector());
        assertEquals("조심스러운 거북이", user.getInvestmentProfileResult());
        assertEquals("입문", user.getInvestmentLevel());
        assertEquals("AI 반도체, 양자컴퓨터", user.getInterestSector());
        ArgumentCaptor<LearningUserStateEntity> stateCaptor = ArgumentCaptor.forClass(LearningUserStateEntity.class);
        verify(learningUserStateRepository).save(stateCaptor.capture());
        LearningUserStateEntity savedState = stateCaptor.getValue();
        assertEquals("{\"intro\":1}", savedState.getEducationCurrentDayJson());
        assertTrue(savedState.getEducationSectorSelectionsJson().contains("\"intro\":[\"ai_semiconductor\",\"quantum_computer\"]"));
    }
}
