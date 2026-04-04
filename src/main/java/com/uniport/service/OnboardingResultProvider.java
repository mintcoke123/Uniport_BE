package com.uniport.service;

import com.uniport.dto.OnboardingSurveyResultDTO;
import com.uniport.dto.SurveyResultDetailItemDTO;
import com.uniport.dto.SurveyResultSectionDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class OnboardingResultProvider {

    private final Map<String, OnboardingSurveyResultDTO> results = Map.of(
            "조심스러운 거북이형", result(
                    1L,
                    "조심스러운 거북이형",
                    "원금은 지키면서 천천히 배우고 싶은 장기형 투자자시네요!",
                    "Lv.1",
                    "이런 투자자일 확률이 높아요!",
                    "코어+위성 조합",
                    List.of(
                            section("나만의 투자원칙", "하나!", "뇌동매매 금지", "매수 전 3번 생각하는 습관을 기르세요"),
                            section("나만의 투자원칙", "둘!", "자동이체로 강제 저축 시스템을 만들어요", "매달 월급날 투자금 이체"),
                            section("나만의 투자원칙", "셋!", "모르는 기업엔 투자 금지", "사업보고서를 읽을 줄 아는 기업에만 투자하세요")
                    ),
                    List.of(
                            section("조심스러운 거북이형의 투자 성향을 분석했어요", "헤드헤드헤드", "레이달리오 원칙과 비슷", "안정적인 리스크 관리를 중요시하는 스타일입니다"),
                            section("조심스러운 거북이형의 투자 성향을 분석했어요", "서브서브서브", "전체 투자자 40%가 이 타입", "가장 보편적이고 균형 잡힌 투자 성향을 가지고 있습니다"),
                            section("조심스러운 거북이형의 투자 성향을 분석했어요", "서브서브서브", "팀전에서 리더에 적합", "논리적인 판단력으로 팀을 올바른 방향으로 이끌 수 있습니다")
                    )
            ),
            "균형잡힌 판다형", result(
                    2L,
                    "균형잡힌 판다형",
                    "균형 있게 분석하면서 기회를 찾는 중기형 투자자시네요!",
                    "Lv.1",
                    "이런 투자자일 확률이 높아요!",
                    "코어+위성 조합",
                    List.of(
                            section("나만의 투자원칙", "하나!", "분산 투자 유지", "한 번에 한 종목으로 몰지 않아요"),
                            section("나만의 투자원칙", "둘!", "이슈보다 데이터 우선", "근거가 있는 투자만 선택해요"),
                            section("나만의 투자원칙", "셋!", "리스크 점검 루틴화", "손실 기준을 먼저 세워요")
                    ),
                    List.of(
                            section("균형잡힌 판다형의 투자 성향을 분석했어요", "헤드헤드헤드", "가장 보편적인 투자 타입", "안정성과 성장성의 균형을 중요하게 생각합니다"),
                            section("균형잡힌 판다형의 투자 성향을 분석했어요", "서브서브서브", "시장 흐름과 기업가치를 함께 봐요", "중기적인 시각에서 합리적으로 접근합니다"),
                            section("균형잡힌 판다형의 투자 성향을 분석했어요", "서브서브서브", "팀 플레이에 잘 맞아요", "의견을 조율하며 방향을 잡는 역할에 강합니다")
                    )
            ),
            "기회를 찾는 여우형", result(
                    3L,
                    "기회를 찾는 여우형",
                    "기민하게 시장 기회를 포착하는 공격형 투자자시네요!",
                    "Lv.1",
                    "이런 투자자일 확률이 높아요!",
                    "모멘텀+분할매수 조합",
                    List.of(
                            section("나만의 투자원칙", "하나!", "손절 기준 명확히", "공격적인 만큼 기준을 먼저 세워요"),
                            section("나만의 투자원칙", "둘!", "이슈 대응 빠르게", "뉴스와 수급을 빠르게 확인해요"),
                            section("나만의 투자원칙", "셋!", "기회는 나눠서 진입", "분할매수로 변동성을 관리해요")
                    ),
                    List.of(
                            section("기회를 찾는 여우형의 투자 성향을 분석했어요", "헤드헤드헤드", "민감한 시장 감각 보유", "성장과 모멘텀을 빠르게 포착하는 편입니다"),
                            section("기회를 찾는 여우형의 투자 성향을 분석했어요", "서브서브서브", "리스크 허용도가 높은 편", "변동성을 감수하고 기회를 찾는 유형입니다"),
                            section("기회를 찾는 여우형의 투자 성향을 분석했어요", "서브서브서브", "빠른 실행력이 강점", "타이밍과 판단 속도가 장점으로 작용할 수 있습니다")
                    )
            )
    );

    public OnboardingSurveyResultDTO getByType(String type) {
        OnboardingSurveyResultDTO result = results.get(type);
        if (result == null) {
            throw new IllegalArgumentException("Unknown onboarding result type: " + type);
        }
        return result;
    }

    private OnboardingSurveyResultDTO result(Long id,
                                             String type,
                                             String description,
                                             String levelLabel,
                                             String probabilityLabel,
                                             String strategyLabel,
                                             List<SurveyResultSectionDTO> features,
                                             List<SurveyResultSectionDTO> guides) {
        return OnboardingSurveyResultDTO.builder()
                .id(id)
                .type(type)
                .title(type)
                .description(description)
                .imageUrl("https://example.com/images/" + id + ".png")
                .levelLabel(levelLabel)
                .probabilityLabel(probabilityLabel)
                .strategyTitle("추천 전략")
                .strategyLabel(strategyLabel)
                .features(features)
                .guides(guides)
                .build();
    }

    private SurveyResultSectionDTO section(String title, String name, String headline, String description) {
        return SurveyResultSectionDTO.builder()
                .title(title)
                .items(List.of(
                        SurveyResultDetailItemDTO.builder()
                                .name(name)
                                .description(headline + " - " + description)
                                .build()
                ))
                .build();
    }
}
