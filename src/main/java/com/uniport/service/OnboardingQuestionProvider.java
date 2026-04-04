package com.uniport.service;

import com.uniport.dto.OnboardingSurveyOptionDTO;
import com.uniport.dto.OnboardingSurveyQuestionDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class OnboardingQuestionProvider {

    public List<OnboardingSurveyQuestionDTO> getQuestions() {
        return List.of(
                singleQuestion(1L, 1, "1주일 사이 100만원이 95만원(-5%)으로 떨어졌어", "나랑 제일 가까운 반응은?",
                        option(1L, "원인부터 다시 확인해본다", "손절보다는 이유를 먼저 살핀다"),
                        option(2L, "일단 조금 더 지켜본다", "흐름을 보고 판단한다"),
                        option(3L, "추가 매수 기회라고 본다", "하락도 기회라고 생각한다")),
                singleQuestion(2L, 2, "가격 변동을 얼마나 자주 보고 싶은 편이야?", "나랑 제일 가까운 반응은?",
                        option(4L, "자주 보면 불안해서 싫다", "가끔 확인하는 게 마음 편하다"),
                        option(5L, "하루 한 번 정도 OK", "마감 시황 정도는 챙겨본다"),
                        option(6L, "수시로 보는 것도 재밌다", "시장의 흐름을 놓치고 싶지 않다")),
                singleQuestion(3L, 3, "이 앱에서 투자를 어느 정도 기간 가져가고 싶어?", "나랑 제일 가까운 반응은?",
                        option(7L, "1년 안에 결과 보고 싶다", "단기 목표 달성형"),
                        option(8L, "3~5년 정도면 괜찮다", "중기 성장 추구형"),
                        option(9L, "10년 이상 길게도 OK", "장기 가치 투자형")),
                singleQuestion(4L, 4, "나는 이런 식으로 투자 결정을 내리고 싶어", "나의 투자 스타일은?",
                        option(10L, "숫자/재무제표/지표 보고", "기업의 내재 가치를 분석해서"),
                        option(11L, "뉴스/스토리/트렌드 보고", "미래 성장 가능성을 예측해서"),
                        option(12L, "차트/가격 흐름 보고", "시장의 흐름과 타이밍을 잡아서")),
                singleQuestion(5L, 5, "투자 해본 경험은 어느 정도야?", "나랑 제일 가까운 반응은?",
                        option(13L, "완전 처음", "계좌도 거의 안 써봄"),
                        option(14L, "가끔 해봄", "소액으로 몇 번 해봤다"),
                        option(15L, "익숙함", "1년 이상 꾸준히 해봤다")),
                OnboardingSurveyQuestionDTO.builder()
                        .id(6L)
                        .order(6)
                        .type("MULTI_SELECT")
                        .title("현재 가장 관심 있는 투자분야는 무엇인가요?")
                        .subtitle("관심 있는 키워드를 모두 선택해주세요. (중복 가능)")
                        .minSelection(1)
                        .maxSelection(3)
                        .options(List.of(
                                option(16L, "AI 반도체", null),
                                option(17L, "로봇", null),
                                option(18L, "방산", null),
                                option(19L, "자율주행", null),
                                option(20L, "2차전지", null),
                                option(21L, "전력기기", null),
                                option(22L, "바이오", null),
                                option(23L, "원전", null),
                                option(24L, "우주/로켓", null)
                        ))
                        .build()
        );
    }

    public boolean hasQuestion(Long questionId) {
        return getQuestions().stream().anyMatch(question -> question.getId().equals(questionId));
    }

    public OnboardingSurveyQuestionDTO getQuestion(Long questionId) {
        return getQuestions().stream()
                .filter(question -> question.getId().equals(questionId))
                .findFirst()
                .orElse(null);
    }

    public Set<Long> getRequiredQuestionIds() {
        return getQuestions().stream().map(OnboardingSurveyQuestionDTO::getId).collect(Collectors.toSet());
    }

    public int getOptionScore(Long optionId) {
        return Map.ofEntries(
                Map.entry(1L, 1), Map.entry(2L, 2), Map.entry(3L, 3),
                Map.entry(4L, 1), Map.entry(5L, 2), Map.entry(6L, 3),
                Map.entry(7L, 1), Map.entry(8L, 2), Map.entry(9L, 3),
                Map.entry(10L, 1), Map.entry(11L, 2), Map.entry(12L, 3),
                Map.entry(13L, 1), Map.entry(14L, 2), Map.entry(15L, 3)
        ).getOrDefault(optionId, 2);
    }

    private OnboardingSurveyQuestionDTO singleQuestion(Long id, int order, String title, String subtitle,
                                                       OnboardingSurveyOptionDTO first,
                                                       OnboardingSurveyOptionDTO second,
                                                       OnboardingSurveyOptionDTO third) {
        return OnboardingSurveyQuestionDTO.builder()
                .id(id)
                .order(order)
                .type("SINGLE_SELECT")
                .title(title)
                .subtitle(subtitle)
                .minSelection(1)
                .maxSelection(1)
                .options(List.of(first, second, third))
                .build();
    }

    private OnboardingSurveyOptionDTO option(Long id, String label, String sublabel) {
        return OnboardingSurveyOptionDTO.builder()
                .id(id)
                .label(label)
                .sublabel(sublabel)
                .build();
    }
}
