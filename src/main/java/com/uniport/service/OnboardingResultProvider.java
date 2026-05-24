package com.uniport.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.dto.OnboardingSurveyResultDTO;
import com.uniport.dto.SurveyResultDetailItemDTO;
import com.uniport.dto.SurveyResultSectionDTO;
import com.uniport.entity.OnboardingResultCatalog;
import com.uniport.repository.OnboardingResultCatalogRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Component
public class OnboardingResultProvider {

    private static final int RISK_WEIGHT = 4;
    private static final int TERM_WEIGHT = 3;
    private static final int STYLE_WEIGHT = 2;
    private static final int INVOLVEMENT_WEIGHT = 1;
    private static final String STRATEGY_SECTION_TITLE = "추천 전략";
    private static final String PRINCIPLES_SECTION_TITLE = "나만의 투자원칙";
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final Map<Integer, CharacterProfile> characters = createCharacters();
    private final OnboardingResultCatalogRepository catalogRepository;
    private final ObjectMapper objectMapper;

    public OnboardingResultProvider(OnboardingResultCatalogRepository catalogRepository,
                                    ObjectMapper objectMapper) {
        this.catalogRepository = catalogRepository;
        this.objectMapper = objectMapper;
    }

    public OnboardingSurveyResultDTO classify(int risk,
                                              int term,
                                              int style,
                                              int involvement,
                                              String investmentLevel,
                                              String interestSector) {
        CharacterProfile bestProfile = null;
        double bestDistance = Double.MAX_VALUE;

        for (CharacterProfile candidate : characters.values()) {
            double candidateDistance = distance(risk, term, style, involvement, candidate);
            if (bestProfile == null || isBetterCandidate(
                    risk,
                    term,
                    style,
                    involvement,
                    candidate,
                    candidateDistance,
                    bestProfile,
                    bestDistance)) {
                bestProfile = candidate;
                bestDistance = candidateDistance;
            }
        }

        return toResult(bestProfile, loadCatalog(bestProfile.id()), investmentLevel, interestSector);
    }

    public OnboardingSurveyResultDTO getByCharacterName(String characterName,
                                                        String investmentLevel,
                                                        String interestSector) {
        OnboardingResultCatalog catalog = catalogRepository.findAllByActiveTrueOrderByCharacterIdAsc().stream()
                .filter(candidate -> matchesCharacterName(candidate, characterName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown onboarding result type: " + characterName));
        CharacterProfile profile = characters.get(catalog.getCharacterId());
        if (profile == null) {
            throw new IllegalStateException("Missing onboarding classification profile for character " + catalog.getCharacterId());
        }

        return toResult(profile, catalog, investmentLevel, interestSector);
    }

    private double distance(int risk, int term, int style, int involvement, CharacterProfile profile) {
        return RISK_WEIGHT * Math.abs(risk - profile.centroidRisk())
                + TERM_WEIGHT * Math.abs(term - profile.centroidTerm())
                + STYLE_WEIGHT * Math.abs(style - profile.centroidStyle())
                + INVOLVEMENT_WEIGHT * Math.abs(involvement - profile.centroidInvolvement());
    }

    private boolean isBetterCandidate(int risk,
                                      int term,
                                      int style,
                                      int involvement,
                                      CharacterProfile candidate,
                                      double candidateDistance,
                                      CharacterProfile bestProfile,
                                      double bestDistance) {
        double distanceDelta = candidateDistance - bestDistance;
        if (Math.abs(distanceDelta) > 1e-9) {
            return distanceDelta < 0;
        }

        int tieBreak = compareTieBreak(risk, term, style, involvement, candidate, bestProfile);
        if (tieBreak != 0) {
            return tieBreak < 0;
        }

        return candidate.id() < bestProfile.id();
    }

    private int compareTieBreak(int risk,
                                int term,
                                int style,
                                int involvement,
                                CharacterProfile candidate,
                                CharacterProfile bestProfile) {
        int riskCompare = Double.compare(
                Math.abs(risk - candidate.centroidRisk()),
                Math.abs(risk - bestProfile.centroidRisk()));
        if (riskCompare != 0) {
            return riskCompare;
        }

        int termCompare = Double.compare(
                Math.abs(term - candidate.centroidTerm()),
                Math.abs(term - bestProfile.centroidTerm()));
        if (termCompare != 0) {
            return termCompare;
        }

        int styleCompare = Double.compare(
                Math.abs(style - candidate.centroidStyle()),
                Math.abs(style - bestProfile.centroidStyle()));
        if (styleCompare != 0) {
            return styleCompare;
        }

        return Double.compare(
                Math.abs(involvement - candidate.centroidInvolvement()),
                Math.abs(involvement - bestProfile.centroidInvolvement()));
    }

    private OnboardingSurveyResultDTO toResult(CharacterProfile profile,
                                               OnboardingResultCatalog catalog,
                                               String investmentLevel,
                                               String interestSector) {
        String resolvedLevel = investmentLevel == null || investmentLevel.isBlank() ? "입문" : investmentLevel;
        String resolvedSector = interestSector == null || interestSector.isBlank() ? null : interestSector;
        List<String> traits = readRequiredStringList(catalog.getTraitsJson(), "traits_json", catalog.getCharacterId());
        List<String> traitDescriptions = readRequiredStringList(
                catalog.getTraitDescriptionsJson(),
                "trait_descriptions_json",
                catalog.getCharacterId());
        List<String> principles = readRequiredStringList(catalog.getPrinciplesJson(), "principles_json", catalog.getCharacterId());
        List<String> principleDescriptions = readRequiredStringList(
                catalog.getPrincipleDescriptionsJson(),
                "principle_descriptions_json",
                catalog.getCharacterId());
        List<String> strategies = readRequiredStringList(catalog.getStrategiesJson(), "strategies_json", catalog.getCharacterId());

        return OnboardingSurveyResultDTO.builder()
                .id((long) catalog.getCharacterId())
                .characterId((long) catalog.getCharacterId())
                .characterName(catalog.getCanonicalName())
                .characterEmoji(profile.emoji())
                .characterColor(profile.color())
                .type(catalog.getCanonicalName())
                .title(catalog.getCanonicalName())
                .description(catalog.getCardSummary())
                .imageUrl(catalog.getCharacterImageResource())
                .levelLabel(catalog.getLevelLabel())
                .investmentLevel(resolvedLevel)
                .interestSector(resolvedSector)
                .investmentType(catalog.getInvestmentType())
                .probabilityLabel(catalog.getAnalysisSubtitle())
                .strategyTitle(STRATEGY_SECTION_TITLE)
                .strategyLabel(strategies.isEmpty() ? null : strategies.get(0))
                .traits(traits)
                .recommendedStrategies(strategies)
                .personalPrinciples(principles)
                .features(List.of(
                        section(catalog.getAnalysisTitle(), traits, traitDescriptions),
                        section(STRATEGY_SECTION_TITLE, strategies)
                ))
                .guides(List.of(section(PRINCIPLES_SECTION_TITLE, principles, principleDescriptions)))
                .build();
    }

    private SurveyResultSectionDTO section(String title, List<String> items) {
        return SurveyResultSectionDTO.builder()
                .title(title)
                .items(items.stream()
                        .map(item -> SurveyResultDetailItemDTO.builder()
                                .name(item)
                                .description("")
                                .build())
                        .toList())
                .build();
    }

    private SurveyResultSectionDTO section(String title, List<String> itemNames, List<String> itemDescriptions) {
        if (itemNames.size() != itemDescriptions.size()) {
            throw new IllegalStateException("Onboarding result catalog section has mismatched item and description counts: " + title);
        }
        return SurveyResultSectionDTO.builder()
                .title(title)
                .items(IntStream.range(0, itemNames.size())
                        .mapToObj(index -> {
                            String itemName = itemNames.get(index);
                            return SurveyResultDetailItemDTO.builder()
                                    .name(itemName)
                                    .description(itemDescriptions.get(index))
                                    .build();
                        })
                        .toList())
                .build();
    }

    private OnboardingResultCatalog loadCatalog(int characterId) {
        return catalogRepository.findByCharacterIdAndActiveTrue(characterId)
                .orElseThrow(() -> new IllegalStateException("Missing onboarding result catalog for character " + characterId));
    }

    private boolean matchesCharacterName(OnboardingResultCatalog catalog, String characterName) {
        String normalizedName = normalizeCharacterName(characterName);
        if (normalizedName.isBlank()) {
            return false;
        }
        if (normalizeCharacterName(catalog.getCanonicalName()).equals(normalizedName)) {
            return true;
        }
        return readStringList(catalog.getLegacyAliasesJson(), "legacy_aliases_json", catalog.getCharacterId()).stream()
                .map(OnboardingResultProvider::normalizeCharacterName)
                .anyMatch(normalizedName::equals);
    }

    private List<String> readRequiredStringList(String json, String fieldName, int characterId) {
        List<String> values = readStringList(json, fieldName, characterId);
        if (values.isEmpty()) {
            throw new IllegalStateException("Missing onboarding result catalog field " + fieldName + " for character " + characterId);
        }
        return values;
    }

    private List<String> readStringList(String json, String fieldName, int characterId) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to parse onboarding result catalog field " + fieldName + " for character " + characterId,
                    exception);
        }
    }

    private static String normalizeCharacterName(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }

    private Map<Integer, CharacterProfile> createCharacters() {
        Map<Integer, CharacterProfile> profiles = new LinkedHashMap<>();

        profiles.put(1, new CharacterProfile(
                1,
                "조심스러운 거북이",
                "🐢",
                "#4A90E2",
                1.0, 3.0, 1.0, 1.0,
                "원금 보존을 가장 중요하게 생각하는 안정 우선 장기 분산형 투자자",
                List.of(
                        "손실에 대한 스트레스가 큰 편이라 큰 변동보다 예측 가능한 흐름을 선호한다.",
                        "투자에서 가장 중요한 기준은 많이 버는 것보다 크게 잃지 않는 것이다.",
                        "복잡한 매매 전략보다 적립식, 분산, 장기보유 같은 구조에 강점이 있다.",
                        "수익률이 아주 폭발적이지 않더라도 꾸준하고 안정적인 복리 흐름을 선호한다."
                ),
                List.of(
                        "인덱스 ETF, 대형 우량주, 배당주 중심의 코어 자산을 우선 구축한다.",
                        "적립식 투자 비중을 높여 진입 타이밍 스트레스를 줄인다.",
                        "분기 또는 반기 단위로만 리밸런싱하며 일간 변동 대응은 최소화한다.",
                        "섹터 투자에 관심이 있어도 전체 자산의 일부만 위성 포지션으로 가져간다."
                ),
                List.of(
                        "한 종목 또는 한 테마에 총자산의 20% 이상 넣지 않는다.",
                        "이해하지 못한 상품은 수익률이 좋아 보여도 바로 투자하지 않는다.",
                        "시장 급락 시 감정적으로 팔기 전에 내 원칙과 비중부터 다시 점검한다.",
                        "수익 기회보다 손실 관리가 먼저라는 기준을 유지한다."
                )
        ));

        profiles.put(2, new CharacterProfile(
                2,
                "균형 잡힌 판다",
                "🐼",
                "#7ED321",
                2.0, 2.6, 1.7, 2.0,
                "안정성과 성장 가능성을 함께 가져가려는 균형형 코어-위성 투자자",
                List.of(
                        "손실 관리와 성장 기회를 동시에 챙기려는 성향이 강하다.",
                        "숫자와 스토리 둘 다 보며 판단하려는 편이다.",
                        "큰 승부보다 꾸준히 개선되는 포트폴리오 운영에 적합하다.",
                        "장기적으로 안정적인 실행력을 내기 쉬운 타입이다."
                ),
                List.of(
                        "코어 70, 위성 30 구조로 포트폴리오를 설계한다.",
                        "코어는 인덱스 ETF, 대형 우량주, 배당주로 구성한다.",
                        "위성은 관심 섹터나 성장주로 채운다.",
                        "월 1회 또는 분기 1회 포트폴리오 점검 기준을 정한다."
                ),
                List.of(
                        "내 포트폴리오의 중심은 언제나 코어 자산이 되도록 유지한다.",
                        "새 종목을 살 때는 찬성 근거와 반대 근거를 동시에 적어본다.",
                        "관심 섹터는 좋아도 전체 자산의 일부로만 관리한다.",
                        "비중이 과도하게 쏠리지 않았는지 주기적으로 확인한다."
                )
        ));

        profiles.put(3, new CharacterProfile(
                3,
                "호기심 많은 치타",
                "🐆",
                "#F5A623",
                3.0, 1.6, 2.3, 2.0,
                "트렌드와 기회를 빠르게 포착하는 공격 성향의 단기~중기 기회 추구형 투자자",
                List.of(
                        "뜨거운 흐름을 감지하는 속도가 빠르다.",
                        "기회를 놓치기 싫어하는 편이라 실행 속도가 빠르다.",
                        "기준이 없으면 추격매수와 충동매매로 이어질 수 있다.",
                        "규칙이 생기면 실력이 빠르게 성장하는 타입이다."
                ),
                List.of(
                        "테마 스윙 투자나 이벤트 드리븐 전략을 사용한다.",
                        "진입과 청산 기준을 먼저 정한다.",
                        "포지션 크기를 작게 나눠 여러 번 진입한다.",
                        "매매일지를 통해 규칙 준수 여부를 복기한다."
                ),
                List.of(
                        "진입 전에 손절가와 익절 기준을 먼저 정한다.",
                        "테마주와 고변동 종목은 전체 자산의 일부만 사용한다.",
                        "뉴스가 뜬 뒤 따라가기 전에 가격 반영 여부를 확인한다.",
                        "손실 한도를 넘으면 즉시 속도를 줄인다."
                )
        ));

        profiles.put(4, new CharacterProfile(
                4,
                "전략 짜는 올빼미",
                "🦉",
                "#9013FE",
                2.4, 2.2, 1.0, 3.0,
                "숫자와 근거를 바탕으로 저평가 기회를 찾는 분석 중심 가치 전략형 투자자",
                List.of(
                        "감보다 근거를 우선한다.",
                        "숫자로 설명되지 않는 투자에는 쉽게 들어가지 않는다.",
                        "매수 전에 오래 고민하지만 이해한 투자에는 확신이 높은 편이다.",
                        "투자 근거가 명확할수록 심리적으로 흔들리지 않는다."
                ),
                List.of(
                        "종목 선정 전 1페이지 리서치 노트를 작성한다.",
                        "밸류에이션이 매력적인 구간에서 분할매수한다.",
                        "실적 발표, 가이던스, 산업 규제 변화를 핵심 체크 포인트로 관리한다.",
                        "반증 조건을 미리 정해 틀렸을 때 나오는 기준을 만든다."
                ),
                List.of(
                        "설명할 수 없는 사업모델에는 투자하지 않는다.",
                        "매수 전에 핵심 가설과 반증 조건을 반드시 글로 남긴다.",
                        "싼 가격만 보지 않고 질 좋은 사업인지 함께 본다.",
                        "감정적 대응보다 가설 검증을 우선한다."
                )
        ));

        profiles.put(5, new CharacterProfile(
                5,
                "감각형 돌고래",
                "🐬",
                "#50E3C2",
                2.4, 2.2, 2.0, 2.6,
                "산업 변화와 제품 스토리를 바탕으로 성장성을 읽는 중위험 성장 추적형 투자자",
                List.of(
                        "앞으로 커질 시장과 변화를 만드는 기업에 자연스럽게 눈길이 간다.",
                        "혁신성과 스토리에 민감하다.",
                        "좋은 이야기와 좋은 투자 기회는 다를 수 있어 검증 습관이 중요하다.",
                        "성장성이 살아 있는 동안 비교적 긴 호흡으로 보유할 수 있다."
                ),
                List.of(
                        "성장주 또는 혁신 섹터를 보되 매출 성장률과 밸류에이션을 함께 확인한다.",
                        "좋은 이야기가 실제 실적과 지표로 연결되는지 점검한다.",
                        "대표 종목과 보조 종목으로 나눠 비중을 배분한다.",
                        "실적 발표, 가이던스, 시장 점유율 변화를 체크 포인트로 둔다."
                ),
                List.of(
                        "스토리만으로 사지 않고 숫자로 한 번 더 검증한다.",
                        "같은 섹터 안에서도 비슷한 종목을 과도하게 겹쳐 담지 않는다.",
                        "시장이 좋아하는 이야기와 실제 사업 성과를 구분한다.",
                        "과열 구간일수록 비중을 더 신중하게 관리한다."
                )
        ));

        profiles.put(6, new CharacterProfile(
                6,
                "파도타는 서퍼",
                "🏄‍♂️",
                "#F8E71C",
                3.0, 1.4, 3.0, 3.0,
                "차트와 수급 흐름을 바탕으로 타이밍을 잡는 고관여 단기 매매형 투자자",
                List.of(
                        "짧은 흐름과 타이밍에서 기회를 찾는 성향이 강하다.",
                        "빠르게 대응하는 능력이 장점이다.",
                        "동시에 심리 흔들림도 크게 받기 쉽다.",
                        "손실 제한 능력이 성과를 좌우한다."
                ),
                List.of(
                        "진입 기준, 손절 기준, 익절 기준을 명확히 둔다.",
                        "하루 손실 한도와 연속 손실 중단 규칙을 만든다.",
                        "매매 전 시나리오를 짧게라도 적는다.",
                        "장중 소음에 휘둘리지 않도록 사용하는 지표와 시간 프레임을 고정한다."
                ),
                List.of(
                        "진입 전에 손절가를 정하지 않은 매매는 하지 않는다.",
                        "하루 손실 한도를 넘으면 그날 매매를 중단한다.",
                        "연속 손실이 나면 패턴부터 복기한다.",
                        "수익보다 생존이 우선이라는 기준을 잊지 않는다."
                )
        ));

        profiles.put(7, new CharacterProfile(
                7,
                "성실한 농부",
                "🌾",
                "#4A4A4A",
                1.7, 3.0, 1.0, 2.0,
                "규칙적으로 모으고 오래 운영하는 적립식 장기 복리형 투자자",
                List.of(
                        "타이밍보다 꾸준함을 믿는 편이다.",
                        "적립식 투자, 장기 보유, 재투자 구조와 궁합이 좋다.",
                        "감정적 매매보다 일정과 규칙을 지키는 실행력이 강점이다.",
                        "시간과 습관으로 성과를 쌓는 타입이다."
                ),
                List.of(
                        "월간 자동매수와 정기 리밸런싱을 기본 전략으로 둔다.",
                        "ETF, 우량주, 배당주 중심으로 구성한다.",
                        "장기 목표를 3년, 5년, 10년 단위로 수치화한다.",
                        "배당금이나 추가 현금을 재투자하는 구조를 우선 고려한다."
                ),
                List.of(
                        "시장 상황과 무관하게 정한 적립일은 지킨다.",
                        "장기 보유 자산도 최소 분기 1회는 점검한다.",
                        "배당이나 현금 흐름은 소비보다 재투자를 우선한다.",
                        "복리는 속도가 아니라 지속성에서 나온다는 점을 기억한다."
                )
        ));

        profiles.put(8, new CharacterProfile(
                8,
                "호기심 많은 연구자",
                "🔬",
                "#D0021B",
                2.2, 2.8, 1.6, 3.0,
                "특정 산업과 기업을 깊이 파고들며 확신을 만들어 가는 고관여 리서치 기반 중장기 투자자",
                List.of(
                        "산업 구조와 기업 차별점을 이해하는 데서 재미를 느낀다.",
                        "숫자와 스토리를 함께 보되 왜 그런가를 깊게 파고든다.",
                        "리서치를 할수록 확신과 보유 지속력이 높아진다.",
                        "정보가 많아질수록 핵심 변수를 구분하는 능력이 중요하다."
                ),
                List.of(
                        "관심 섹터를 정하면 핵심 키워드, 대표 기업, 체크 지표를 먼저 정리한다.",
                        "기업 리서치 노트에 투자 가설과 반증 조건을 구조화한다.",
                        "포트폴리오를 늘릴 것, 유지할 것, 줄일 것, 버릴 것으로 재분류한다.",
                        "산업 변화와 기업 경쟁력 변화를 핵심 판단 기준으로 둔다."
                ),
                List.of(
                        "새 섹터를 볼 때는 핵심 키워드와 대표 기업, 체크 지표를 먼저 정리한다.",
                        "남의 의견을 보기 전에 내 가설을 먼저 문장으로 써본다.",
                        "공부가 많이 됐더라도 분산 원칙은 쉽게 포기하지 않는다.",
                        "반증 조건을 항상 함께 관리한다."
                )
        ));

        return profiles;
    }

    private record CharacterProfile(
            int id,
            String name,
            String emoji,
            String color,
            double centroidRisk,
            double centroidTerm,
            double centroidStyle,
            double centroidInvolvement,
            String investmentType,
            List<String> traits,
            List<String> recommendedStrategies,
            List<String> personalPrinciples
    ) {
    }
}
