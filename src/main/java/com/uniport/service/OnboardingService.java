package com.uniport.service;

import com.uniport.dto.OnboardingCompleteResponseDTO;
import com.uniport.dto.OnboardingNicknameUpdateRequestDTO;
import com.uniport.dto.OnboardingNicknameUpdateResponseDTO;
import com.uniport.dto.OnboardingSurveyAnswerDTO;
import com.uniport.dto.OnboardingSurveyFlowResponseDTO;
import com.uniport.dto.OnboardingSurveyQuestionDTO;
import com.uniport.dto.OnboardingSurveyResultDTO;
import com.uniport.dto.OnboardingSurveySubmitRequestDTO;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OnboardingService {

    private final OnboardingQuestionProvider onboardingQuestionProvider;
    private final OnboardingResultProvider onboardingResultProvider;
    private final UserRepository userRepository;

    public OnboardingService(OnboardingQuestionProvider onboardingQuestionProvider,
                             OnboardingResultProvider onboardingResultProvider,
                             UserRepository userRepository) {
        this.onboardingQuestionProvider = onboardingQuestionProvider;
        this.onboardingResultProvider = onboardingResultProvider;
        this.userRepository = userRepository;
    }

    public OnboardingSurveyFlowResponseDTO getSurveyFlow(User user) {
        if (user == null) {
            throw new ApiException("Authenticated user is required", HttpStatus.UNAUTHORIZED);
        }

        OnboardingSurveyResultDTO result = hasResult(user)
                ? onboardingResultProvider.getByType(user.getInvestmentProfileResult())
                : null;

        return OnboardingSurveyFlowResponseDTO.builder()
                .nickname(user.getNickname())
                .nicknameRequired(user.getNickname() == null || user.getNickname().isBlank() || user.getNickname().startsWith("user_"))
                .hasResult(result != null)
                .questions(onboardingQuestionProvider.getQuestions())
                .result(result)
                .build();
    }

    @Transactional
    public OnboardingNicknameUpdateResponseDTO updateNickname(User user, OnboardingNicknameUpdateRequestDTO request) {
        if (user == null) {
            throw new ApiException("Authenticated user is required", HttpStatus.UNAUTHORIZED);
        }
        if (request == null || request.getNickname() == null || request.getNickname().isBlank()) {
            throw new ApiException("nickname is required", HttpStatus.BAD_REQUEST);
        }

        String nickname = request.getNickname().trim();
        if (nickname.length() > 10) {
            throw new ApiException("nickname must be 10 characters or fewer", HttpStatus.BAD_REQUEST);
        }
        userRepository.findByNickname(nickname)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    throw new ApiException("이미 사용 중인 닉네임입니다.", HttpStatus.CONFLICT);
                });

        user.setNickname(nickname);
        userRepository.save(user);
        return OnboardingNicknameUpdateResponseDTO.builder()
                .nickname(nickname)
                .updated(true)
                .build();
    }

    @Transactional
    public OnboardingSurveyResultDTO submitSurvey(User user, OnboardingSurveySubmitRequestDTO request) {
        if (user == null) {
            throw new ApiException("Authenticated user is required", HttpStatus.UNAUTHORIZED);
        }
        if (request == null || request.getAnswers() == null || request.getAnswers().isEmpty()) {
            throw new ApiException("answers is required", HttpStatus.BAD_REQUEST);
        }
        if (hasResult(user)) {
            return onboardingResultProvider.getByType(user.getInvestmentProfileResult());
        }

        validateAnswers(request.getAnswers());

        int singleQuestionScore = request.getAnswers().stream()
                .filter(answer -> answer.getQuestionId() != null && answer.getQuestionId() <= 5)
                .mapToInt(answer -> onboardingQuestionProvider.getOptionScore(answer.getOptionIds().get(0)))
                .sum();
        double averageScore = singleQuestionScore / 5.0;

        String resultType = averageScore <= 1.6
                ? "조심스러운 거북이형"
                : averageScore <= 2.4 ? "균형잡힌 판다형" : "기회를 찾는 여우형";

        user.setInvestmentProfileResult(resultType);
        userRepository.save(user);
        return onboardingResultProvider.getByType(resultType);
    }

    public OnboardingSurveyResultDTO getMyResult(User user) {
        if (user == null) {
            throw new ApiException("Authenticated user is required", HttpStatus.UNAUTHORIZED);
        }
        if (!hasResult(user)) {
            throw new ApiException("온보딩 결과가 없습니다.", HttpStatus.NOT_FOUND);
        }
        return onboardingResultProvider.getByType(user.getInvestmentProfileResult());
    }

    public OnboardingCompleteResponseDTO complete(User user) {
        if (user == null) {
            throw new ApiException("Authenticated user is required", HttpStatus.UNAUTHORIZED);
        }
        return OnboardingCompleteResponseDTO.builder()
                .completed(true)
                .noteCreated(true)
                .message("첫 투자 노트가 생성되었어요")
                .nextActionLabel("30일 투자 공부 하러가기")
                .build();
    }

    private void validateAnswers(List<OnboardingSurveyAnswerDTO> answers) {
        Set<Long> seenQuestionIds = new HashSet<>();
        Set<Long> requiredQuestionIds = onboardingQuestionProvider.getRequiredQuestionIds();

        for (OnboardingSurveyAnswerDTO answer : answers) {
            if (answer == null || answer.getQuestionId() == null || answer.getOptionIds() == null || answer.getOptionIds().isEmpty()) {
                throw new ApiException("questionId and optionIds are required", HttpStatus.BAD_REQUEST);
            }
            if (!seenQuestionIds.add(answer.getQuestionId())) {
                throw new ApiException("Duplicate questionId is not allowed", HttpStatus.BAD_REQUEST);
            }

            OnboardingSurveyQuestionDTO question = onboardingQuestionProvider.getQuestion(answer.getQuestionId());
            if (question == null) {
                throw new ApiException("Question not found: " + answer.getQuestionId(), HttpStatus.NOT_FOUND);
            }

            int minSelection = question.getMinSelection() != null ? question.getMinSelection() : 1;
            int maxSelection = question.getMaxSelection() != null ? question.getMaxSelection() : 1;
            if (answer.getOptionIds().size() < minSelection || answer.getOptionIds().size() > maxSelection) {
                throw new ApiException("Invalid number of options for question: " + answer.getQuestionId(), HttpStatus.BAD_REQUEST);
            }

            Set<Long> validOptionIds = question.getOptions().stream()
                    .map(option -> option.getId())
                    .collect(Collectors.toSet());
            for (Long optionId : answer.getOptionIds()) {
                if (!validOptionIds.contains(optionId)) {
                    throw new ApiException("Option not found for question: " + answer.getQuestionId(), HttpStatus.NOT_FOUND);
                }
            }
        }

        if (!seenQuestionIds.equals(requiredQuestionIds)) {
            throw new ApiException("All survey questions must be answered exactly once", HttpStatus.BAD_REQUEST);
        }
    }

    private boolean hasResult(User user) {
        return user.getInvestmentProfileResult() != null && !user.getInvestmentProfileResult().isBlank();
    }
}
