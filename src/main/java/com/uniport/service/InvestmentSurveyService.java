package com.uniport.service;

import com.uniport.dto.InvestmentSurveyQuestionDTO;
import com.uniport.dto.InvestmentSurveyQuestionsResponseDTO;
import com.uniport.dto.SurveyAnswerItemDTO;
import com.uniport.dto.SurveyOnboardingRequestDTO;
import com.uniport.dto.SurveyOnboardingResponseDTO;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class InvestmentSurveyService {

    private final InvestmentSurveyQuestionProvider investmentSurveyQuestionProvider;
    private final InvestmentSurveyResultProvider investmentSurveyResultProvider;
    private final UserRepository userRepository;

    public InvestmentSurveyService(InvestmentSurveyQuestionProvider investmentSurveyQuestionProvider,
                                   InvestmentSurveyResultProvider investmentSurveyResultProvider,
                                   UserRepository userRepository) {
        this.investmentSurveyQuestionProvider = investmentSurveyQuestionProvider;
        this.investmentSurveyResultProvider = investmentSurveyResultProvider;
        this.userRepository = userRepository;
    }

    public InvestmentSurveyQuestionsResponseDTO getQuestions(User user) {
        if (user == null) {
            throw new ApiException("Authenticated user is required", HttpStatus.UNAUTHORIZED);
        }

        if (user.getInvestmentProfileResult() != null && !user.getInvestmentProfileResult().isBlank()) {
            return InvestmentSurveyQuestionsResponseDTO.builder()
                    .questions(List.of())
                    .hasResult(true)
                    .investmentProfileResult(user.getInvestmentProfileResult())
                    .message("이미 투자 성향 결과가 있습니다.")
                    .build();
        }

        List<InvestmentSurveyQuestionDTO> questions = investmentSurveyQuestionProvider.getQuestions();
        if (questions == null || questions.isEmpty()) {
            throw new ApiException("Investment survey questions not found", HttpStatus.NOT_FOUND);
        }

        return InvestmentSurveyQuestionsResponseDTO.builder()
                .questions(questions)
                .hasResult(false)
                .investmentProfileResult(null)
                .message("투자 성향 설문 질문 조회 성공")
                .build();
    }

    public SurveyOnboardingResponseDTO submitOnboarding(User user, SurveyOnboardingRequestDTO request) {
        if (user == null) {
            throw new ApiException("Authenticated user is required", HttpStatus.UNAUTHORIZED);
        }
        if (request == null || request.getAnswers() == null || request.getAnswers().isEmpty()) {
            throw new ApiException("answers is required", HttpStatus.BAD_REQUEST);
        }
        if (user.getInvestmentProfileResult() != null && !user.getInvestmentProfileResult().isBlank()) {
            return investmentSurveyResultProvider.getByType(user.getInvestmentProfileResult());
        }

        validateAnswers(request.getAnswers());

        int totalScore = request.getAnswers().stream()
                .mapToInt(answer -> investmentSurveyQuestionProvider.getOptionScore(answer.getOptionId()))
                .sum();
        double averageScore = (double) totalScore / request.getAnswers().size();
        String resultType = averageScore <= 1.5
                ? "안정 중시형"
                : averageScore <= 2.5 ? "균형 잡힌 판단형" : "기회 포착형";

        user.setInvestmentProfileResult(resultType);
        userRepository.save(user);
        return investmentSurveyResultProvider.getByType(resultType);
    }

    private void validateAnswers(List<SurveyAnswerItemDTO> answers) {
        Set<Long> seenQuestionIds = new HashSet<>();
        Set<Long> requiredQuestionIds = investmentSurveyQuestionProvider.getRequiredQuestionIds();

        for (SurveyAnswerItemDTO answer : answers) {
            if (answer == null || answer.getQuestionId() == null || answer.getOptionId() == null) {
                throw new ApiException("questionId and optionId are required", HttpStatus.BAD_REQUEST);
            }
            if (!seenQuestionIds.add(answer.getQuestionId())) {
                throw new ApiException("Duplicate questionId is not allowed", HttpStatus.BAD_REQUEST);
            }
            if (!investmentSurveyQuestionProvider.hasQuestion(answer.getQuestionId())) {
                throw new ApiException("Question not found: " + answer.getQuestionId(), HttpStatus.NOT_FOUND);
            }
            if (!investmentSurveyQuestionProvider.hasOption(answer.getQuestionId(), answer.getOptionId())) {
                throw new ApiException("Option not found for question: " + answer.getQuestionId(), HttpStatus.NOT_FOUND);
            }
        }

        if (!seenQuestionIds.equals(requiredQuestionIds)) {
            throw new ApiException("All survey questions must be answered exactly once", HttpStatus.BAD_REQUEST);
        }
    }
}