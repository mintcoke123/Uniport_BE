package com.uniport.service;

import com.uniport.dto.LearningCategoryDTO;
import com.uniport.dto.LearningCourseDetailResponseDTO;
import com.uniport.dto.LearningDayContentResponseDTO;
import com.uniport.dto.LearningDayStepDTO;
import com.uniport.dto.LearningKeyConceptDTO;
import com.uniport.dto.LearningProgressDTO;
import com.uniport.dto.LearningStepOptionDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LearningMockDataProvider {

    private final List<LearningCategoryDTO> categories = List.of(
            LearningCategoryDTO.builder().code("MAIN").label("메인 코스").build(),
            LearningCategoryDTO.builder().code("MINI").label("미니 코스").build(),
            LearningCategoryDTO.builder().code("ADVANCED").label("심화 코스").build()
    );

    private final Map<Long, LearningCourseCatalog> catalogs = Map.of(
            1L, new LearningCourseCatalog(
                    1L,
                    "MAIN",
                    "입문 30일 코스",
                    "투자의 기초를 탄탄하게 다지는 첫걸음",
                    "https://example.com/course-beginner.png",
                    false,
                    List.of(
                            new LearningDayCatalog(
                                    1,
                                    "CHAPTER 01",
                                    "주식의 첫걸음",
                                    "투자의 기본 개념과 시장의 구조를 이해합니다.",
                                    "https://example.com/day-1.png",
                                    List.of(
                                            LearningKeyConceptDTO.builder().title("주식이란 무엇인가").build(),
                                            LearningKeyConceptDTO.builder().title("시장가와 지정가의 차이").build(),
                                            LearningKeyConceptDTO.builder().title("주문이 체결되는 방식").build()
                                    ),
                                    List.of(
                                            LearningDayStepDTO.builder()
                                                    .id(1001L).order(1).type("THEORY").chapter("CHAPTER 01")
                                                    .title("주식의 기본 구조")
                                                    .description("주식은 기업의 소유권 일부를 의미합니다.")
                                                    .imageUrl("https://example.com/learning/theory-stock.png")
                                                    .build(),
                                            LearningDayStepDTO.builder()
                                                    .id(1002L).order(2).type("QUIZ").chapter("CHAPTER 01")
                                                    .title("오늘의 퀴즈")
                                                    .question("주식이 의미하는 것으로 가장 적절한 것은?")
                                                    .options(List.of(
                                                            new LearningStepOptionDTO(1L, "기업의 소유권 일부"),
                                                            new LearningStepOptionDTO(2L, "기업의 부채"),
                                                            new LearningStepOptionDTO(3L, "예금 상품")
                                                    ))
                                                    .build(),
                                            LearningDayStepDTO.builder()
                                                    .id(1003L).order(3).type("GAME")
                                                    .title("상승 신호 찾기")
                                                    .description("더 긍정적인 의미에 가까운 선택지를 고르세요.")
                                                    .options(List.of(
                                                            new LearningStepOptionDTO(1L, "매수 관심 증가"),
                                                            new LearningStepOptionDTO(2L, "거래 중단")
                                                    ))
                                                    .build()
                                    )
                            ),
                            new LearningDayCatalog(
                                    2,
                                    "CHAPTER 02",
                                    "캔들스틱 차트의 이해",
                                    "캔들스틱 차트는 특정 기간 동안의 가격 움직임을 시각적으로 보여줍니다.",
                                    "https://example.com/detail.png",
                                    List.of(
                                            LearningKeyConceptDTO.builder().title("시가, 종가, 고가, 저가의 정의").build(),
                                            LearningKeyConceptDTO.builder().title("몸통(Body)과 꼬리(Shadow)의 의미").build(),
                                            LearningKeyConceptDTO.builder().title("추세 반전 신호 확인하기").build()
                                    ),
                                    List.of(
                                            LearningDayStepDTO.builder()
                                                    .id(101L).order(1).type("THEORY").chapter("CHAPTER 02")
                                                    .title("캔들스틱의 기본 구조")
                                                    .description("캔들스틱은 시가, 종가, 고가, 저가를 하나의 막대로 표현합니다.")
                                                    .imageUrl("https://example.com/candle.png")
                                                    .build(),
                                            LearningDayStepDTO.builder()
                                                    .id(102L).order(2).type("QUIZ").chapter("CHAPTER 02")
                                                    .title("오늘의 퀴즈")
                                                    .question("다음 중 캔들스틱에서 몸통이 나타내는 것은?")
                                                    .options(List.of(
                                                            new LearningStepOptionDTO(1L, "고가와 저가의 차이"),
                                                            new LearningStepOptionDTO(2L, "시가와 종가의 차이")
                                                    ))
                                                    .build(),
                                            LearningDayStepDTO.builder()
                                                    .id(103L).order(3).type("GAME")
                                                    .title("다음 캔들 예측하기")
                                                    .description("다음 캔들을 예측하세요")
                                                    .options(List.of(
                                                            new LearningStepOptionDTO(1L, "양봉"),
                                                            new LearningStepOptionDTO(2L, "음봉")
                                                    ))
                                                    .build()
                                    )
                            ),
                            new LearningDayCatalog(
                                    3,
                                    "CHAPTER 03",
                                    "거래량 읽기",
                                    "거래량과 가격의 관계를 통해 시장 심리를 해석합니다.",
                                    "https://example.com/day-3.png",
                                    List.of(
                                            LearningKeyConceptDTO.builder().title("거래량 증가의 의미").build(),
                                            LearningKeyConceptDTO.builder().title("가격과 거래량의 관계").build(),
                                            LearningKeyConceptDTO.builder().title("돌파 신호 해석").build()
                                    ),
                                    List.of(
                                            LearningDayStepDTO.builder()
                                                    .id(301L).order(1).type("THEORY").chapter("CHAPTER 03")
                                                    .title("거래량의 기본")
                                                    .description("거래량은 일정 시간 동안 얼마나 많이 거래됐는지를 의미합니다.")
                                                    .imageUrl("https://example.com/volume.png")
                                                    .build(),
                                            LearningDayStepDTO.builder()
                                                    .id(302L).order(2).type("QUIZ").chapter("CHAPTER 03")
                                                    .title("오늘의 퀴즈")
                                                    .question("상승과 함께 거래량이 증가하면 보통 어떤 의미로 해석하나요?")
                                                    .options(List.of(
                                                            new LearningStepOptionDTO(1L, "상승 신뢰도 증가"),
                                                            new LearningStepOptionDTO(2L, "하락 전환 확정")
                                                    ))
                                                    .build()
                                    )
                            )
                    )
            ),
            2L, new LearningCourseCatalog(
                    2L,
                    "MAIN",
                    "초급 30일 코스",
                    "실전 감각을 익히는 심화 과정",
                    "https://example.com/course-basic.png",
                    false,
                    List.of(
                            new LearningDayCatalog(
                                    1,
                                    "CHAPTER 01",
                                    "보조지표 기초",
                                    "이동평균선과 RSI의 기본 개념을 배웁니다.",
                                    "https://example.com/basic-day-1.png",
                                    List.of(
                                            LearningKeyConceptDTO.builder().title("이동평균선의 역할").build(),
                                            LearningKeyConceptDTO.builder().title("RSI 과매수/과매도").build()
                                    ),
                                    List.of(
                                            LearningDayStepDTO.builder()
                                                    .id(2001L).order(1).type("THEORY").chapter("CHAPTER 01")
                                                    .title("이동평균선이란")
                                                    .description("이동평균선은 일정 기간 가격의 평균입니다.")
                                                    .imageUrl("https://example.com/ma.png")
                                                    .build(),
                                            LearningDayStepDTO.builder()
                                                    .id(2002L).order(2).type("QUIZ").chapter("CHAPTER 01")
                                                    .title("오늘의 퀴즈")
                                                    .question("이동평균선은 주로 무엇을 파악하는 데 도움을 주나요?")
                                                    .options(List.of(
                                                            new LearningStepOptionDTO(1L, "추세"),
                                                            new LearningStepOptionDTO(2L, "배당금")
                                                    ))
                                                    .build()
                                    )
                            )
                    )
            ),
            4L, new LearningCourseCatalog(
                    4L,
                    "MINI",
                    "매매 기초 코스",
                    "짧고 굵게 핵심만 익히는 미니 코스",
                    "https://example.com/course-mini-trading.png",
                    false,
                    List.of(
                            new LearningDayCatalog(
                                    1,
                                    "MINI 01",
                                    "트레이딩의 이해",
                                    "트레이딩의 기본 개념과 용어를 빠르게 익힙니다.",
                                    "https://example.com/mini-day-1.png",
                                    List.of(
                                            LearningKeyConceptDTO.builder().title("매수와 매도의 차이").build(),
                                            LearningKeyConceptDTO.builder().title("체결 가격 이해하기").build()
                                    ),
                                    List.of(
                                            LearningDayStepDTO.builder()
                                                    .id(4001L).order(1).type("THEORY").chapter("MINI 01")
                                                    .title("트레이딩의 이해")
                                                    .description("짧은 호흡으로 매매할 때 꼭 알아야 할 기본 개념을 정리합니다.")
                                                    .imageUrl("https://example.com/mini-trading.png")
                                                    .build(),
                                            LearningDayStepDTO.builder()
                                                    .id(4002L).order(2).type("QUIZ").chapter("MINI 01")
                                                    .title("오늘의 퀴즈")
                                                    .question("매수는 어떤 의미일까요?")
                                                    .options(List.of(
                                                            new LearningStepOptionDTO(1L, "주식을 사는 것"),
                                                            new LearningStepOptionDTO(2L, "주식을 파는 것")
                                                    ))
                                                    .build()
                                    )
                            )
                    )
            ),
            5L, new LearningCourseCatalog(
                    5L,
                    "MINI",
                    "뉴스 읽기 코스",
                    "뉴스와 공시를 해석하는 기초 감각을 익힙니다.",
                    "https://example.com/course-mini-news.png",
                    false,
                    List.of(
                            new LearningDayCatalog(
                                    1,
                                    "MINI 02",
                                    "뉴스 읽는 법",
                                    "뉴스 제목만 보고 판단하지 않는 습관을 배웁니다.",
                                    "https://example.com/mini-news.png",
                                    List.of(
                                            LearningKeyConceptDTO.builder().title("제목과 본문의 차이").build(),
                                            LearningKeyConceptDTO.builder().title("출처 확인하기").build()
                                    ),
                                    List.of(
                                            LearningDayStepDTO.builder()
                                                    .id(5001L).order(1).type("THEORY").chapter("MINI 02")
                                                    .title("뉴스 읽는 법")
                                                    .description("헤드라인만 보고 투자 판단을 내리지 않도록 기사 구조를 살펴봅니다.")
                                                    .imageUrl("https://example.com/mini-news-reading.png")
                                                    .build(),
                                            LearningDayStepDTO.builder()
                                                    .id(5002L).order(2).type("QUIZ").chapter("MINI 02")
                                                    .title("오늘의 퀴즈")
                                                    .question("뉴스를 볼 때 가장 먼저 확인할 것은?")
                                                    .options(List.of(
                                                            new LearningStepOptionDTO(1L, "출처와 날짜"),
                                                            new LearningStepOptionDTO(2L, "댓글 수")
                                                    ))
                                                    .build()
                                    )
                            )
                    )
            ),
            3L, new LearningCourseCatalog(
                    3L,
                    "ADVANCED",
                    "중급 30일 코스",
                    "전문가로 거듭나는 마스터 클래스",
                    "https://example.com/course-advanced.png",
                    true,
                    List.of(
                            new LearningDayCatalog(
                                    1,
                                    "CHAPTER 01",
                                    "포트폴리오 전략",
                                    "분산과 리밸런싱 전략을 학습합니다.",
                                    "https://example.com/advanced-day-1.png",
                                    List.of(
                                            LearningKeyConceptDTO.builder().title("자산 배분").build()
                                    ),
                                    List.of(
                                            LearningDayStepDTO.builder()
                                                    .id(3001L).order(1).type("THEORY").chapter("CHAPTER 01")
                                                    .title("분산 투자의 원리")
                                                    .description("여러 자산에 나눠 투자하면 리스크를 줄일 수 있습니다.")
                                                    .imageUrl("https://example.com/diversification.png")
                                                    .build()
                                    )
                            )
                    )
            )
    );

    public List<LearningCategoryDTO> getCategories() {
        return categories;
    }

    public List<LearningCourseCatalog> getCoursesByCategory(String category) {
        String resolvedCategory = category == null || category.isBlank() ? "MAIN" : category.trim().toUpperCase();
        return catalogs.values().stream()
                .filter(course -> course.category().equals(resolvedCategory))
                .sorted((a, b) -> Long.compare(a.id(), b.id()))
                .toList();
    }

    public LearningCourseCatalog getCourse(long courseId) {
        LearningCourseCatalog catalog = catalogs.get(courseId);
        if (catalog == null) {
            throw new IllegalArgumentException("Learning course not found: " + courseId);
        }
        return catalog;
    }

    public LearningDayCatalog getDay(long courseId, int day) {
        return getCourse(courseId).days().stream()
                .filter(d -> d.day() == day)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Learning day not found: " + day));
    }

    public LearningStepLookup findStep(long stepId) {
        for (LearningCourseCatalog course : catalogs.values()) {
            for (LearningDayCatalog day : course.days()) {
                for (LearningDayStepDTO step : day.steps()) {
                    if (step.getId() == stepId) {
                        return new LearningStepLookup(course, day, step);
                    }
                }
            }
        }
        throw new IllegalArgumentException("Learning step not found: " + stepId);
    }

    public LearningCourseDetailResponseDTO toCourseDetail(LearningCourseCatalog course, int currentDay) {
        LearningDayCatalog day = getDay(course.id(), currentDay);
        return LearningCourseDetailResponseDTO.builder()
                .id(course.id())
                .day(day.day())
                .chapter(day.chapter())
                .title(day.title())
                .description(day.description())
                .thumbnailUrl(day.thumbnailUrl())
                .keyConcepts(day.keyConcepts())
                .progress(new LearningProgressDTO(currentDay, course.days().size()))
                .status("IN_PROGRESS")
                .build();
    }

    public LearningDayContentResponseDTO toDayContent(LearningCourseCatalog course, int currentDay, LearningDayCatalog day) {
        return LearningDayContentResponseDTO.builder()
                .courseId(course.id())
                .day(day.day())
                .title(day.title())
                .progress(new LearningProgressDTO(currentDay, course.days().size()))
                .currentStepOrder(1)
                .totalSteps(day.steps().size())
                .steps(day.steps())
                .build();
    }

    public record LearningCourseCatalog(
            Long id,
            String category,
            String title,
            String description,
            String thumbnailUrl,
            boolean locked,
            List<LearningDayCatalog> days
    ) {
    }

    public record LearningDayCatalog(
            int day,
            String chapter,
            String title,
            String description,
            String thumbnailUrl,
            List<LearningKeyConceptDTO> keyConcepts,
            List<LearningDayStepDTO> steps
    ) {
    }

    public record LearningStepLookup(
            LearningCourseCatalog course,
            LearningDayCatalog day,
            LearningDayStepDTO step
    ) {
    }
}
