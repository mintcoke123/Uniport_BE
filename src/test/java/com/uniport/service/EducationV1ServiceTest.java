package com.uniport.service;

import com.uniport.entity.EducationCardEntity;
import com.uniport.entity.EducationOverviewEntity;
import com.uniport.entity.EducationQuizEntity;
import com.uniport.entity.LearningUserStateEntity;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.EducationCardRepository;
import com.uniport.repository.EducationOverviewRepository;
import com.uniport.repository.EducationQuizRepository;
import com.uniport.repository.LearningUserStateRepository;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void courseListCarriesFigmaCardStateAndButtonContract() {
        when(learningUserStateRepository.findById(1L)).thenReturn(Optional.of(LearningUserStateEntity.builder()
                .userId(1L)
                .level(0)
                .point(3000)
                .streakDays(0)
                .currentDayByCourseJson("{}")
                .completedDaysByCourseJson("{}")
                .submittedStepIdsJson("[]")
                .educationCurrentDayJson("{\"advanced\":2}")
                .educationCompletedDaysJson("{\"intro\":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30],\"advanced\":[1]}")
                .educationQuizAnswersJson("{}")
                .educationCardProgressJson("{}")
                .educationSectorSelectionsJson("{}")
                .build()));

        Map<String, Object> response = service.getCourses(user, "main");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) response.get("courses");
        assertEquals(2, courses.size());
        assertEquals("completed", courses.get(0).get("status"));
        assertEquals("학습 완료됨", courses.get(0).get("status_label"));
        assertEquals("Day 30 / 30", courses.get(0).get("progress_label"));
        assertEquals(false, courses.get(0).get("is_locked"));
        assertEquals("복습하기", courses.get(0).get("action_label"));

        assertEquals("in_progress", courses.get(1).get("status"));
        assertEquals("현재 이수중", courses.get(1).get("status_label"));
        assertEquals("Day 02 / 30", courses.get(1).get("progress_label"));
        assertEquals("이어하기", courses.get(1).get("action_label"));
        @SuppressWarnings("unchecked")
        Map<String, Object> advancedAction = (Map<String, Object>) courses.get(1).get("primary_action");
        assertEquals("continue", advancedAction.get("type"));
        assertEquals(true, advancedAction.get("enabled"));
        assertEquals("advanced", advancedAction.get("target_id"));
    }

    @Test
    void roadmapDaysCarryFigmaLockedProgressAndButtonState() {
        when(learningUserStateRepository.findById(1L)).thenReturn(Optional.empty());
        when(educationOverviewRepository.findByTrackAndSectorOrderByDayNumberAsc(eq("intro_core"), isNull()))
                .thenReturn(coreOverviews("intro_core"));
        when(educationCardRepository.findByTrackAndSectorAndDayNumberOrderBySourceIdxAsc(anyString(), any(), anyInt()))
                .thenReturn(List.of());
        when(educationQuizRepository.findByTrackAndSectorAndDayNumberOrderByQuizNumberAsc(anyString(), any(), anyInt()))
                .thenReturn(List.of());

        Map<String, Object> response = service.getCourseRoadmap(user, "intro");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> days = (List<Map<String, Object>>) response.get("days");
        assertEquals("current", days.get(0).get("status"));
        assertEquals("현재 학습", days.get(0).get("status_label"));
        assertEquals("Day 01 / 30", days.get(0).get("progress_label"));
        assertEquals(false, days.get(0).get("is_locked"));
        assertEquals("이어하기", days.get(0).get("action_label"));

        assertEquals("locked", days.get(1).get("status"));
        assertEquals("잠김", days.get(1).get("status_label"));
        assertEquals(true, days.get(1).get("is_locked"));
        assertEquals("이전 Day를 완료하면 열려요", days.get(1).get("locked_reason"));
        @SuppressWarnings("unchecked")
        Map<String, Object> lockedAction = (Map<String, Object>) days.get(1).get("primary_action");
        assertEquals("locked", lockedAction.get("type"));
        assertEquals(false, lockedAction.get("enabled"));
    }

    @Test
    void coursesOnlyExposeImplementedIntroAndAdvanced() {
        when(learningUserStateRepository.findById(1L)).thenReturn(Optional.empty());

        Map<String, Object> response = service.getCourses(user, "main");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) response.get("courses");
        assertEquals(2, courses.size());
        assertEquals("intro", courses.get(0).get("course_id"));
        assertEquals("advanced", courses.get(1).get("course_id"));
        assertFalse(courses.stream().anyMatch(course -> "intermediate".equals(course.get("course_id"))));
    }

    @Test
    void intermediateCourseCannotBeOpenedUntilContentExists() {
        when(learningUserStateRepository.findById(1L)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> service.getCourseRoadmap(user, "intermediate"));

        assertEquals("COURSE_NOT_FOUND", exception.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
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
        assertEquals("current", response.get("status"));
        assertEquals("현재 학습", response.get("status_label"));
        assertEquals(false, response.get("is_locked"));
        @SuppressWarnings("unchecked")
        Map<String, Object> progress = (Map<String, Object>) response.get("progress");
        assertEquals("1 / 4", progress.get("progress_label"));
        @SuppressWarnings("unchecked")
        Map<String, Object> primaryAction = (Map<String, Object>) response.get("primary_action");
        assertEquals("continue", primaryAction.get("type"));
        assertEquals("이어하기", primaryAction.get("label"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> flow = (List<Map<String, Object>>) response.get("flow");
        assertEquals("day_overview", flow.get(0).get("template_type"));
        assertEquals(1, flow.get(0).get("step_order"));
        @SuppressWarnings("unchecked")
        Map<String, Object> overviewAction = (Map<String, Object>) flow.get(0).get("primary_action");
        assertEquals("continue", overviewAction.get("type"));
        assertEquals("계속", overviewAction.get("label"));
        assertEquals(true, overviewAction.get("enabled"));
        assertEquals("content_text", flow.get(1).get("template_type"));
        assertEquals("content_visual", flow.get(2).get("template_type"));
        assertEquals("quiz_single_choice", flow.get(3).get("template_type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> quizAction = (Map<String, Object>) flow.get(3).get("primary_action");
        assertEquals("submit", quizAction.get("type"));
        assertEquals("제출", quizAction.get("label"));
        assertEquals(false, quizAction.get("enabled"));
        assertFalse(flow.get(3).containsKey("correct_choice_id"));
    }

    @Test
    void lockedFutureDayCannotBeOpenedDirectly() {
        when(learningUserStateRepository.findById(1L)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> service.getCourseDay(user, "intro", 2));

        assertEquals("DAY_LOCKED", exception.getMessage());
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
    }

    @Test
    void quizAttemptReturnsFigmaFeedbackStateOnlyAfterSubmit() {
        when(learningUserStateRepository.findById(1L)).thenReturn(Optional.empty());
        when(learningUserStateRepository.save(any(LearningUserStateEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(educationQuizRepository.findByTrackAndSectorAndDayNumberOrderByQuizNumberAsc(eq("intro_core"), isNull(), eq(1)))
                .thenReturn(List.of(quiz(1)));

        Map<String, Object> quiz = service.getQuiz("intro_d1_q1");
        assertEquals("quiz_single_choice", quiz.get("template_type"));
        assertEquals("not_selected", quiz.get("quiz_state"));
        assertFalse(quiz.containsKey("correct_choice_id"));

        Map<String, Object> correct = service.submitQuizAttempt(user, Map.of("quiz_id", "intro_d1_q1", "selected_choice_id", "a"));
        assertEquals("submitted_correct", correct.get("quiz_state"));
        assertEquals(true, correct.get("is_correct"));
        assertEquals("a", correct.get("correct_choice_id"));
        assertEquals("정답이에요!", correct.get("feedback_title"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nextAction = (Map<String, Object>) correct.get("next_action");
        assertEquals("continue", nextAction.get("type"));
        assertEquals("intro_d1_completion", nextAction.get("next_step_id"));

        Map<String, Object> wrong = service.submitQuizAttempt(user, Map.of("quiz_id", "intro_d1_q1", "selected_choice_id", "b"));
        assertEquals("submitted_wrong", wrong.get("quiz_state"));
        assertEquals(false, wrong.get("is_correct"));
        assertEquals("a", wrong.get("correct_choice_id"));
        assertEquals("오답이에요!", wrong.get("feedback_title"));
    }

    @Test
    void quizAttemptPreservesExistingLearningStateCreatedAtWhenSavingProgress() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 10, 9, 30);
        LearningUserStateEntity existing = existingLearningState();
        existing.setCreatedAt(createdAt);
        existing.setUpdatedAt(createdAt);
        AtomicReference<LearningUserStateEntity> savedState = new AtomicReference<>();
        when(learningUserStateRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(learningUserStateRepository.save(any(LearningUserStateEntity.class))).thenAnswer(invocation -> {
            LearningUserStateEntity entity = invocation.getArgument(0);
            savedState.set(entity);
            return entity;
        });
        when(educationQuizRepository.findByTrackAndSectorAndDayNumberOrderByQuizNumberAsc(eq("intro_core"), isNull(), eq(1)))
                .thenReturn(List.of(quiz(1)));

        service.submitQuizAttempt(user, Map.of("quiz_id", "intro_d1_q1", "selected_choice_id", "a"));

        assertEquals(createdAt, savedState.get().getCreatedAt());
    }

    @Test
    void intermediateQuizIdsAreRejectedUntilCourseExists() {
        when(learningUserStateRepository.findById(1L)).thenReturn(Optional.empty());

        ApiException readException = assertThrows(ApiException.class, () -> service.getQuiz("intermediate_d1_q1"));
        assertEquals("QUIZ_NOT_FOUND", readException.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, readException.getStatus());

        ApiException submitException = assertThrows(ApiException.class, () -> service.submitQuizAttempt(user, Map.of("quiz_id", "intermediate_d1_q1", "selected_choice_id", "a")));
        assertEquals("QUIZ_NOT_FOUND", submitException.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, submitException.getStatus());
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

        assertEquals("day_completion", first.get("template_type"));
        assertEquals("오늘도 정복 완료!", first.get("completion_title"));
        assertEquals("learning_complete_character_default", first.get("character_asset_key"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nextAction = (Map<String, Object>) first.get("next_action");
        assertEquals("roadmap", nextAction.get("type"));
        assertEquals("로드맵으로 돌아가기", nextAction.get("label"));
        assertEquals(2, nextAction.get("next_day"));
        assertEquals(false, nextAction.get("course_completed"));
        @SuppressWarnings("unchecked")
        Map<String, Object> firstReward = (Map<String, Object>) first.get("reward");
        @SuppressWarnings("unchecked")
        Map<String, Object> secondReward = (Map<String, Object>) second.get("reward");
        assertEquals(500, firstReward.get("point"));
        assertEquals(500, firstReward.get("total_point"));
        assertEquals(0, secondReward.get("point"));
        assertEquals(500, secondReward.get("total_point"));
    }

    @Test
    void dayCompletionPreservesExistingLearningStateCreatedAtWhenSavingProgress() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 10, 10, 15);
        LearningUserStateEntity existing = existingLearningState();
        existing.setCreatedAt(createdAt);
        existing.setUpdatedAt(createdAt);
        AtomicReference<LearningUserStateEntity> savedState = new AtomicReference<>();
        when(learningUserStateRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(learningUserStateRepository.save(any(LearningUserStateEntity.class))).thenAnswer(invocation -> {
            LearningUserStateEntity entity = invocation.getArgument(0);
            savedState.set(entity);
            return entity;
        });

        service.completeCourseDay(user, "intro", 1, Map.of("last_step_id", "intro_d1_completion"));

        assertEquals(createdAt, savedState.get().getCreatedAt());
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

    private LearningUserStateEntity existingLearningState() {
        return LearningUserStateEntity.builder()
                .userId(1L)
                .level(0)
                .point(0)
                .streakDays(0)
                .currentDayByCourseJson("{}")
                .completedDaysByCourseJson("{}")
                .submittedStepIdsJson("[]")
                .educationCurrentDayJson("{}")
                .educationCompletedDaysJson("{}")
                .educationQuizAnswersJson("{}")
                .educationCardProgressJson("{}")
                .educationSectorSelectionsJson("{}")
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
