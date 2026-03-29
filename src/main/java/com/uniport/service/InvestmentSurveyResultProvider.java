package com.uniport.service;

import com.uniport.dto.SurveyOnboardingResponseDTO;
import com.uniport.dto.SurveyResultDetailItemDTO;
import com.uniport.dto.SurveyResultSectionDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class InvestmentSurveyResultProvider {

    private final Map<String, SurveyOnboardingResponseDTO> results = Map.of(
            "안정 중시형", SurveyOnboardingResponseDTO.builder()
                    .id(1L)
                    .type("안정 중시형")
                    .title("안정 중시형")
                    .description("안정성과 예측 가능한 흐름을 더 중요하게 생각하는 투자 성향입니다.")
                    .imageUrl("https://example.com/images/result-conservative.png")
                    .features(List.of(
                            SurveyResultSectionDTO.builder()
                                    .title("나의 투자원칙 TOP 3")
                                    .items(List.of(
                                            SurveyResultDetailItemDTO.builder().name("손실 회피").description("큰 손실 가능성을 줄이는 것을 우선으로 생각합니다.").build(),
                                            SurveyResultDetailItemDTO.builder().name("꾸준한 흐름 선호").description("급격한 변동보다 안정적인 흐름에 더 편안함을 느낍니다.").build(),
                                            SurveyResultDetailItemDTO.builder().name("신중한 진입").description("투자 전 충분히 확인하고 결정하는 편입니다.").build()
                                    ))
                                    .build()
                    ))
                    .guides(List.of(
                            SurveyResultSectionDTO.builder()
                                    .title("내 유형의 특징")
                                    .items(List.of(
                                            SurveyResultDetailItemDTO.builder().name("리스크 관리").description("손실 가능성을 낮추는 자산 배분과 잘 맞습니다.").build(),
                                            SurveyResultDetailItemDTO.builder().name("방어적 투자").description("안정적인 자산 비중을 높이는 전략이 잘 맞습니다.").build(),
                                            SurveyResultDetailItemDTO.builder().name("분산 투자").description("하나에 몰기보다 나누어 투자할 때 강점을 보입니다.").build()
                                    ))
                                    .build()
                    ))
                    .build(),
            "균형 잡힌 판단형", SurveyOnboardingResponseDTO.builder()
                    .id(2L)
                    .type("균형 잡힌 판단형")
                    .title("균형 잡힌 판단형")
                    .description("안정성과 수익 사이의 균형을 중요하게 생각하는 투자 성향입니다.")
                    .imageUrl("https://example.com/images/result-balanced.png")
                    .features(List.of(
                            SurveyResultSectionDTO.builder()
                                    .title("나의 투자원칙 TOP 3")
                                    .items(List.of(
                                            SurveyResultDetailItemDTO.builder().name("분석형 의사결정").description("중요한 투자 결정을 내릴 때 신중하게 판단하는 편입니다.").build(),
                                            SurveyResultDetailItemDTO.builder().name("위험 관리").description("손실 가능성을 줄이면서도 적절한 수익을 기대합니다.").build(),
                                            SurveyResultDetailItemDTO.builder().name("장기적 관점").description("짧은 변동보다 장기적인 흐름을 더 중요하게 생각합니다.").build()
                                    ))
                                    .build()
                    ))
                    .guides(List.of(
                            SurveyResultSectionDTO.builder()
                                    .title("내 유형의 특징")
                                    .items(List.of(
                                            SurveyResultDetailItemDTO.builder().name("리스크 관리 능력").description("과도한 손실을 피하려는 성향이 강합니다.").build(),
                                            SurveyResultDetailItemDTO.builder().name("안정적 투자 선호").description("급격한 변동보다 꾸준한 흐름을 선호합니다.").build(),
                                            SurveyResultDetailItemDTO.builder().name("분산 투자 적합").description("한 종목에 집중하기보다 나누어 투자하는 방식과 잘 맞습니다.").build()
                                    ))
                                    .build()
                    ))
                    .build(),
            "기회 포착형", SurveyOnboardingResponseDTO.builder()
                    .id(3L)
                    .type("기회 포착형")
                    .title("기회 포착형")
                    .description("변동 속에서도 기회를 찾고 적극적으로 대응하는 투자 성향입니다.")
                    .imageUrl("https://example.com/images/result-aggressive.png")
                    .features(List.of(
                            SurveyResultSectionDTO.builder()
                                    .title("나의 투자원칙 TOP 3")
                                    .items(List.of(
                                            SurveyResultDetailItemDTO.builder().name("기회 탐색").description("가격 변동을 기회로 보는 경향이 강합니다.").build(),
                                            SurveyResultDetailItemDTO.builder().name("빠른 반응").description("시장 변화에 민감하게 반응하며 대응하려고 합니다.").build(),
                                            SurveyResultDetailItemDTO.builder().name("수익 추구").description("더 높은 성과를 위해 적극적인 선택을 할 수 있습니다.").build()
                                    ))
                                    .build()
                    ))
                    .guides(List.of(
                            SurveyResultSectionDTO.builder()
                                    .title("내 유형의 특징")
                                    .items(List.of(
                                            SurveyResultDetailItemDTO.builder().name("변동성 수용").description("가격 흔들림을 비교적 잘 받아들이는 편입니다.").build(),
                                            SurveyResultDetailItemDTO.builder().name("공격적 투자 성향").description("성장성과 기회를 중요하게 생각합니다.").build(),
                                            SurveyResultDetailItemDTO.builder().name("기준 있는 매매").description("적극적일수록 손절과 분산 기준을 함께 세우는 것이 중요합니다.").build()
                                    ))
                                    .build()
                    ))
                    .build()
    );

    public SurveyOnboardingResponseDTO getByType(String type) {
        SurveyOnboardingResponseDTO result = results.get(type);
        if (result == null) {
            throw new IllegalArgumentException("Unknown investment survey result type: " + type);
        }
        return result;
    }
}
