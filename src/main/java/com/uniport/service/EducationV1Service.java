package com.uniport.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EducationV1Service {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CONTENT_VERSION = "2026-05-09.1";
    private static final int TOTAL_DAYS = 30;
    private static final int CORE_DAYS = 26;
    private static final int REQUIRED_SECTOR_COUNT = 2;
    private static final int DAILY_REWARD_POINT = 500;
    private static final int DAILY_REWARD_EXP = 500;
    private static final Pattern QUIZ_ID_PATTERN = Pattern.compile("^(intro|advanced)_d(\\d+)_q(\\d+)(?:_.+)?$");
    private static final TypeReference<Map<String, Integer>> MAP_STRING_INTEGER_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Set<Integer>>> MAP_STRING_SET_INTEGER_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Map<String, Integer>>> MAP_STRING_MAP_INTEGER_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, List<String>>> MAP_STRING_LIST_STRING_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private static final Map<String, CourseDefinition> COURSE_DEFINITIONS = Map.of(
            "intro", new CourseDefinition("intro", "입문 30일 코스", "투자의 기초를 탄탄하게 다지는 첫걸음", "intro_core", "intro_sector", "입문", "course_cover_intro_main"),
            "advanced", new CourseDefinition("advanced", "초급 30일 코스", "실전 감각을 익히는 심화 과정", "advanced_core", "advanced_sector", "초급", "course_cover_advanced_main")
    );

    private static final List<SectorDefinition> SECTORS = List.of(
            new SectorDefinition("battery", "2차전지"),
            new SectorDefinition("power_equipment", "전력기기"),
            new SectorDefinition("bio", "바이오"),
            new SectorDefinition("nuclear", "원전"),
            new SectorDefinition("space_rocket", "우주/로켓"),
            new SectorDefinition("ai_semiconductor", "AI 반도체"),
            new SectorDefinition("defense", "방산"),
            new SectorDefinition("quantum_computer", "양자컴퓨터"),
            new SectorDefinition("autonomous_driving", "자율주행"),
            new SectorDefinition("robot", "로봇")
    );

    private final LearningUserStateRepository learningUserStateRepository;
    private final EducationOverviewRepository educationOverviewRepository;
    private final EducationCardRepository educationCardRepository;
    private final EducationQuizRepository educationQuizRepository;
    private final PointLedgerService pointLedgerService;

    public EducationV1Service(LearningUserStateRepository learningUserStateRepository,
                              EducationOverviewRepository educationOverviewRepository,
                              EducationCardRepository educationCardRepository,
                              EducationQuizRepository educationQuizRepository,
                              PointLedgerService pointLedgerService) {
        this.learningUserStateRepository = learningUserStateRepository;
        this.educationOverviewRepository = educationOverviewRepository;
        this.educationCardRepository = educationCardRepository;
        this.educationQuizRepository = educationQuizRepository;
        this.pointLedgerService = pointLedgerService;
    }

    public Map<String, Object> getHome(User user) {
        EducationApiState state = getOrCreateState(user);
        Map<String, Object> response = linkedMap();
        response.put("content_version", CONTENT_VERSION);
        response.put("user", Map.of(
                "level_label", "Lv." + state.level,
                "point", state.point,
                "profile_asset_key", "profile_animal_default"));
        response.put("tabs", List.of(
                Map.of("key", "main", "label", "메인 코스", "selected", true),
                Map.of("key", "mini", "label", "미니 코스", "selected", false)));
        return response;
    }

    public Map<String, Object> getCourses(User user, String tab) {
        EducationApiState state = getOrCreateState(user);
        String selectedTab = tab == null || tab.isBlank() ? "main" : tab.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("main", "mini").contains(selectedTab)) {
            throw new ApiException("Invalid education tab: " + selectedTab, HttpStatus.BAD_REQUEST);
        }

        List<Map<String, Object>> courses = new ArrayList<>();
        if ("main".equals(selectedTab)) {
            courses.add(toCourseSummary(COURSE_DEFINITIONS.get("intro"), state));
            courses.add(toCourseSummary(COURSE_DEFINITIONS.get("advanced"), state));
        }

        Map<String, Object> response = linkedMap();
        response.put("content_version", CONTENT_VERSION);
        response.put("courses", courses);
        return response;
    }

    public Map<String, Object> getCourseRoadmap(User user, String courseId) {
        EducationApiState state = getOrCreateState(user);
        CourseDefinition course = requirePlayableCourse(courseId);
        List<EducationOverviewEntity> coreOverviews = educationOverviewRepository
                .findByTrackAndSectorOrderByDayNumberAsc(course.coreTrack(), null);
        Map<Integer, EducationOverviewEntity> overviewByDay = new HashMap<>();
        for (EducationOverviewEntity overview : coreOverviews) {
            overviewByDay.put(overview.getDayNumber(), overview);
        }

        List<String> selectedSectorIds = state.sectorSelectionsByCourse.getOrDefault(course.id(), List.of());
        Set<Integer> completedDays = state.completedDaysByCourse.getOrDefault(course.id(), Set.of());
        int currentDay = resolveCurrentDay(course.id(), state);

        List<Map<String, Object>> days = new ArrayList<>();
        for (int day = 1; day <= TOTAL_DAYS; day += 1) {
            DayTarget target = day <= CORE_DAYS || selectedSectorIds.size() < REQUIRED_SECTOR_COUNT
                    ? new DayTarget(course.coreTrack(), null, day, "core")
                    : sectorDayTarget(course, selectedSectorIds, day);
            EducationOverviewEntity overview = overviewByDay.get(day);
            String title = resolveRoadmapTitle(day, target, overview);
            Map<String, Object> dayMap = linkedMap();
            dayMap.put("day", day);
            dayMap.put("title", title);
            dayMap.put("module_type", target.moduleType());
            String sectorId = target.sectorId();
            if (sectorId != null) {
                dayMap.put("sector_id", sectorId);
            }
            String status = resolveDayStatus(day, currentDay, completedDays);
            dayMap.put("status", status);
            dayMap.put("status_label", statusLabel(status));
            dayMap.put("is_locked", "locked".equals(status));
            dayMap.put("locked_reason", lockedReason(status));
            dayMap.put("progress_label", dayProgressLabel(day));
            dayMap.put("cta_type", ctaType(status));
            dayMap.put("action_label", actionLabel(status));
            dayMap.put("primary_action", actionMap(status, course.id() + "_d" + day));
            dayMap.put("card_count", countCards(target));
            dayMap.put("quiz_count", countQuizzes(target));
            days.add(dayMap);
        }

        Map<String, Object> response = linkedMap();
        response.put("content_version", CONTENT_VERSION);
        response.put("course", Map.of(
                "course_id", course.id(),
                "title", course.title(),
                "subtitle", course.subtitle(),
                "total_days", TOTAL_DAYS,
                "core_days", CORE_DAYS,
                "sector_days", TOTAL_DAYS - CORE_DAYS));
        response.put("user_progress", Map.of(
                "level_label", "Lv." + state.level,
                "point", state.point,
                "current_day", currentDay,
                "completed_days", completedDays.size()));
        response.put("selected_sectors", selectedSectorMaps(selectedSectorIds));
        response.put("days", days);
        return response;
    }

    public Map<String, Object> getSectorSelection(User user, String courseId) {
        EducationApiState state = getOrCreateState(user);
        CourseDefinition course = requirePlayableCourse(courseId);
        Map<String, Object> response = linkedMap();
        response.put("content_version", CONTENT_VERSION);
        response.put("course_id", course.id());
        response.put("required_count", REQUIRED_SECTOR_COUNT);
        response.put("selected_sectors", selectedSectorMaps(state.sectorSelectionsByCourse.getOrDefault(course.id(), List.of())));
        response.put("available_sectors", availableSectorMaps());
        return response;
    }

    @Transactional
    public Map<String, Object> updateSectorSelection(User user, String courseId, Map<String, Object> request) {
        EducationApiState state = getOrCreateState(user);
        CourseDefinition course = requirePlayableCourse(courseId);
        if (resolveCurrentDay(course.id(), state) >= 27
                || state.completedDaysByCourse.getOrDefault(course.id(), Set.of()).stream().anyMatch(day -> day >= 27)) {
            throw new ApiException("SECTOR_SELECTION_LOCKED", HttpStatus.CONFLICT);
        }

        List<String> selectedIds = stringList(request == null ? null : request.get("selected_sector_ids"));
        if (selectedIds.size() != REQUIRED_SECTOR_COUNT || new LinkedHashSet<>(selectedIds).size() != REQUIRED_SECTOR_COUNT) {
            throw new ApiException("SECTOR_SELECTION_INVALID_COUNT", HttpStatus.BAD_REQUEST);
        }
        List<String> normalizedIds = selectedIds.stream()
                .map(this::normalizeSectorId)
                .toList();
        state.sectorSelectionsByCourse.put(course.id(), normalizedIds);
        persistState(user.getId(), state);
        return getSectorSelection(user, course.id());
    }

    public Map<String, Object> getCourseDay(User user, String courseId, int day) {
        EducationApiState state = getOrCreateState(user);
        CourseDefinition course = requirePlayableCourse(courseId);
        String dayStatus = ensureDayAccessible(course, state, day);
        DayTarget target = resolveDayTarget(course, state, day);
        Optional<EducationOverviewEntity> overview = educationOverviewRepository
                .findByTrackAndSectorAndDayNumber(target.track(), target.sectorName(), target.sourceDay());
        List<EducationCardEntity> cards = educationCardRepository
                .findByTrackAndSectorAndDayNumberOrderBySourceIdxAsc(target.track(), target.sectorName(), target.sourceDay());
        List<EducationQuizEntity> quizzes = educationQuizRepository
                .findByTrackAndSectorAndDayNumberOrderByQuizNumberAsc(target.track(), target.sectorName(), target.sourceDay());

        if (overview.isEmpty() && cards.isEmpty() && quizzes.isEmpty()) {
            throw new ApiException("DAY_NOT_FOUND", HttpStatus.NOT_FOUND);
        }

        List<Map<String, Object>> flow = new ArrayList<>();
        overview.ifPresent(value -> flow.add(toOverviewStep(course, day, value)));
        for (EducationCardEntity card : cards) {
            flow.add(toCardStep(course, day, card));
        }
        for (EducationQuizEntity quiz : quizzes.stream().sorted(Comparator.comparingInt(entity -> safeInt(entity.getQuizNumber()))).toList()) {
            flow.add(toQuizStep(course, day, quiz));
        }
        applyFlowUiContract(flow);

        int completedSteps = completedFlowSteps(course.id(), day, state);
        Map<String, Object> response = linkedMap();
        response.put("content_version", CONTENT_VERSION);
        response.put("course_id", course.id());
        response.put("course_label", course.label());
        response.put("day", day);
        response.put("day_label", course.label() + " Day " + day);
        response.put("module_type", target.moduleType());
        response.put("status", dayStatus);
        response.put("status_label", statusLabel(dayStatus));
        response.put("is_locked", false);
        response.put("locked_reason", null);
        response.put("title", overview.map(EducationOverviewEntity::getTitle).orElse("Day " + day));
        response.put("estimated_minutes", 5);
        response.put("progress", progressMap(Math.min(completedSteps + 1, Math.max(flow.size(), 1)), Math.max(flow.size(), 1), completedSteps));
        response.put("primary_action", actionMap(dayStatus, course.id() + "_d" + day));
        response.put("flow", flow);
        response.put("completion_preview", completionPreviewMap(state));
        return response;
    }

    public Map<String, Object> getCourseDayQuiz(User user, String courseId, int day) {
        Map<String, Object> dayResponse = getCourseDay(user, courseId, day);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> flow = (List<Map<String, Object>>) dayResponse.get("flow");
        List<Map<String, Object>> questions = flow.stream()
                .filter(item -> "quiz".equals(item.get("step_type")))
                .toList();
        Map<String, Object> response = linkedMap();
        response.put("content_version", CONTENT_VERSION);
        response.put("course_id", courseId);
        response.put("day", day);
        response.put("quiz_type", questions.isEmpty() ? "none" : "daily");
        response.put("questions", questions);
        return response;
    }

    public Map<String, Object> getQuiz(String quizId) {
        QuizLookup lookup = findQuizById(quizId);
        return toQuizResponse(lookup.course(), lookup.day(), lookup.quiz());
    }

    @Transactional
    public Map<String, Object> submitQuizAttempt(User user, Map<String, Object> request) {
        EducationApiState state = getOrCreateState(user);
        String quizId = stringValue(request, "quiz_id");
        String selectedChoiceId = stringValue(request, "selected_choice_id");
        if (quizId == null || selectedChoiceId == null) {
            throw new ApiException("quiz_id and selected_choice_id are required", HttpStatus.BAD_REQUEST);
        }
        QuizLookup lookup = findQuizById(quizId);
        int selectedChoiceIndex = choiceIndex(selectedChoiceId);
        if (selectedChoiceIndex < 1) {
            throw new ApiException("selected_choice_id is invalid", HttpStatus.BAD_REQUEST);
        }
        int correctChoiceIndex = Math.max(1, safeInt(lookup.quiz().getAnswerIndex()));
        boolean isCorrect = selectedChoiceIndex == correctChoiceIndex;
        String selectedChoiceText = choiceText(lookup.quiz(), selectedChoiceIndex);
        String correctChoiceText = choiceText(lookup.quiz(), correctChoiceIndex);

        String dayKey = dayKey(lookup.course().id(), lookup.day());
        state.quizAnswersByDay.computeIfAbsent(dayKey, ignored -> new HashMap<>()).put(quizId, selectedChoiceIndex);
        persistState(user.getId(), state);

        Map<String, Object> response = linkedMap();
        response.put("content_version", CONTENT_VERSION);
        response.put("attempt_id", "attempt_" + quizId + "_" + selectedChoiceId);
        response.put("quiz_id", quizId);
        response.put("selected_choice_id", selectedChoiceId);
        response.put("correct_choice_id", choiceId(correctChoiceIndex));
        if (selectedChoiceText != null) {
            response.put("selected_choice_text", selectedChoiceText);
        }
        if (correctChoiceText != null) {
            response.put("correct_choice_text", correctChoiceText);
        }
        response.put("is_correct", isCorrect);
        response.put("quiz_state", isCorrect ? "submitted_correct" : "submitted_wrong");
        response.put("feedback_title", isCorrect ? "정답이에요!" : "오답이에요!");
        response.put("explanation", explanation(lookup.quiz(), isCorrect, correctChoiceText));
        response.put("next_action", Map.of("type", "continue", "next_step_id", lookup.course().id() + "_d" + lookup.day() + "_completion"));
        return response;
    }

    @Transactional
    public Map<String, Object> completeCard(User user, Map<String, Object> request) {
        EducationApiState state = getOrCreateState(user);
        String courseId = stringValue(request, "course_id");
        Integer day = intValue(request, "day");
        Integer idx = intValue(request, "idx");
        if (courseId == null || day == null || idx == null) {
            throw new ApiException("course_id, day and idx are required", HttpStatus.BAD_REQUEST);
        }
        CourseDefinition course = requirePlayableCourse(courseId);
        ensureDayAccessible(course, state, day);
        DayTarget target = resolveDayTarget(course, state, day);
        List<EducationCardEntity> cards = educationCardRepository
                .findByTrackAndSectorAndDayNumberOrderBySourceIdxAsc(target.track(), target.sectorName(), target.sourceDay());
        if (cards.stream().noneMatch(card -> Objects.equals(card.getSourceIdx(), idx))) {
            throw new ApiException("CARD_NOT_FOUND", HttpStatus.NOT_FOUND);
        }

        String dayKey = dayKey(course.id(), day);
        state.completedCardIdxByDay.computeIfAbsent(dayKey, ignored -> new HashSet<>()).add(idx);
        persistState(user.getId(), state);

        int completedCards = state.completedCardIdxByDay.getOrDefault(dayKey, Set.of()).size();
        boolean canCompleteDay = completedCards >= cards.size();
        Map<String, Object> response = linkedMap();
        response.put("content_version", CONTENT_VERSION);
        response.put("progress", Map.of(
                "course_id", course.id(),
                "day", day,
                "completed_cards", completedCards,
                "total_cards", cards.size(),
                "current_card_index", Math.min(completedCards, Math.max(cards.size() - 1, 0)),
                "progress_label", completedCards + " / " + cards.size(),
                "progress_ratio", cards.isEmpty() ? 0.0 : completedCards / (double) cards.size(),
                "can_complete_day", canCompleteDay,
                "is_day_completed", state.completedDaysByCourse.getOrDefault(course.id(), Set.of()).contains(day)));
        return response;
    }

    @Transactional
    public Map<String, Object> completeCourseDay(User user, String courseId, int day, Map<String, Object> request) {
        EducationApiState state = getOrCreateState(user);
        CourseDefinition course = requirePlayableCourse(courseId);
        ensureDayAccessible(course, state, day);

        Set<Integer> completedDays = state.completedDaysByCourse.computeIfAbsent(course.id(), ignored -> new HashSet<>());
        boolean alreadyCompleted = completedDays.contains(day);
        int earnedPoint = alreadyCompleted ? 0 : DAILY_REWARD_POINT;
        int earnedExp = alreadyCompleted ? 0 : DAILY_REWARD_EXP;
        if (!alreadyCompleted) {
            ensureDayCompletionReady(course, state, day);
            completedDays.add(day);
            state.point += DAILY_REWARD_POINT;
            state.exp += DAILY_REWARD_EXP;
            LearningProgressPolicy.Progress progress = LearningProgressPolicy.fromExp(state.exp);
            state.level = progress.level();
            pointLedgerService.earn(
                    user,
                    DAILY_REWARD_POINT,
                    "EDUCATION_DAY_COMPLETE",
                    educationRewardSourceId(user.getId(), course.id(), day),
                    "교육 Day 완료 보상"
            );
            updateStreak(state);
            int nextDay = Math.min(day + 1, TOTAL_DAYS);
            state.currentDayByCourse.put(course.id(), nextDay);
            persistState(user.getId(), state);
        }
        LearningProgressPolicy.Progress progress = LearningProgressPolicy.fromExp(state.exp);

        Map<String, Object> response = linkedMap();
        response.put("content_version", CONTENT_VERSION);
        response.put("template_type", "day_completion");
        response.put("course_id", course.id());
        response.put("day", day);
        response.put("completion_title", "오늘도 정복 완료!");
        response.put("completion_subtitle", "고생 많으셨어요");
        response.put("streak", Map.of("days", state.streakDays, "label", state.streakDays + "일 연속!"));
        response.put("reward", Map.of(
                "exp", earnedExp,
                "point", earnedPoint,
                "total_exp", state.exp,
                "total_point", state.point,
                "level", progress.level(),
                "current_exp", progress.currentExp(),
                "max_exp", progress.maxExp()));
        response.put("character_asset_key", "learning_complete_character_default");
        Map<String, Object> nextAction = linkedMap();
        nextAction.put("type", "roadmap");
        nextAction.put("label", "로드맵으로 돌아가기");
        nextAction.put("next_day", day >= TOTAL_DAYS ? null : day + 1);
        nextAction.put("course_completed", completedDays.size() >= TOTAL_DAYS);
        response.put("next_action", nextAction);
        return response;
    }

    private void ensureDayCompletionReady(CourseDefinition course, EducationApiState state, int day) {
        DayTarget target = resolveDayTarget(course, state, day);
        String dayKey = dayKey(course.id(), day);
        Set<Integer> completedCards = state.completedCardIdxByDay.getOrDefault(dayKey, Set.of());
        Map<String, Integer> answeredQuizzes = state.quizAnswersByDay.getOrDefault(dayKey, Map.of());

        boolean cardsComplete = safeList(educationCardRepository
                .findByTrackAndSectorAndDayNumberOrderBySourceIdxAsc(target.track(), target.sectorName(), target.sourceDay()))
                .stream()
                .allMatch(card -> card.getSourceIdx() != null && completedCards.contains(card.getSourceIdx()));
        boolean quizzesComplete = safeList(educationQuizRepository
                .findByTrackAndSectorAndDayNumberOrderByQuizNumberAsc(target.track(), target.sectorName(), target.sourceDay()))
                .stream()
                .allMatch(quiz -> answeredQuizzes.containsKey(quizId(course, day, quiz)));

        if (!cardsComplete || !quizzesComplete) {
            throw new ApiException("DAY_PROGRESS_INCOMPLETE", HttpStatus.CONFLICT);
        }
    }

    private Map<String, Object> toCourseSummary(CourseDefinition course, EducationApiState state) {
        Set<Integer> completedDays = state.completedDaysByCourse.getOrDefault(course.id(), Set.of());
        Integer currentDay = state.currentDayByCourse.get(course.id());
        String status;
        if (completedDays.size() >= TOTAL_DAYS) {
            status = "completed";
            currentDay = TOTAL_DAYS;
        } else if (currentDay != null || !completedDays.isEmpty()) {
            status = "in_progress";
            currentDay = resolveCurrentDay(course.id(), state);
        } else {
            status = "unlocked";
            currentDay = 0;
        }

        Map<String, Object> map = linkedMap();
        map.put("course_id", course.id());
        map.put("title", course.title());
        map.put("subtitle", course.subtitle());
        map.put("total_days", TOTAL_DAYS);
        map.put("current_day", currentDay);
        map.put("completed_days", completedDays.size());
        map.put("progress_percent", (int) Math.floor((completedDays.size() * 100.0) / TOTAL_DAYS));
        map.put("progress_label", dayProgressLabel(currentDay == null ? 0 : currentDay));
        map.put("status", status);
        map.put("status_label", statusLabel(status));
        map.put("cover_asset_key", course.coverAssetKey());
        map.put("is_locked", "locked".equals(status));
        map.put("locked_reason", lockedReason(status));
        map.put("cta_type", ctaType(status));
        map.put("action_label", actionLabel(status));
        map.put("primary_action", actionMap(status, course.id()));
        return map;
    }

    private Map<String, Object> toOverviewStep(CourseDefinition course, int day, EducationOverviewEntity overview) {
        Map<String, Object> step = linkedMap();
        step.put("step_id", course.id() + "_d" + day + "_overview");
        step.put("step_type", "overview");
        step.put("template_type", "day_overview");
        step.put("title", overview.getTitle());
        step.put("body", bodyList(overview));
        step.put("key_concepts", readStringList(overview.getKeyPointsJson()));
        step.put("visual", Map.of(
                "visual_type", "component",
                "visual_key", course.id() + "_day" + day + "_overview",
                "asset_key", "",
                "alt", overview.getTitle()));
        return step;
    }

    private Map<String, Object> toCardStep(CourseDefinition course, int day, EducationCardEntity card) {
        String templateType = resolveTemplateType(card.getTemplateType(), card.getImageType());
        JsonNode cardVisual = readJsonNode(card.getVisualJson());
        JsonNode visualPayload = readJsonNode(card.getVisualPayloadJson());
        EducationVisualContractNormalizer.NormalizedVisual normalizedVisual = EducationVisualContractNormalizer.normalize(
                card.getImageType(),
                null,
                card.getVisualType(),
                card.getVisualKey(),
                card.getAssetKey(),
                card.getAssetId(),
                card.getSourceIdx(),
                cardVisual,
                visualPayload,
                card.getTitle(),
                card.getText());
        Map<String, Object> step = linkedMap();
        step.put("step_id", course.id() + "_d" + day + "_card_" + card.getSourceIdx());
        step.put("step_type", "card");
        step.put("template_type", templateType);
        step.put("idx", card.getSourceIdx());
        step.put("sheet", card.getSheet());
        step.put("track", card.getTrack());
        step.put("sector", card.getSector());
        step.put("day", day);
        step.put("section", card.getSection());
        step.put("card_number", card.getCardNumber());
        step.put("asset_id", card.getAssetId());
        step.put("title", card.getTitle());
        step.put("text", card.getText());
        step.put("image_type", normalizedVisual.imageType());
        step.put("visual_type", normalizedVisual.visualType());
        step.put("visual_key", normalizedVisual.visualKey());
        step.put("asset_key", normalizedVisual.assetKey());
        step.put("card_visual", normalizedVisual.cardVisual());
        if (!"content_text".equals(templateType)) {
            step.put("visual", visualMap(card, normalizedVisual));
        }
        return step;
    }

    private Map<String, Object> toQuizStep(CourseDefinition course, int day, EducationQuizEntity quiz) {
        Map<String, Object> step = toQuizResponse(course, day, quiz);
        step.put("step_id", step.get("quiz_id"));
        step.put("step_type", "quiz");
        return step;
    }

    private Map<String, Object> toQuizResponse(CourseDefinition course, int day, EducationQuizEntity quiz) {
        Map<String, Object> map = linkedMap();
        map.put("content_version", CONTENT_VERSION);
        map.put("quiz_id", quizId(course, day, quiz));
        map.put("template_type", "quiz_single_choice");
        map.put("question", quiz.getQuestion());
        map.put("choices", choices(quiz));
        map.put("quiz_state", "not_selected");
        return map;
    }

    private Map<String, Object> visualMap(EducationCardEntity card, EducationVisualContractNormalizer.NormalizedVisual normalizedVisual) {
        Map<String, Object> visual = linkedMap();
        visual.put("visual_type", normalizedVisual.visualType());
        visual.put("visual_key", normalizedVisual.visualKey());
        visual.put("asset_key", normalizedVisual.assetKey());
        visual.put("alt", altText(card));
        visual.put("payload", normalizedVisual.payload());
        visual.put("render_policy", readJsonNode(card.getRenderPolicyJson()));
        return visual;
    }

    private List<Map<String, Object>> choices(EducationQuizEntity quiz) {
        List<String> optionTexts = readStringList(quiz.getOptionsJson());
        List<Map<String, Object>> choices = new ArrayList<>();
        for (int i = 0; i < optionTexts.size(); i += 1) {
            choices.add(Map.of("choice_id", choiceId(i + 1), "text", optionTexts.get(i)));
        }
        return choices;
    }

    private String choiceText(EducationQuizEntity quiz, int oneBasedIndex) {
        List<String> optionTexts = readStringList(quiz.getOptionsJson());
        int index = oneBasedIndex - 1;
        if (index < 0 || index >= optionTexts.size()) {
            return null;
        }
        return optionTexts.get(index);
    }

    private QuizLookup findQuizById(String quizId) {
        if (quizId == null || quizId.isBlank()) {
            throw new ApiException("QUIZ_NOT_FOUND", HttpStatus.NOT_FOUND);
        }
        Matcher matcher = QUIZ_ID_PATTERN.matcher(quizId);
        if (!matcher.matches()) {
            throw new ApiException("QUIZ_NOT_FOUND", HttpStatus.NOT_FOUND);
        }
        CourseDefinition course = requirePlayableCourse(matcher.group(1));
        int day = Integer.parseInt(matcher.group(2));
        int quizNumber = Integer.parseInt(matcher.group(3));
        List<EducationQuizEntity> quizzes = educationQuizRepository
                .findByTrackAndSectorAndDayNumberOrderByQuizNumberAsc(course.coreTrack(), null, day);
        return quizzes.stream()
                .filter(quiz -> safeInt(quiz.getQuizNumber()) == quizNumber)
                .findFirst()
                .map(quiz -> new QuizLookup(course, day, quiz))
                .orElseThrow(() -> new ApiException("QUIZ_NOT_FOUND", HttpStatus.NOT_FOUND));
    }

    private DayTarget resolveDayTarget(CourseDefinition course, EducationApiState state, int day) {
        validateDay(day);
        if (day <= CORE_DAYS) {
            return new DayTarget(course.coreTrack(), null, day, "core");
        }
        List<String> selectedSectorIds = state.sectorSelectionsByCourse.getOrDefault(course.id(), List.of());
        if (selectedSectorIds.size() < REQUIRED_SECTOR_COUNT) {
            throw new ApiException("SECTOR_SELECTION_REQUIRED", HttpStatus.BAD_REQUEST);
        }
        return sectorDayTarget(course, selectedSectorIds, day);
    }

    private DayTarget sectorDayTarget(CourseDefinition course, List<String> selectedSectorIds, int day) {
        int selectedIndex = day <= 28 ? 0 : 1;
        int sourceDay = day == 27 || day == 29 ? 1 : 2;
        String sectorId = selectedSectorIds.get(selectedIndex);
        SectorDefinition sector = sectorById(sectorId)
                .orElseThrow(() -> new ApiException("SECTOR_NOT_FOUND", HttpStatus.NOT_FOUND));
        return new DayTarget(course.sectorTrack(), sector.name(), sourceDay, "sector", sector.id());
    }

    private String resolveRoadmapTitle(int day, DayTarget target, EducationOverviewEntity overview) {
        if ("sector".equals(target.moduleType())) {
            return target.sectorName() + " 섹터 " + target.sourceDay();
        }
        if (overview != null && overview.getTitle() != null) {
            return overview.getTitle();
        }
        return "Day " + day;
    }

    private int countCards(DayTarget target) {
        if (target.sectorName() == null && target.sourceDay() > CORE_DAYS) {
            return 0;
        }
        return educationCardRepository
                .findByTrackAndSectorAndDayNumberOrderBySourceIdxAsc(target.track(), target.sectorName(), target.sourceDay())
                .size();
    }

    private int countQuizzes(DayTarget target) {
        if (target.sectorName() == null && target.sourceDay() > CORE_DAYS) {
            return 0;
        }
        return educationQuizRepository
                .findByTrackAndSectorAndDayNumberOrderByQuizNumberAsc(target.track(), target.sectorName(), target.sourceDay())
                .size();
    }

    private int resolveCurrentDay(String courseId, EducationApiState state) {
        Integer storedDay = state.currentDayByCourse.get(courseId);
        if (storedDay != null) {
            return Math.min(Math.max(storedDay, 1), TOTAL_DAYS);
        }
        Set<Integer> completed = state.completedDaysByCourse.getOrDefault(courseId, Set.of());
        for (int day = 1; day <= TOTAL_DAYS; day += 1) {
            if (!completed.contains(day)) {
                return day;
            }
        }
        return TOTAL_DAYS;
    }

    private String resolveDayStatus(int day, int currentDay, Set<Integer> completedDays) {
        if (completedDays.contains(day)) {
            return "completed";
        }
        if (day == currentDay) {
            return "current";
        }
        if (day < currentDay) {
            return "available";
        }
        return "locked";
    }

    private String ensureDayAccessible(CourseDefinition course, EducationApiState state, int day) {
        validateDay(day);
        String status = resolveDayStatus(day, resolveCurrentDay(course.id(), state), state.completedDaysByCourse.getOrDefault(course.id(), Set.of()));
        if ("locked".equals(status)) {
            throw new ApiException("DAY_LOCKED", HttpStatus.CONFLICT);
        }
        return status;
    }

    private int completedFlowSteps(String courseId, int day, EducationApiState state) {
        String key = dayKey(courseId, day);
        int completedCards = state.completedCardIdxByDay.getOrDefault(key, Set.of()).size();
        int answeredQuizzes = state.quizAnswersByDay.getOrDefault(key, Map.of()).size();
        return completedCards + answeredQuizzes;
    }

    private void applyFlowUiContract(List<Map<String, Object>> flow) {
        int totalSteps = flow.size();
        for (int i = 0; i < totalSteps; i += 1) {
            Map<String, Object> step = flow.get(i);
            int stepOrder = i + 1;
            String nextStepId = i + 1 < totalSteps ? Objects.toString(flow.get(i + 1).get("step_id"), null) : null;
            step.put("step_order", stepOrder);
            step.put("total_steps", totalSteps);
            step.put("progress", progressMap(stepOrder, totalSteps, i));
            step.put("primary_action", stepActionMap(step, nextStepId));
        }
    }

    private List<Map<String, Object>> selectedSectorMaps(List<String> selectedSectorIds) {
        List<Map<String, Object>> selected = new ArrayList<>();
        for (int i = 0; i < selectedSectorIds.size(); i += 1) {
            SectorDefinition sector = sectorById(selectedSectorIds.get(i)).orElse(null);
            if (sector != null) {
                selected.add(Map.of("sector_id", sector.id(), "name", sector.name(), "order", i + 1));
            }
        }
        return selected;
    }

    private List<Map<String, Object>> availableSectorMaps() {
        return SECTORS.stream()
                .map(sector -> Map.<String, Object>of("sector_id", sector.id(), "name", sector.name()))
                .toList();
    }

    private Map<String, Object> progressMap(int currentStep, int totalSteps, int completedSteps) {
        Map<String, Object> progress = linkedMap();
        progress.put("current_step", currentStep);
        progress.put("total_steps", totalSteps);
        progress.put("completed_steps", completedSteps);
        progress.put("progress_label", currentStep + " / " + totalSteps);
        progress.put("progress_ratio", totalSteps == 0 ? 0.0 : currentStep / (double) totalSteps);
        progress.put("is_completed", totalSteps > 0 && completedSteps >= totalSteps);
        return progress;
    }

    private Map<String, Object> completionPreviewMap(EducationApiState state) {
        Map<String, Object> preview = linkedMap();
        preview.put("template_type", "day_completion");
        preview.put("reward_exp", DAILY_REWARD_EXP);
        preview.put("reward_point", DAILY_REWARD_POINT);
        preview.put("streak_days", state.streakDays);
        preview.put("character_asset_key", "learning_complete_character_default");
        return preview;
    }

    private List<String> bodyList(EducationOverviewEntity overview) {
        List<String> body = new ArrayList<>();
        if (overview.getSummary1() != null && !overview.getSummary1().isBlank()) {
            body.add(overview.getSummary1());
        }
        if (overview.getSummary2() != null && !overview.getSummary2().isBlank()) {
            body.add(overview.getSummary2());
        }
        return body;
    }

    private String altText(EducationCardEntity card) {
        JsonNode visual = readJsonNode(card.getVisualJson());
        if (visual != null && visual.hasNonNull("alt")) {
            return visual.get("alt").asText();
        }
        return card.getTitle();
    }

    private String quizId(CourseDefinition course, int day, EducationQuizEntity quiz) {
        return course.id() + "_d" + day + "_q" + safeInt(quiz.getQuizNumber());
    }

    private String explanation(EducationQuizEntity quiz, boolean isCorrect, String correctChoiceText) {
        String prefix = correctChoiceText == null || correctChoiceText.isBlank()
                ? null
                : "정답은 \"" + correctChoiceText + "\"입니다.";
        if (quiz.getIntent() != null && !quiz.getIntent().isBlank()) {
            return prefix == null ? quiz.getIntent() : prefix + " " + quiz.getIntent();
        }
        if (prefix != null) {
            return prefix;
        }
        return isCorrect ? "정답 선택지를 잘 골랐어요." : "정답 선택지와 해설을 다시 확인해 주세요.";
    }

    private String resolveTemplateType(String storedValue, String imageType) {
        if (storedValue != null && !storedValue.isBlank()) {
            return storedValue;
        }
        return isTextOnlyImageType(imageType) ? "content_text" : "content_visual";
    }

    private boolean isTextOnlyImageType(String imageType) {
        return imageType == null || imageType.isBlank() || "placeholder".equalsIgnoreCase(imageType.trim());
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "completed" -> "학습 완료됨";
            case "current" -> "현재 학습";
            case "available" -> "학습 가능";
            case "in_progress" -> "현재 이수중";
            case "locked" -> "잠김";
            default -> "잠금 해제됨";
        };
    }

    private String ctaType(String status) {
        return switch (status) {
            case "completed" -> "review";
            case "current", "in_progress" -> "continue";
            case "locked" -> "locked";
            default -> "start";
        };
    }

    private String actionLabel(String status) {
        return switch (status) {
            case "completed" -> "복습하기";
            case "current", "in_progress" -> "이어하기";
            case "locked" -> "잠김";
            default -> "학습하기";
        };
    }

    private String lockedReason(String status) {
        return "locked".equals(status) ? "이전 Day를 완료하면 열려요" : null;
    }

    private String dayProgressLabel(int day) {
        return String.format("Day %02d / %02d", day, TOTAL_DAYS);
    }

    private Map<String, Object> actionMap(String status, String targetId) {
        Map<String, Object> action = linkedMap();
        action.put("type", ctaType(status));
        action.put("label", actionLabel(status));
        action.put("enabled", !"locked".equals(status));
        action.put("target_id", targetId);
        return action;
    }

    private Map<String, Object> stepActionMap(Map<String, Object> step, String nextStepId) {
        Map<String, Object> action = linkedMap();
        boolean quiz = "quiz".equals(step.get("step_type"));
        action.put("type", quiz ? "submit" : "continue");
        action.put("label", quiz ? "제출" : "계속");
        action.put("enabled", !quiz);
        action.put("next_step_id", nextStepId);
        return action;
    }

    private String normalizeSectorId(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new ApiException("SECTOR_NOT_FOUND", HttpStatus.NOT_FOUND);
        }
        String trimmed = rawValue.trim();
        Optional<SectorDefinition> byId = sectorById(trimmed);
        if (byId.isPresent()) {
            return byId.get().id();
        }
        return SECTORS.stream()
                .filter(sector -> sector.name().equals(trimmed))
                .findFirst()
                .map(SectorDefinition::id)
                .orElseThrow(() -> new ApiException("SECTOR_NOT_FOUND", HttpStatus.NOT_FOUND));
    }

    private Optional<SectorDefinition> sectorById(String sectorId) {
        return SECTORS.stream().filter(sector -> sector.id().equals(sectorId)).findFirst();
    }

    private CourseDefinition requirePlayableCourse(String courseId) {
        CourseDefinition course = COURSE_DEFINITIONS.get(normalizeCourseId(courseId));
        if (course == null) {
            throw new ApiException("COURSE_NOT_FOUND", HttpStatus.NOT_FOUND);
        }
        return course;
    }

    private String normalizeCourseId(String courseId) {
        return courseId == null ? "" : courseId.trim().toLowerCase(Locale.ROOT);
    }

    private void validateDay(int day) {
        if (day < 1 || day > TOTAL_DAYS) {
            throw new ApiException("DAY_NOT_FOUND", HttpStatus.NOT_FOUND);
        }
    }

    private EducationApiState getOrCreateState(User user) {
        if (user == null || user.getId() == null) {
            throw new ApiException("Authenticated user is required", HttpStatus.UNAUTHORIZED);
        }
        return learningUserStateRepository.findById(user.getId())
                .map(this::toState)
                .orElseGet(EducationApiState::new);
    }

    private EducationApiState toState(LearningUserStateEntity entity) {
        EducationApiState state = new EducationApiState();
        state.level = entity.getLevel() == null ? 0 : entity.getLevel();
        state.point = entity.getPoint() == null ? 0 : entity.getPoint();
        state.exp = entity.getExp() == null ? state.point : entity.getExp();
        state.level = LearningProgressPolicy.fromExp(state.exp).level();
        state.streakDays = entity.getStreakDays() == null ? 0 : entity.getStreakDays();
        state.lastCompletedDate = entity.getLastCompletedDate();
        state.currentDayByCourse.putAll(readObject(entity.getEducationCurrentDayJson(), MAP_STRING_INTEGER_TYPE, new HashMap<>()));
        copySetMap(readObject(entity.getEducationCompletedDaysJson(), MAP_STRING_SET_INTEGER_TYPE, new HashMap<>()), state.completedDaysByCourse);
        copySetMap(readObject(entity.getEducationCardProgressJson(), MAP_STRING_SET_INTEGER_TYPE, new HashMap<>()), state.completedCardIdxByDay);
        readObject(entity.getEducationQuizAnswersJson(), MAP_STRING_MAP_INTEGER_TYPE, new HashMap<>())
                .forEach((key, value) -> state.quizAnswersByDay.put(key, new HashMap<>(value)));
        readObject(entity.getEducationSectorSelectionsJson(), MAP_STRING_LIST_STRING_TYPE, new HashMap<>())
                .forEach((key, value) -> state.sectorSelectionsByCourse.put(key, new ArrayList<>(value)));
        return state;
    }

    private void copySetMap(Map<String, Set<Integer>> source, Map<String, Set<Integer>> target) {
        source.forEach((key, value) -> target.put(key, new HashSet<>(value)));
    }

    private void persistState(Long userId, EducationApiState state) {
        LearningUserStateEntity entity = learningUserStateRepository.findById(userId)
                .orElseGet(() -> LearningUserStateEntity.builder()
                        .userId(userId)
                        .currentDayByCourseJson("{}")
                        .completedDaysByCourseJson("{}")
                        .submittedStepIdsJson("[]")
                        .build());
        entity.setLevel(state.level);
        entity.setPoint(state.point);
        entity.setExp(state.exp);
        entity.setStreakDays(state.streakDays);
        entity.setLastCompletedDate(state.lastCompletedDate);
        entity.setCurrentDayByCourseJson(defaultObjectJson(entity.getCurrentDayByCourseJson()));
        entity.setCompletedDaysByCourseJson(defaultObjectJson(entity.getCompletedDaysByCourseJson()));
        entity.setSubmittedStepIdsJson(defaultArrayJson(entity.getSubmittedStepIdsJson()));
        entity.setEducationCurrentDayJson(writeValue(state.currentDayByCourse));
        entity.setEducationCompletedDaysJson(writeValue(state.completedDaysByCourse));
        entity.setEducationQuizAnswersJson(writeValue(state.quizAnswersByDay));
        entity.setEducationCardProgressJson(writeValue(state.completedCardIdxByDay));
        entity.setEducationSectorSelectionsJson(writeValue(state.sectorSelectionsByCourse));
        learningUserStateRepository.save(entity);
    }

    private void updateStreak(EducationApiState state) {
        LocalDate today = LocalDate.now();
        if (today.equals(state.lastCompletedDate)) {
            return;
        }
        if (today.minusDays(1).equals(state.lastCompletedDate)) {
            state.streakDays += 1;
        } else {
            state.streakDays = 1;
        }
        state.lastCompletedDate = today;
    }

    private String educationRewardSourceId(Long userId, String courseId, int day) {
        return "user-" + userId + "-" + courseId + "-day-" + day;
    }

    private <T> T readObject(String json, TypeReference<T> typeReference, T defaultValue) {
        if (json == null || json.isBlank()) {
            return defaultValue;
        }
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException exception) {
            return defaultValue;
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private JsonNode readJsonNode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String writeValue(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize education state", exception);
        }
    }

    private String defaultObjectJson(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }

    private String defaultArrayJson(String value) {
        return value == null || value.isBlank() ? "[]" : value;
    }

    private String dayKey(String courseId, int day) {
        return courseId + "|day:" + day;
    }

    private String choiceId(int oneBasedIndex) {
        if (oneBasedIndex < 1 || oneBasedIndex > 26) {
            return String.valueOf(oneBasedIndex);
        }
        return String.valueOf((char) ('a' + oneBasedIndex - 1));
    }

    private int choiceIndex(String choiceId) {
        String normalized = choiceId.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() == 1 && normalized.charAt(0) >= 'a' && normalized.charAt(0) <= 'z') {
            return normalized.charAt(0) - 'a' + 1;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private Integer intValue(Map<String, Object> request, String key) {
        Object value = request == null ? null : request.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String stringValue(Map<String, Object> request, String key) {
        Object value = request == null ? null : request.get(key);
        return value == null ? null : value.toString();
    }

    private List<String> stringList(Object rawValue) {
        if (!(rawValue instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(value -> value == null ? null : value.toString())
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private LinkedHashMap<String, Object> linkedMap() {
        return new LinkedHashMap<>();
    }

    private record CourseDefinition(String id,
                                    String title,
                                    String subtitle,
                                    String coreTrack,
                                    String sectorTrack,
                                    String label,
                                    String coverAssetKey) {
    }

    private record SectorDefinition(String id, String name) {
    }

    private record DayTarget(String track, String sectorName, int sourceDay, String moduleType, String sectorId) {
        private DayTarget(String track, String sectorName, int sourceDay, String moduleType) {
            this(track, sectorName, sourceDay, moduleType, null);
        }
    }

    private record QuizLookup(CourseDefinition course, int day, EducationQuizEntity quiz) {
    }

    private static class EducationApiState {
        private int level = 1;
        private int point;
        private int exp;
        private int streakDays;
        private LocalDate lastCompletedDate;
        private final Map<String, Integer> currentDayByCourse = new HashMap<>();
        private final Map<String, Set<Integer>> completedDaysByCourse = new HashMap<>();
        private final Map<String, Set<Integer>> completedCardIdxByDay = new HashMap<>();
        private final Map<String, Map<String, Integer>> quizAnswersByDay = new HashMap<>();
        private final Map<String, List<String>> sectorSelectionsByCourse = new HashMap<>();
    }
}
