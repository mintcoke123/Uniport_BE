package com.uniport.service;

import com.uniport.entity.EducationCardEntity;
import com.uniport.entity.EducationOverviewEntity;
import com.uniport.entity.EducationQuizEntity;
import com.uniport.entity.LearningUserStateEntity;
import com.uniport.entity.User;
import com.uniport.repository.EducationCardRepository;
import com.uniport.repository.EducationOverviewRepository;
import com.uniport.repository.EducationQuizRepository;
import com.uniport.repository.LearningUserStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationV1ServiceTest {

    @Mock
    private LearningUserStateRepository learningUserStateRepository;

    @Mock
    private EducationOverviewRepository educationOverviewRepository;

    @Mock
    private EducationCardRepository educationCardRepository;

    @Mock
    private EducationQuizRepository educationQuizRepository;

    private EducationV1Service service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new EducationV1Service(
                learningUserStateRepository,
                educationOverviewRepository,
                educationCardRepository,
                educationQuizRepository);
        user = User.builder().id(1L).studentId("20260001").password("pw").nickname("kmp").build();
    }

    @Test
    void roadmapMapsDays27To30OnlyToTheUsersTwoSelectedSectors() {
        when(learningUserStateRepository.findById(1L)).thenReturn(Optional.of(stateWithSelectedSectors()));
        when(educationOverviewRepository.findByTrackAndSectorOrderByDayNumberAsc(eq("intro_core"), isNull()))
                .thenReturn(coreOverviews("intro_core"));
        when(educationCardRepository.findByTrackAndSectorAndDayNumberOrderBySourceIdxAsc(anyString(), any(), anyInt()))
                .thenReturn(List.of());
        when(educationQuizRepository.findByTrackAndSectorAndDayNumberOrderByQuizNumberAsc(anyString(), any(), anyInt()))
                .thenReturn(List.of());

        Map<String, Object> response = service.getCourseRoadmap(user, "intro");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> days = (List<Map<String, Object>>) response.get("days");
        assertEquals(30, days.size());
        assertEquals("ai_semiconductor", days.get(26).get("sector_id"));
        assertEquals("ai_semiconductor", days.get(27).get("sector_id"));
        assertEquals("quantum_computer", days.get(28).get("sector_id"));
        assertEquals("quantum_computer", days.get(29).get("sector_id"));
        assertFalse(days.stream().anyMatch(day -> "battery".equals(day.get("sector_id"))));
    }

    @Test
    void courseDayBuildsKmpFlowWithTemplateTypesAndDoesNotExposeCorrectQuizChoice() {
        when(learningUserStateRepository.findById(1L)).thenReturn(Optional.empty());
        when(educationOverviewRepository.findByTrackAndSectorAndDayNumber(eq("intro_core"), isNull(), eq(1)))
                .thenReturn(Optional.of(overview("intro_core", null, 1, "캔들스틱 차트의 이해")));
        when(educationCardRepository.findByTrackAndSectorAndDayNumberOrderBySourceIdxAsc(eq("intro_core"), isNull(), eq(1)))
                .thenReturn(List.of(
                        card(0, "placeholder", "인플레이션과 내 돈", "{}"),
                        card(1, "image", "캔들스틱 차트", "{\"alt\":\"시가 종가 고가 저가\"}")
                ));
        when(educationQuizRepository.findByTrackAndSectorAndDayNumberOrderByQuizNumberAsc(eq("intro_core"), isNull(), eq(1)))
                .thenReturn(List.of(quiz(1)));

        Map<String, Object> response = service.getCourseDay(user, "intro", 1);

        assertEquals("2026-05-09.1", response.get("content_version"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> flow = (List<Map<String, Object>>) response.get("flow");
        assertEquals("day_overview", flow.get(0).get("template_type"));
        assertEquals("content_text", flow.get(1).get("template_type"));
        assertEquals("content_visual", flow.get(2).get("template_type"));
        assertEquals("quiz_single_choice", flow.get(3).get("template_type"));
        assertFalse(flow.get(3).containsKey("correct_choice_id"));
    }

    @Test
    void dayCompletionIsIdempotentAndDoesNotGrantRewardTwice() {
        AtomicReference<LearningUserStateEntity> storedState = new AtomicReference<>();
        when(learningUserStateRepository.findById(1L)).thenAnswer(invocation -> Optional.ofNullable(storedState.get()));
        when(learningUserStateRepository.save(any(LearningUserStateEntity.class))).thenAnswer(invocation -> {
            LearningUserStateEntity entity = invocation.getArgument(0);
            storedState.set(entity);
            return entity;
        });

        Map<String, Object> first = service.completeCourseDay(user, "intro", 1, Map.of("last_step_id", "intro_d1_card_1"));
        Map<String, Object> second = service.completeCourseDay(user, "intro", 1, Map.of("last_step_id", "intro_d1_card_1"));

        @SuppressWarnings("unchecked")
        Map<String, Object> firstReward = (Map<String, Object>) first.get("reward");
        @SuppressWarnings("unchecked")
        Map<String, Object> secondReward = (Map<String, Object>) second.get("reward");
        assertEquals(500, firstReward.get("point"));
        assertEquals(500, firstReward.get("total_point"));
        assertEquals(0, secondReward.get("point"));
        assertEquals(500, secondReward.get("total_point"));
    }

    private LearningUserStateEntity stateWithSelectedSectors() {
        return LearningUserStateEntity.builder()
                .userId(1L)
                .level(0)
                .point(3000)
                .streakDays(0)
                .currentDayByCourseJson("{}")
                .completedDaysByCourseJson("{}")
                .submittedStepIdsJson("[]")
                .educationCurrentDayJson("{\"intro\":27}")
                .educationCompletedDaysJson("{}")
                .educationQuizAnswersJson("{}")
                .educationCardProgressJson("{}")
                .educationSectorSelectionsJson("{\"intro\":[\"ai_semiconductor\",\"quantum_computer\"]}")
                .build();
    }

    private List<EducationOverviewEntity> coreOverviews(String track) {
        List<EducationOverviewEntity> overviews = new ArrayList<>();
        for (int day = 1; day <= 30; day += 1) {
            overviews.add(overview(track, null, day, "Day " + day));
        }
        return overviews;
    }

    private EducationOverviewEntity overview(String track, String sector, int day, String title) {
        return EducationOverviewEntity.builder()
                .track(track)
                .sector(sector)
                .dayNumber(day)
                .levelLabel("입문")
                .dayLabel("Day " + day)
                .title(title)
                .summary1("요약 1")
                .summary2("요약 2")
                .keyPointsJson("[\"핵심 1\",\"핵심 2\"]")
                .ctaLabel("계속")
                .build();
    }

    private EducationCardEntity card(int idx, String imageType, String title, String visualJson) {
        return EducationCardEntity.builder()
                .sourceIdx(idx)
                .assetId("asset-" + idx)
                .sheet("입문_카드_FINAL")
                .track("intro_core")
                .dayNumber(1)
                .section("섹션")
                .cardNumber((idx + 1) + "/2")
                .title(title)
                .text("본문")
                .imageType(imageType)
                .visualJson(visualJson)
                .build();
    }

    private EducationQuizEntity quiz(int quizNumber) {
        return EducationQuizEntity.builder()
                .sourceMode("daily")
                .track("intro_core")
                .dayNumber(1)
                .quizNumber(quizNumber)
                .quizType("MCQ")
                .question("질문")
                .optionsJson("[\"시가\",\"종가\",\"고가\",\"저가\"]")
                .answerIndex(1)
                .topic("캔들")
                .area("차트")
                .intent("해설")
                .build();
    }
}
