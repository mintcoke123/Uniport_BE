package com.uniport.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.dto.OnboardingCompleteResponseDTO;
import com.uniport.dto.OnboardingNicknameUpdateRequestDTO;
import com.uniport.dto.OnboardingNicknameUpdateResponseDTO;
import com.uniport.dto.OnboardingSurveyAnswerDTO;
import com.uniport.dto.OnboardingSurveyFlowResponseDTO;
import com.uniport.dto.OnboardingSurveyQuestionDTO;
import com.uniport.dto.OnboardingSurveyResultDTO;
import com.uniport.dto.OnboardingSurveySubmitRequestDTO;
import com.uniport.entity.LearningUserStateEntity;
import com.uniport.entity.User;
import com.uniport.entity.UserMyPagePreference;
import com.uniport.exception.ApiException;
import com.uniport.repository.LearningUserStateRepository;
import com.uniport.repository.UserMyPagePreferenceRepository;
import com.uniport.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OnboardingService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String INTRO_COURSE_ID = "intro";
    private static final String ADVANCED_COURSE_ID = "advanced";
    private static final TypeReference<Map<String, Integer>> MAP_STRING_INTEGER_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, List<String>>> MAP_STRING_LIST_STRING_TYPE = new TypeReference<>() {};

    private final OnboardingQuestionProvider onboardingQuestionProvider;
    private final OnboardingResultProvider onboardingResultProvider;
    private final UserRepository userRepository;
    private final UserMyPagePreferenceRepository userMyPagePreferenceRepository;
    private final LearningUserStateRepository learningUserStateRepository;
    private final ProfileImageUrlService profileImageUrlService;

    public OnboardingService(OnboardingQuestionProvider onboardingQuestionProvider,
                             OnboardingResultProvider onboardingResultProvider,
                             UserRepository userRepository,
                             UserMyPagePreferenceRepository userMyPagePreferenceRepository,
                             LearningUserStateRepository learningUserStateRepository,
                             ProfileImageUrlService profileImageUrlService) {
        this.onboardingQuestionProvider = onboardingQuestionProvider;
        this.onboardingResultProvider = onboardingResultProvider;
        this.userRepository = userRepository;
        this.userMyPagePreferenceRepository = userMyPagePreferenceRepository;
        this.learningUserStateRepository = learningUserStateRepository;
        this.profileImageUrlService = profileImageUrlService;
    }

    public OnboardingSurveyFlowResponseDTO getSurveyFlow(User user) {
        if (user == null) {
            throw new ApiException("Authenticated user is required", HttpStatus.UNAUTHORIZED);
        }

        OnboardingSurveyResultDTO result = hasResult(user)
                ? onboardingResultProvider.getByCharacterName(
                user.getInvestmentProfileResult(),
                user.getInvestmentLevel(),
                user.getInterestSector())
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
            OnboardingSurveyResultDTO result = onboardingResultProvider.getByCharacterName(
                    user.getInvestmentProfileResult(),
                    user.getInvestmentLevel(),
                    user.getInterestSector());
            applyCharacterProfile(user, result);
            userRepository.save(user);
            return result;
        }

        Map<Long, OnboardingSurveyAnswerDTO> answersByQuestion = validateAnswers(request.getAnswers());

        int risk = getSingleValue(answersByQuestion, OnboardingQuestionProvider.QUESTION_RISK, onboardingQuestionProvider::getRiskValue);
        int term = getSingleValue(answersByQuestion, OnboardingQuestionProvider.QUESTION_TERM, onboardingQuestionProvider::getTermValue);
        int involvement = getSingleValue(answersByQuestion, OnboardingQuestionProvider.QUESTION_INVOLVEMENT, onboardingQuestionProvider::getInvolvementValue);
        int style = getSingleValue(answersByQuestion, OnboardingQuestionProvider.QUESTION_STYLE, onboardingQuestionProvider::getStyleValue);
        String investmentLevel = onboardingQuestionProvider.getLevelLabel(
                getSingleSelectedOptionId(answersByQuestion.get(OnboardingQuestionProvider.QUESTION_LEVEL)));
        List<Long> sectorOptionIds = getSelectedOptionIds(answersByQuestion.get(OnboardingQuestionProvider.QUESTION_SECTOR));
        String interestSector = sectorOptionIds.stream()
                .map(onboardingQuestionProvider::getSectorLabel)
                .collect(Collectors.joining(", "));
        List<String> educationSectorIds = sectorOptionIds.stream()
                .map(onboardingQuestionProvider::getSectorId)
                .toList();

        OnboardingSurveyResultDTO result = onboardingResultProvider.classify(
                risk,
                term,
                style,
                involvement,
                investmentLevel,
                interestSector);

        user.setInvestmentProfileResult(result.getCharacterName());
        user.setInvestmentLevel(investmentLevel);
        user.setInterestSector(interestSector);
        applyCharacterProfile(user, result);
        userRepository.save(user);
        persistEducationRoadmapSeed(user, investmentLevel, educationSectorIds);

        return result;
    }

    public OnboardingSurveyResultDTO getMyResult(User user) {
        if (user == null) {
            throw new ApiException("Authenticated user is required", HttpStatus.UNAUTHORIZED);
        }
        if (!hasResult(user)) {
            throw new ApiException("온보딩 결과가 없습니다.", HttpStatus.NOT_FOUND);
        }
        return onboardingResultProvider.getByCharacterName(
                user.getInvestmentProfileResult(),
                user.getInvestmentLevel(),
                user.getInterestSector());
    }

    @Transactional
    public OnboardingCompleteResponseDTO complete(User user) {
        if (user == null) {
            throw new ApiException("Authenticated user is required", HttpStatus.UNAUTHORIZED);
        }
        if (hasResult(user)) {
            OnboardingSurveyResultDTO result = onboardingResultProvider.getByCharacterName(
                    user.getInvestmentProfileResult(),
                    user.getInvestmentLevel(),
                    user.getInterestSector());
            applyCharacterProfile(user, result);
            userRepository.save(user);
        }
        return OnboardingCompleteResponseDTO.builder()
                .completed(true)
                .noteCreated(true)
                .message("첫 투자노트가 생성되었어요")
                .nextActionLabel("30일 투자공부하러가기")
                .build();
    }

    private void applyCharacterProfile(User user, OnboardingSurveyResultDTO result) {
        String characterCode = profileImageUrlService.profileOptionCodeForCharacterName(result.getCharacterName());
        UserMyPagePreference preference = userMyPagePreferenceRepository.findById(user.getId())
                .orElseGet(() -> UserMyPagePreference.builder()
                        .userId(user.getId())
                        .pushEnabled(Boolean.TRUE)
                        .build());
        preference.setSelectedCharacterCode(characterCode);
        userMyPagePreferenceRepository.save(preference);
        user.setProfileImageUrl(profileImageUrlService.profileOptionImageUrl(characterCode));
    }

    private Map<Long, OnboardingSurveyAnswerDTO> validateAnswers(List<OnboardingSurveyAnswerDTO> answers) {
        Set<Long> seenQuestionIds = new HashSet<>();
        Set<Long> requiredQuestionIds = onboardingQuestionProvider.getRequiredQuestionIds();
        Map<Long, OnboardingSurveyAnswerDTO> answersByQuestion = new HashMap<>();

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
            if (new HashSet<>(answer.getOptionIds()).size() != answer.getOptionIds().size()) {
                throw new ApiException("Duplicate optionId is not allowed for question: " + answer.getQuestionId(), HttpStatus.BAD_REQUEST);
            }

            Set<Long> validOptionIds = question.getOptions().stream()
                    .map(option -> option.getId())
                    .collect(Collectors.toSet());
            for (Long optionId : answer.getOptionIds()) {
                if (!validOptionIds.contains(optionId)) {
                    throw new ApiException("Option not found for question: " + answer.getQuestionId(), HttpStatus.NOT_FOUND);
                }
            }

            answersByQuestion.put(answer.getQuestionId(), answer);
        }

        if (!seenQuestionIds.equals(requiredQuestionIds)) {
            throw new ApiException("All survey questions must be answered exactly once", HttpStatus.BAD_REQUEST);
        }

        return answersByQuestion;
    }

    private int getSingleValue(Map<Long, OnboardingSurveyAnswerDTO> answersByQuestion,
                               long questionId,
                               OptionValueResolver resolver) {
        long optionId = getSingleSelectedOptionId(answersByQuestion.get(questionId));
        int value = resolver.resolve(optionId);
        if (value < 1 || value > 3) {
            throw new ApiException("Mapped answer is out of range for question: " + questionId, HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private long getSingleSelectedOptionId(OnboardingSurveyAnswerDTO answer) {
        if (answer == null || answer.getOptionIds() == null || answer.getOptionIds().size() != 1) {
            throw new ApiException("Each onboarding question must have exactly one selected option", HttpStatus.BAD_REQUEST);
        }
        return answer.getOptionIds().get(0);
    }

    private List<Long> getSelectedOptionIds(OnboardingSurveyAnswerDTO answer) {
        if (answer == null || answer.getOptionIds() == null || answer.getOptionIds().isEmpty()) {
            throw new ApiException("Each onboarding question must have selected options", HttpStatus.BAD_REQUEST);
        }
        return answer.getOptionIds();
    }

    private void persistEducationRoadmapSeed(User user, String investmentLevel, List<String> sectorIds) {
        String courseId = resolveEducationCourseId(investmentLevel);
        LearningUserStateEntity existing = learningUserStateRepository.findById(user.getId()).orElse(null);
        Map<String, Integer> currentDays = readObject(
                existing == null ? null : existing.getEducationCurrentDayJson(),
                MAP_STRING_INTEGER_TYPE,
                new HashMap<>());
        Map<String, List<String>> sectorSelections = readObject(
                existing == null ? null : existing.getEducationSectorSelectionsJson(),
                MAP_STRING_LIST_STRING_TYPE,
                new HashMap<>());

        currentDays.put(courseId, 1);
        sectorSelections.put(courseId, new ArrayList<>(sectorIds));
        if (INTRO_COURSE_ID.equals(courseId)) {
            sectorSelections.putIfAbsent(ADVANCED_COURSE_ID, new ArrayList<>(sectorIds));
        }

        learningUserStateRepository.save(LearningUserStateEntity.builder()
                .userId(user.getId())
                .level(existing == null || existing.getLevel() == null ? 0 : existing.getLevel())
                .point(existing == null || existing.getPoint() == null ? 0 : existing.getPoint())
                .activeCourseId(existing == null ? null : existing.getActiveCourseId())
                .streakDays(existing == null || existing.getStreakDays() == null ? 0 : existing.getStreakDays())
                .lastCompletedDate(existing == null ? null : existing.getLastCompletedDate())
                .roadmapLastCompletedDate(existing == null ? null : existing.getRoadmapLastCompletedDate())
                .currentDayByCourseJson(existing == null ? "{}" : defaultObjectJson(existing.getCurrentDayByCourseJson()))
                .completedDaysByCourseJson(existing == null ? "{}" : defaultObjectJson(existing.getCompletedDaysByCourseJson()))
                .submittedStepIdsJson(existing == null ? "[]" : defaultArrayJson(existing.getSubmittedStepIdsJson()))
                .educationCurrentDayJson(writeValue(currentDays))
                .educationCompletedDaysJson(existing == null ? "{}" : defaultObjectJson(existing.getEducationCompletedDaysJson()))
                .educationQuizAnswersJson(existing == null ? "{}" : defaultObjectJson(existing.getEducationQuizAnswersJson()))
                .educationCardProgressJson(existing == null ? "{}" : defaultObjectJson(existing.getEducationCardProgressJson()))
                .educationSectorSelectionsJson(writeValue(sectorSelections))
                .build());
    }

    private String resolveEducationCourseId(String investmentLevel) {
        if ("입문".equals(investmentLevel)) {
            return INTRO_COURSE_ID;
        }
        return ADVANCED_COURSE_ID;
    }

    private boolean hasResult(User user) {
        return user.getInvestmentProfileResult() != null
                && !user.getInvestmentProfileResult().isBlank()
                && user.getInvestmentLevel() != null
                && !user.getInvestmentLevel().isBlank()
                && user.getInterestSector() != null
                && !user.getInterestSector().isBlank();
    }

    private <T> T readObject(String json, TypeReference<T> typeReference, T defaultValue) {
        if (json == null || json.isBlank()) {
            return defaultValue;
        }
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException exception) {
            return defaultValue;
        }
    }

    private String writeValue(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize onboarding education state", exception);
        }
    }

    private String defaultObjectJson(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }

    private String defaultArrayJson(String value) {
        return value == null || value.isBlank() ? "[]" : value;
    }

    @FunctionalInterface
    private interface OptionValueResolver {
        int resolve(Long optionId);
    }
}
