package com.uniport.service;

import com.uniport.entity.OnboardingSurveyOptionEntity;
import com.uniport.entity.OnboardingSurveyQuestionEntity;
import com.uniport.repository.OnboardingSurveyOptionRepository;
import com.uniport.repository.OnboardingSurveyQuestionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class OnboardingSurveySeeder implements ApplicationRunner {

    private final OnboardingSurveyQuestionRepository questionRepository;
    private final OnboardingSurveyOptionRepository optionRepository;

    public OnboardingSurveySeeder(
            OnboardingSurveyQuestionRepository questionRepository,
            OnboardingSurveyOptionRepository optionRepository
    ) {
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<Long> seedQuestionIds = OnboardingSurveySeed.questions().stream()
                .map(OnboardingSurveySeed.QuestionSeed::id)
                .collect(Collectors.toSet());
        Set<Long> seedOptionIds = OnboardingSurveySeed.questions().stream()
                .flatMap(question -> question.options().stream())
                .map(OnboardingSurveySeed.OptionSeed::id)
                .collect(Collectors.toSet());

        questionRepository.findAll().stream()
                .filter(question -> !seedQuestionIds.contains(question.getId()))
                .forEach(question -> {
                    question.setActive(Boolean.FALSE);
                    questionRepository.save(question);
                });
        optionRepository.findAll().stream()
                .filter(option -> !seedOptionIds.contains(option.getId()))
                .forEach(option -> {
                    option.setActive(Boolean.FALSE);
                    optionRepository.save(option);
                });

        for (OnboardingSurveySeed.QuestionSeed seedQuestion : OnboardingSurveySeed.questions()) {
            OnboardingSurveyQuestionEntity question = questionRepository.findById(seedQuestion.id())
                    .orElseGet(() -> OnboardingSurveyQuestionEntity.builder()
                            .id(seedQuestion.id())
                            .build());
            question.setQuestionOrder(seedQuestion.order());
            question.setType(seedQuestion.type());
            question.setTitle(seedQuestion.title());
            question.setSubtitle(seedQuestion.subtitle());
            question.setMinSelection(seedQuestion.minSelection());
            question.setMaxSelection(seedQuestion.maxSelection());
            question.setActive(Boolean.TRUE);
            questionRepository.save(question);

            for (OnboardingSurveySeed.OptionSeed seedOption : seedQuestion.options()) {
                OnboardingSurveyOptionEntity option = optionRepository.findById(seedOption.id())
                        .orElseGet(() -> OnboardingSurveyOptionEntity.builder()
                                .id(seedOption.id())
                                .build());
                option.setQuestion(question);
                option.setOptionOrder(seedOption.order());
                option.setLabel(seedOption.label());
                option.setSublabel(seedOption.sublabel());
                option.setActive(Boolean.TRUE);
                optionRepository.save(option);
            }
        }
    }
}
