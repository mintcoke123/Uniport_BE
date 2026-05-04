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

    public static final long QUESTION_RISK = 1L;
    public static final long QUESTION_TERM = 2L;
    public static final long QUESTION_INVOLVEMENT = 3L;
    public static final long QUESTION_STYLE = 4L;
    public static final long QUESTION_LEVEL = 5L;
    public static final long QUESTION_SECTOR = 6L;

    private static final Map<Long, Integer> RISK_VALUES = Map.of(1L, 1, 2L, 2, 3L, 3);
    private static final Map<Long, Integer> TERM_VALUES = Map.of(4L, 1, 5L, 2, 6L, 3);
    private static final Map<Long, Integer> INVOLVEMENT_VALUES = Map.of(7L, 1, 8L, 2, 9L, 3);
    private static final Map<Long, Integer> STYLE_VALUES = Map.of(10L, 1, 11L, 2, 12L, 3);
    private static final Map<Long, Integer> LEVEL_VALUES = Map.of(13L, 1, 14L, 2, 15L, 3);
    private static final Map<Long, String> SECTOR_VALUES = Map.ofEntries(
            Map.entry(16L, "AI 반도체"),
            Map.entry(17L, "2차전지(배터리)"),
            Map.entry(18L, "로봇·휴머노이드"),
            Map.entry(19L, "전력·전력기기"),
            Map.entry(20L, "방산"),
            Map.entry(21L, "바이오·비만치료제"),
            Map.entry(22L, "자율주행·미래차"),
            Map.entry(23L, "원전·SMR"),
            Map.entry(24L, "양자컴퓨터"),
            Map.entry(25L, "우주·항공")
    );

    public List<OnboardingSurveyQuestionDTO> getQuestions() {
        return List.of(
                singleQuestion(
                        QUESTION_RISK,
                        1,
                        "100만원을 투자했는데 일주일 만에 5만원 손실이 났다. 내 실제 반응에 가장 가까운 것은?",
                        "리스크 성향을 확인하는 질문이야.",
                        option(1L, "불안해서 비중을 줄이거나 매도를 고민한다.", "R1 · 안정"),
                        option(2L, "이유를 확인하고 조금 더 지켜본다.", "R2 · 중립"),
                        option(3L, "원래 생길 수 있는 변동이라 생각하고 계획대로 간다.", "R3 · 공격")
                ),
                singleQuestion(
                        QUESTION_TERM,
                        2,
                        "투자 결과를 어느 정도 시간 기준으로 보는 편이야?",
                        "투자 기간 선호를 확인하는 질문이야.",
                        option(4L, "6개월~1년 안에 흐름과 결과를 보고 싶다.", "T1 · 단기"),
                        option(5L, "1~5년 정도는 기다릴 수 있다.", "T2 · 중기"),
                        option(6L, "5년 이상 길게 보고 키우는 편이 좋다.", "T3 · 장기")
                ),
                singleQuestion(
                        QUESTION_INVOLVEMENT,
                        3,
                        "투자한 뒤 나는 보통 어떻게 운영하는 편이야?",
                        "투자 운영 관여도를 확인하는 질문이야.",
                        option(7L, "자주 보기보다 정해둔 방식으로 천천히 가져가는 편이다.", "M1 · 저관여"),
                        option(8L, "뉴스나 가격을 가끔 확인하며 조정하는 편이다.", "M2 · 보통"),
                        option(9L, "흐름을 자주 보고 직접 판단하고 대응하는 편이다.", "M3 · 고관여")
                ),
                singleQuestion(
                        QUESTION_STYLE,
                        4,
                        "내가 더 끌리는 투자 판단 방식은?",
                        "투자 스타일 축을 확인하는 질문이야.",
                        option(10L, "실적, 재무, 밸류에이션처럼 숫자로 설명되는 투자", "S1 · 가치"),
                        option(11L, "산업 변화, 제품, 성장성, 미래 스토리로 설명되는 투자", "S2 · 성장"),
                        option(12L, "차트, 수급, 가격 흐름처럼 타이밍이 중요한 투자", "S3 · 모멘텀")
                ),
                singleQuestion(
                        QUESTION_LEVEL,
                        5,
                        "내 투자 경험은 어느 쪽에 더 가까워?",
                        "투자 난이도와 경험 수준을 확인하는 질문이야.",
                        option(13L, "완전 처음이거나 계좌 사용이 아직 낯설다.", "LV1"),
                        option(14L, "소액 투자 경험이 있고 기본 용어는 조금 안다.", "LV2"),
                        option(15L, "1년 이상 직접 사고팔아 보며 자기 기준이 조금 생겼다.", "LV3")
                ),
                singleQuestion(
                        QUESTION_SECTOR,
                        6,
                        "지금 가장 관심 있게 보는 투자 분야는?",
                        "관심 섹터는 결과 화면과 투자노트에 따로 보여줄게.",
                        option(16L, "AI 반도체", null),
                        option(17L, "2차전지(배터리)", null),
                        option(18L, "로봇·휴머노이드", null),
                        option(19L, "전력·전력기기", null),
                        option(20L, "방산", null),
                        option(21L, "바이오·비만치료제", null),
                        option(22L, "자율주행·미래차", null),
                        option(23L, "원전·SMR", null),
                        option(24L, "양자컴퓨터", null),
                        option(25L, "우주·항공", null)
                )
        );
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

    private int getRequiredValue(Map<Long, Integer> values, Long optionId, String axisName) {
        Integer value = values.get(optionId);
        if (value == null) {
            throw new IllegalArgumentException("Unknown " + axisName + " option: " + optionId);
        }
        return value;
    }

    private OnboardingSurveyQuestionDTO singleQuestion(Long id,
                                                       int order,
                                                       String title,
                                                       String subtitle,
                                                       OnboardingSurveyOptionDTO... options) {
        return OnboardingSurveyQuestionDTO.builder()
                .id(id)
                .order(order)
                .type("SINGLE_SELECT")
                .title(title)
                .subtitle(subtitle)
                .minSelection(1)
                .maxSelection(1)
                .options(List.of(options))
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
