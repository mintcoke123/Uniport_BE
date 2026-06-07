package com.uniport.service;

import com.uniport.dto.OnboardingSurveyOptionDTO;
import com.uniport.dto.OnboardingSurveyQuestionDTO;

import java.util.List;

final class OnboardingSurveySeed {

    private static final List<QuestionSeed> QUESTIONS = List.of(
            singleQuestion(
                    OnboardingQuestionProvider.QUESTION_RISK,
                    1,
                    "1주일 사이 100만원이\n95만원(-5%) 으로 떨어졌어",
                    "나랑 제일 가까운 반응은?",
                    option(1L, 1, "바로 팔고 다시는 안 산다", "스트레스 받아서 못견디겠어"),
                    option(2L, 2, "이유 찾아보고 더 지켜본다", "일시적인 하락일 수도 있잖아?"),
                    option(3L, 3, "원래 그럴 수 있다 그냥 둔다", "장기적으로 보면 오를 거야")
            ),
            singleQuestion(
                    OnboardingQuestionProvider.QUESTION_INVOLVEMENT,
                    2,
                    "가격 변동을\n얼마나 자주 확인하는 편이야?",
                    "나랑 제일 가까운 반응은?",
                    option(4L, 1, "자주 보면 불안해서 싫다", "가끔 확인하는게 마음 편해요"),
                    option(5L, 2, "하루 한 번 정도 OK", "마감 시황 정도는 챙겨봐요"),
                    option(6L, 3, "수시로 보는 것도 재밌다", "시장의 흐름을 놓치고 싶지 않아요")
            ),
            singleQuestion(
                    OnboardingQuestionProvider.QUESTION_TERM,
                    3,
                    "이 앱에서 투자기간를\n어느 정도 기간 가져가고 싶어?",
                    "나랑 제일 가까운 반응은?",
                    option(7L, 1, "1년 안에 결과 보고 싶다", "단기 목표 달성형"),
                    option(8L, 2, "3~5년 정도면 괜찮다", "중기 성장 추구형"),
                    option(9L, 3, "10년 이상 길게도 OK", "장기 가치 투자형")
            ),
            singleQuestion(
                    OnboardingQuestionProvider.QUESTION_STYLE,
                    4,
                    "나는 이런 식으로\n투자 결정을 내리고 싶어",
                    "나의 투자 스타일은?",
                    option(10L, 1, "숫자/재무제표/지표 보고", "기업의 내재 가치를 분석해서"),
                    option(11L, 2, "뉴스/스토리/트렌드 보고", "미래 성장 가능성을 예측해서"),
                    option(12L, 3, "차트/가격 흐름 보고", "시장의 흐름과 타이밍을 잡아서")
            ),
            singleQuestion(
                    OnboardingQuestionProvider.QUESTION_LEVEL,
                    5,
                    "투자 해본 경험은\n어느 정도야?",
                    "나랑 제일 가까운 반응은?",
                    option(13L, 1, "완전 처음", "계좌도 거의 안 써봄"),
                    option(14L, 2, "가끔 해봄", "소액으로 몇 번 해봤다"),
                    option(15L, 3, "익숙함", "1년 이상 꾸준히 해봤다")
            ),
            multiQuestion(
                    OnboardingQuestionProvider.QUESTION_SECTOR,
                    6,
                    "현재 가장 관심 있는\n투자분야가 있다면?",
                    "관심 있는 키워드를 모두 선택해주세요. (최대 2개)",
                    option(16L, 1, "AI 반도체", null),
                    option(18L, 2, "로봇", null),
                    option(20L, 3, "방산", null),
                    option(22L, 4, "자율주행", null),
                    option(24L, 5, "양자컴퓨터", null),
                    option(17L, 6, "2차전지", null),
                    option(19L, 7, "전력기기", null),
                    option(21L, 8, "바이오", null),
                    option(23L, 9, "원전", null),
                    option(25L, 10, "우주/로켓", null)
            )
    );

    private OnboardingSurveySeed() {
    }

    static List<QuestionSeed> questions() {
        return QUESTIONS;
    }

    static List<OnboardingSurveyQuestionDTO> questionDtos() {
        return QUESTIONS.stream()
                .map(OnboardingSurveySeed::toDto)
                .toList();
    }

    static OnboardingSurveyQuestionDTO toDto(QuestionSeed question) {
        return OnboardingSurveyQuestionDTO.builder()
                .id(question.id())
                .order(question.order())
                .type(question.type())
                .title(question.title())
                .subtitle(question.subtitle())
                .minSelection(question.minSelection())
                .maxSelection(question.maxSelection())
                .options(question.options().stream()
                        .map(OnboardingSurveySeed::toDto)
                        .toList())
                .build();
    }

    static OnboardingSurveyOptionDTO toDto(OptionSeed option) {
        return OnboardingSurveyOptionDTO.builder()
                .id(option.id())
                .label(option.label())
                .sublabel(option.sublabel())
                .build();
    }

    private static QuestionSeed singleQuestion(
            long id,
            int order,
            String title,
            String subtitle,
            OptionSeed... options
    ) {
        return new QuestionSeed(id, order, "SINGLE_SELECT", title, subtitle, 1, 1, List.of(options));
    }

    private static QuestionSeed multiQuestion(
            long id,
            int order,
            String title,
            String subtitle,
            OptionSeed... options
    ) {
        return new QuestionSeed(id, order, "MULTI_SELECT", title, subtitle, 2, 2, List.of(options));
    }

    private static OptionSeed option(long id, int order, String label, String sublabel) {
        return new OptionSeed(id, order, label, sublabel);
    }

    record QuestionSeed(
            long id,
            int order,
            String type,
            String title,
            String subtitle,
            int minSelection,
            int maxSelection,
            List<OptionSeed> options
    ) {
    }

    record OptionSeed(
            long id,
            int order,
            String label,
            String sublabel
    ) {
    }
}
