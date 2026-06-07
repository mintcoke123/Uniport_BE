package com.uniport.service;

import com.uniport.dto.OnboardingSurveyOptionDTO;
import com.uniport.dto.OnboardingSurveyQuestionDTO;
import com.uniport.entity.OnboardingSurveyOptionEntity;
import com.uniport.entity.OnboardingSurveyQuestionEntity;
import com.uniport.repository.OnboardingSurveyOptionRepository;
import com.uniport.repository.OnboardingSurveyQuestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class OnboardingQuestionProvider {

    public static final long QUESTION_RISK = 1L;
    public static final long QUESTION_INVOLVEMENT = 2L;
    public static final long QUESTION_TERM = 3L;
    public static final long QUESTION_STYLE = 4L;
    public static final long QUESTION_LEVEL = 5L;
    public static final long QUESTION_SECTOR = 6L;

    private static final Map<Long, Integer> RISK_VALUES = Map.of(1L, 1, 2L, 2, 3L, 3);
    private static final Map<Long, Integer> INVOLVEMENT_VALUES = Map.of(4L, 1, 5L, 2, 6L, 3);
    private static final Map<Long, Integer> TERM_VALUES = Map.of(7L, 1, 8L, 2, 9L, 3);
    private static final Map<Long, Integer> STYLE_VALUES = Map.of(10L, 1, 11L, 2, 12L, 3);
    private static final Map<Long, Integer> LEVEL_VALUES = Map.of(13L, 1, 14L, 2, 15L, 3);

    private static final Map<Long, String> SECTOR_VALUES = Map.ofEntries(
            Map.entry(16L, "AI 반도체"),
            Map.entry(17L, "2차전지"),
            Map.entry(18L, "로봇"),
            Map.entry(19L, "전력기기"),
            Map.entry(20L, "방산"),
            Map.entry(21L, "바이오"),
            Map.entry(22L, "자율주행"),
            Map.entry(23L, "원전"),
            Map.entry(24L, "양자컴퓨터"),
            Map.entry(25L, "우주/로켓")
    );

    private static final Map<Long, String> SECTOR_IDS = Map.ofEntries(
            Map.entry(16L, "ai_semiconductor"),
            Map.entry(17L, "battery"),
            Map.entry(18L, "robot"),
            Map.entry(19L, "power_equipment"),
            Map.entry(20L, "defense"),
            Map.entry(21L, "bio"),
            Map.entry(22L, "autonomous_driving"),
            Map.entry(23L, "nuclear"),
            Map.entry(24L, "quantum_computer"),
            Map.entry(25L, "space_rocket")
    );

    private final OnboardingSurveyQuestionRepository questionRepository;
    private final OnboardingSurveyOptionRepository optionRepository;

    @Autowired
    public OnboardingQuestionProvider(
            OnboardingSurveyQuestionRepository questionRepository,
            OnboardingSurveyOptionRepository optionRepository
    ) {
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
    }

    OnboardingQuestionProvider() {
        this.questionRepository = null;
        this.optionRepository = null;
    }

    public List<OnboardingSurveyQuestionDTO> getQuestions() {
        if (questionRepository == null || optionRepository == null) {
            return OnboardingSurveySeed.questionDtos();
        }

        List<OnboardingSurveyQuestionEntity> questions = questionRepository.findByActiveTrueOrderByQuestionOrderAsc();
        if (questions.isEmpty()) {
            throw new IllegalStateException("Onboarding survey questions are not seeded");
        }

        List<Long> questionIds = questions.stream()
                .map(OnboardingSurveyQuestionEntity::getId)
                .toList();
        Map<Long, List<OnboardingSurveyOptionEntity>> optionsByQuestionId = optionRepository
                .findActiveByQuestionIds(questionIds)
                .stream()
                .collect(Collectors.groupingBy(option -> option.getQuestion().getId()));

        return questions.stream()
                .map(question -> toDto(
                        question,
                        optionsByQuestionId.getOrDefault(question.getId(), Collections.emptyList())
                ))
                .toList();
    }

    public OnboardingSurveyQuestionDTO getQuestion(Long questionId) {
        return getQuestions().stream()
                .filter(question -> question.getId().equals(questionId))
                .findFirst()
                .orElse(null);
    }

    public Set<Long> getRequiredQuestionIds() {
        return getQuestions().stream()
                .map(OnboardingSurveyQuestionDTO::getId)
                .collect(Collectors.toSet());
    }

    public int getRiskValue(Long optionId) {
        return getRequiredValue(RISK_VALUES, optionId, "risk");
    }

    public int getTermValue(Long optionId) {
        return getRequiredValue(TERM_VALUES, optionId, "term");
    }

    public int getInvolvementValue(Long optionId) {
        return getRequiredValue(INVOLVEMENT_VALUES, optionId, "involvement");
    }

    public int getStyleValue(Long optionId) {
        return getRequiredValue(STYLE_VALUES, optionId, "style");
    }

    public int getLevelValue(Long optionId) {
        return getRequiredValue(LEVEL_VALUES, optionId, "level");
    }

    public String getLevelLabel(Long optionId) {
        return switch (getLevelValue(optionId)) {
            case 1 -> "입문";
            case 2 -> "기본";
            case 3 -> "심화";
            default -> throw new IllegalArgumentException("Unsupported level option: " + optionId);
        };
    }

    public String getSectorLabel(Long optionId) {
        String sector = SECTOR_VALUES.get(optionId);
        if (sector == null) {
            throw new IllegalArgumentException("Unknown sector option: " + optionId);
        }
        return sector;
    }

    public String getSectorId(Long optionId) {
        String sectorId = SECTOR_IDS.get(optionId);
        if (sectorId == null) {
            throw new IllegalArgumentException("Unknown sector option: " + optionId);
        }
        return sectorId;
    }

    private int getRequiredValue(Map<Long, Integer> values, Long optionId, String axisName) {
        Integer value = values.get(optionId);
        if (value == null) {
            throw new IllegalArgumentException("Unknown " + axisName + " option: " + optionId);
        }
        return value;
    }

    private OnboardingSurveyQuestionDTO toDto(
            OnboardingSurveyQuestionEntity question,
            List<OnboardingSurveyOptionEntity> options
    ) {
        if (options.isEmpty()) {
            throw new IllegalStateException("Onboarding survey question options are not seeded: " + question.getId());
        }
        return OnboardingSurveyQuestionDTO.builder()
                .id(question.getId())
                .order(question.getQuestionOrder())
                .type(question.getType())
                .title(question.getTitle())
                .subtitle(question.getSubtitle())
                .minSelection(question.getMinSelection())
                .maxSelection(question.getMaxSelection())
                .options(options.stream()
                        .map(this::toDto)
                        .toList())
                .build();
    }

    private OnboardingSurveyOptionDTO toDto(OnboardingSurveyOptionEntity option) {
        return OnboardingSurveyOptionDTO.builder()
                .id(option.getId())
                .label(option.getLabel())
                .sublabel(option.getSublabel())
                .build();
    }
}
