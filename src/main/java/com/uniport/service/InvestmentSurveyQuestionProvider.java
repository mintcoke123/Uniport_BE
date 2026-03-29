package com.uniport.service;

import com.uniport.dto.InvestmentSurveyQuestionDTO;
import com.uniport.dto.InvestmentSurveyQuestionOptionDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class InvestmentSurveyQuestionProvider {

    public List<InvestmentSurveyQuestionDTO> getQuestions() {
        return List.of(
                InvestmentSurveyQuestionDTO.builder()
                        .id(1L)
                        .order(1)
                        .title("100만원이 1주일 사이 5만원(5%) 떨어졌어.")
                        .subtitle("나랑 제일 가까운 반응은?")
                        .options(List.of(
                                InvestmentSurveyQuestionOptionDTO.builder()
                                        .id(1L)
                                        .label("바로 팔고 다시는 안 한다")
                                        .sublabel("손실을 더 보고 싶지 않다")
                                        .build(),
                                InvestmentSurveyQuestionOptionDTO.builder()
                                        .id(2L)
                                        .label("이유를 찾아보고 조금 더 지켜본다")
                                        .sublabel("상황을 보고 판단한다")
                                        .build(),
                                InvestmentSurveyQuestionOptionDTO.builder()
                                        .id(3L)
                                        .label("오히려 추가 매수 기회라고 본다")
                                        .sublabel("장기적으로 다시 오를 수 있다")
                                        .build()
                        ))
                        .build(),
                InvestmentSurveyQuestionDTO.builder()
                        .id(2L)
                        .order(2)
                        .title("가격 변동을 얼마나 자주 보고 싶은 편인가요?")
                        .subtitle(null)
                        .options(List.of(
                                InvestmentSurveyQuestionOptionDTO.builder()
                                        .id(4L)
                                        .label("자주 보면 불안해서 싫다")
                                        .sublabel(null)
                                        .build(),
                                InvestmentSurveyQuestionOptionDTO.builder()
                                        .id(5L)
                                        .label("하루 한 번 정도는 괜찮다")
                                        .sublabel(null)
                                        .build(),
                                InvestmentSurveyQuestionOptionDTO.builder()
                                        .id(6L)
                                        .label("수시로 보는 편이 편하다")
                                        .sublabel(null)
                                        .build()
                        ))
                        .build()
        );
    }

    public boolean hasQuestion(long questionId) {
        return getQuestions().stream().anyMatch(question -> question.getId() == questionId);
    }

    public boolean hasOption(long questionId, long optionId) {
        return getQuestions().stream()
                .filter(question -> question.getId() == questionId)
                .flatMap(question -> question.getOptions().stream())
                .anyMatch(option -> option.getId() == optionId);
    }

    public int getOptionScore(long optionId) {
        return switch ((int) optionId) {
            case 1, 4 -> 1;
            case 2, 5 -> 2;
            case 3, 6 -> 3;
            default -> throw new IllegalArgumentException("Unknown survey option id: " + optionId);
        };
    }

    public Set<Long> getRequiredQuestionIds() {
        return getQuestions().stream()
                .map(InvestmentSurveyQuestionDTO::getId)
                .collect(Collectors.toSet());
    }
}