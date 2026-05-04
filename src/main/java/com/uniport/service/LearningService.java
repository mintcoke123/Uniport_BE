package com.uniport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.dto.LearningCourseDetailResponseDTO;
import com.uniport.dto.LearningCourseStartResponseDTO;
import com.uniport.dto.LearningCourseSummaryDTO;
import com.uniport.dto.LearningCoursesResponseDTO;
import com.uniport.dto.LearningCurrentContentDTO;
import com.uniport.dto.LearningDayCompleteResponseDTO;
import com.uniport.dto.LearningDayContentResponseDTO;
import com.uniport.dto.LearningDayStepDTO;
import com.uniport.dto.LearningHomeCourseDTO;
import com.uniport.dto.LearningHomeResponseDTO;
import com.uniport.dto.LearningKeyConceptDTO;
import com.uniport.dto.LearningProgressDTO;
import com.uniport.dto.LearningRoadmapItemDTO;
import com.uniport.dto.LearningStepSubmitRequestDTO;
import com.uniport.dto.LearningStepSubmitResponseDTO;
import com.uniport.entity.LearningCourseEntity;
import com.uniport.entity.LearningUserStateEntity;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.LearningCourseRepository;
import com.uniport.repository.LearningUserStateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class LearningService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<LearningMockDataProvider.LearningDayCatalog>> DAY_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Integer>> CURRENT_DAY_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Set<Integer>>> COMPLETED_DAY_TYPE = new TypeReference<>() {};
    private static final TypeReference<Set<Long>> SUBMITTED_STEP_TYPE = new TypeReference<>() {};

    private final LearningCourseRepository learningCourseRepository;
    private final LearningUserStateRepository learningUserStateRepository;
    private final LearningMockDataProvider learningMockDataProvider;

    public LearningService(LearningCourseRepository learningCourseRepository,
                           LearningUserStateRepository learningUserStateRepository,
                           LearningMockDataProvider learningMockDataProvider) {
        this.learningCourseRepository = learningCourseRepository;
        this.learningUserStateRepository = learningUserStateRepository;
        this.learningMockDataProvider = learningMockDataProvider;
    }

    @PostConstruct
    @Transactional
    public void seedCatalogIfEmpty() {
        if (learningCourseRepository.count() > 0) {
            return;
        }
        List<Long> seedIds = List.of(1L, 2L, 3L, 4L, 5L);
        for (Long courseId : seedIds) {
            LearningMockDataProvider.LearningCourseCatalog course = learningMockDataProvider.getCourse(courseId);
            learningCourseRepository.save(LearningCourseEntity.builder()
                    .id(course.id())
                    .category(course.category())
                    .title(course.title())
                    .description(course.description())
                    .thumbnailUrl(course.thumbnailUrl())
                    .locked(course.locked())
                    .daysJson(writeValue(course.days()))
                    .build());
        }
    }

    public LearningCoursesResponseDTO getCourses(User user, String category) {
        LearningUserState state = getOrCreateState(user);
        String selectedCategory = category == null || category.isBlank() ? "MAIN" : category.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("MAIN", "MINI", "ADVANCED").contains(selectedCategory)) {
            throw new ApiException("Invalid learning category: " + selectedCategory, HttpStatus.BAD_REQUEST);
        }
        List<LearningCourseSummaryDTO> courses = learningCourseRepository.findByCategoryOrderByIdAsc(selectedCategory).stream()
                .map(this::toCourseCatalog)
                .map(course -> toCourseSummary(course, state))
                .toList();
        return LearningCoursesResponseDTO.builder()
                .categories(learningMockDataProvider.getCategories())
                .selectedCategory(selectedCategory)
                .courses(courses)
                .build();
    }

    @Transactional
    public LearningCourseStartResponseDTO startCourse(User user, long courseId) {
        LearningUserState state = getOrCreateState(user);
        LearningCourseCatalog course = getCourseOrThrow(courseId);
        if (course.locked()) {
            throw new ApiException("Locked course cannot be started", HttpStatus.CONFLICT);
        }
        state.activeCourseId = courseId;
        state.currentDayByCourse.putIfAbsent(courseId, 1);
        persistState(user.getId(), state);
        int currentDay = state.currentDayByCourse.get(courseId);
        return LearningCourseStartResponseDTO.builder()
                .courseId(courseId)
                .started(true)
                .status("IN_PROGRESS")
                .currentDay(currentDay)
                .totalDays(course.days().size())
                .build();
    }

    public LearningHomeResponseDTO getHome(User user) {
        LearningUserState state = getOrCreateState(user);
        if (state.activeCourseId == null) {
            throw new ApiException("No course in progress", HttpStatus.NOT_FOUND);
        }
        LearningCourseCatalog course = getCourseOrThrow(state.activeCourseId);
        int currentDay = state.currentDayByCourse.getOrDefault(course.id(), 1);
        LearningMockDataProvider.LearningDayCatalog day = getDayOrThrow(course.id(), currentDay);
        int completedDays = state.completedDaysByCourse.getOrDefault(course.id(), Set.of()).size();
        int totalDays = course.days().size();
        int progressPercent = totalDays == 0 ? 0 : (int) Math.floor((completedDays * 100.0) / totalDays);
        return LearningHomeResponseDTO.builder()
                .level(state.level)
                .point(state.point)
                .todayLearningCompleted(LocalDate.now().equals(state.lastCompletedDate))
                .course(new LearningHomeCourseDTO(
                        course.id(),
                        course.title(),
                        progressPercent,
                        currentDay,
                        totalDays,
                        String.format("Day %02d / %02d", currentDay, totalDays),
                        resolveWorldTheme(course.category()),
                        resolveWorldLabel(course.category())))
                .roadmap(buildRoadmap(course.id(), totalDays, currentDay, state))
                .currentContent(new LearningCurrentContentDTO(day.day(), day.title(), "CURRENT"))
                .build();
    }

    public LearningCourseDetailResponseDTO getCourseDetail(User user, long courseId) {
        LearningUserState state = getOrCreateState(user);
        LearningCourseCatalog course = getCourseOrThrow(courseId);
        int currentDay = state.currentDayByCourse.getOrDefault(courseId, 1);
        LearningMockDataProvider.LearningDayCatalog day = getDayOrThrow(courseId, currentDay);
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

    public LearningDayContentResponseDTO getDayContent(User user, long courseId, int dayId) {
        LearningUserState state = getOrCreateState(user);
        LearningCourseCatalog course = getCourseOrThrow(courseId);
        LearningMockDataProvider.LearningDayCatalog day = getDayOrThrow(courseId, dayId);
        int currentDay = state.currentDayByCourse.getOrDefault(courseId, 1);
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

    @Transactional
    public LearningStepSubmitResponseDTO submitStep(User user, long stepId, LearningStepSubmitRequestDTO request) {
        LearningUserState state = getOrCreateState(user);
        if (request == null || request.getSelectedAnswerId() == null) {
            throw new ApiException("selectedAnswerId is required", HttpStatus.BAD_REQUEST);
        }
        LearningStepLookup lookup = getStepOrThrow(stepId);
        if ("THEORY".equals(lookup.step().getType())) {
            throw new ApiException("THEORY step cannot be submitted", HttpStatus.BAD_REQUEST);
        }
        Long correctAnswerId = getCorrectAnswerId(stepId);
        boolean isCorrect = correctAnswerId != null && correctAnswerId.equals(request.getSelectedAnswerId());
        state.submittedStepIds.add(stepId);
        persistState(user.getId(), state);
        boolean dayCompleted = isDayReadyToComplete(lookup.course().id(), lookup.day().day(), state);
        return LearningStepSubmitResponseDTO.builder()
                .stepId(stepId)
                .isCorrect(isCorrect)
                .correctAnswerId(correctAnswerId)
                .explanation(getExplanation(stepId))
                .submitted(true)
                .nextStepId(getNextStepId(lookup.day().steps(), stepId))
                .dayCompleted(dayCompleted)
                .resultTitle(isCorrect ? "Correct!" : "Try again")
                .resultDescription(getExplanation(stepId))
                .build();
    }

    @Transactional
    public LearningDayCompleteResponseDTO completeDay(User user, long courseId, int dayId) {
        LearningUserState state = getOrCreateState(user);
        LearningCourseCatalog course = getCourseOrThrow(courseId);
        LearningMockDataProvider.LearningDayCatalog day = getDayOrThrow(courseId, dayId);
        if (state.completedDaysByCourse.getOrDefault(courseId, Set.of()).contains(day.day())) {
            throw new ApiException("Day already completed", HttpStatus.BAD_REQUEST);
        }
        if (!isDayReadyToComplete(courseId, day.day(), state)) {
            throw new ApiException("Day completion requirements are not met", HttpStatus.BAD_REQUEST);
        }

        state.completedDaysByCourse.computeIfAbsent(courseId, ignored -> new HashSet<>()).add(day.day());
        state.point += 50;
        state.level = Math.max(0, state.point / 300);
        updateStreak(state);

        int nextDay = Math.min(day.day() + 1, course.days().size());
        state.currentDayByCourse.put(courseId, nextDay);
        state.activeCourseId = courseId;
        persistState(user.getId(), state);

        return LearningDayCompleteResponseDTO.builder()
                .courseId(courseId)
                .day(day.day())
                .completed(true)
                .streakDays(state.streakDays)
                .earnedPoint(50)
                .earnedExp(120)
                .completionTitle("Day completed")
                .completionDescription("Your progress has been saved to the learning database.")
                .build();
    }

    private LearningCourseSummaryDTO toCourseSummary(LearningCourseCatalog course, LearningUserState state) {
        Set<Integer> completedDays = state.completedDaysByCourse.getOrDefault(course.id(), Set.of());
        Integer currentDay = state.currentDayByCourse.get(course.id());
        String status;
        if (course.locked()) {
            status = "LOCKED";
            currentDay = null;
        } else if (completedDays.size() >= course.days().size() && !course.days().isEmpty()) {
            status = "COMPLETED";
        } else if (state.activeCourseId != null && state.activeCourseId.equals(course.id())) {
            status = "IN_PROGRESS";
        } else {
            status = "AVAILABLE";
            if (currentDay == null) {
                currentDay = 0;
            }
        }
        return LearningCourseSummaryDTO.builder()
                .id(course.id())
                .title(course.title())
                .description(course.description())
                .thumbnailUrl(course.thumbnailUrl())
                .currentDay(currentDay)
                .totalDays(course.locked() ? null : course.days().size())
                .progressLabel(course.locked() || currentDay == null ? null : String.format("Day %02d / %02d", currentDay, course.days().size()))
                .status(status)
                .statusLabel(toStatusLabel(status))
                .actionLabel(toActionLabel(status))
                .isLocked(course.locked())
                .build();
    }

    private List<LearningRoadmapItemDTO> buildRoadmap(long courseId, int totalDays, int currentDay, LearningUserState state) {
        Set<Integer> completedDays = state.completedDaysByCourse.getOrDefault(courseId, Set.of());
        LearningCourseCatalog course = getCourseOrThrow(courseId);
        return java.util.stream.IntStream.rangeClosed(1, totalDays)
                .mapToObj(day -> LearningRoadmapItemDTO.builder()
                        .day(day)
                        .status(completedDays.contains(day) ? "COMPLETED" : day == currentDay ? "CURRENT" : "LOCKED")
                        .statusLabel(completedDays.contains(day) ? "Completed" : day == currentDay ? "Current" : "Locked")
                        .nodeType(resolveNodeType(day, currentDay, completedDays))
                        .xOffset(resolveXOffset(day))
                        .chapterLabel(getDayOrThrow(courseId, day).chapter())
                        .rewardLabel(day == currentDay || completedDays.contains(day) ? "50P" : null)
                        .lockedReason(completedDays.contains(day) || day == currentDay ? null : "Complete previous day first")
                        .build())
                .toList();
    }

    private String resolveNodeType(int day, int currentDay, Set<Integer> completedDays) {
        if (day == 1) {
            return "START";
        }
        if (day == currentDay) {
            return "CURRENT";
        }
        if (completedDays.contains(day)) {
            return day % 5 == 0 ? "CHECKPOINT" : "LESSON";
        }
        return day % 5 == 0 ? "CHECKPOINT" : "LESSON";
    }

    private int resolveXOffset(int day) {
        int pattern = (day - 1) % 4;
        return switch (pattern) {
            case 0 -> -2;
            case 1 -> 1;
            case 2 -> 2;
            default -> -1;
        };
    }

    private String resolveWorldTheme(String category) {
        return switch (category) {
            case "MINI" -> "SKY";
            case "ADVANCED" -> "CAVE";
            default -> "FOREST";
        };
    }

    private String resolveWorldLabel(String category) {
        return switch (category) {
            case "MINI" -> "빠르게 배우는 하늘 코스";
            case "ADVANCED" -> "심화 개념을 푸는 동굴 코스";
            default -> "기초 개념을 익히는 숲";
        };
    }

    private String toStatusLabel(String status) {
        return switch (status) {
            case "IN_PROGRESS" -> "In progress";
            case "COMPLETED" -> "Completed";
            case "LOCKED" -> "Locked";
            default -> "Available";
        };
    }

    private String toActionLabel(String status) {
        return switch (status) {
            case "IN_PROGRESS" -> "Resume";
            case "COMPLETED" -> "Review";
            case "LOCKED" -> "Locked";
            default -> "Start";
        };
    }

    private boolean isDayReadyToComplete(long courseId, int day, LearningUserState state) {
        LearningMockDataProvider.LearningDayCatalog catalog = getDayOrThrow(courseId, day);
        return catalog.steps().stream()
                .filter(step -> !"THEORY".equals(step.getType()))
                .allMatch(step -> state.submittedStepIds.contains(step.getId()));
    }

    private Long getCorrectAnswerId(long stepId) {
        return switch ((int) stepId) {
            case 1002, 1003, 302, 2002, 4002, 5002 -> 1L;
            case 102 -> 2L;
            case 103 -> 1L;
            default -> null;
        };
    }

    private String getExplanation(long stepId) {
        return switch ((int) stepId) {
            case 1002 -> "Stocks represent ownership in a company.";
            case 1003 -> "Demand growth can be interpreted as a positive signal.";
            case 102 -> "The candle body reflects the difference between open and close.";
            case 103 -> "A bullish candle closes above the open.";
            case 302 -> "Price and volume rising together often supports the move.";
            case 2002 -> "Moving averages are mainly used for identifying trend direction.";
            case 4002 -> "Buying means acquiring shares at the given market price.";
            case 5002 -> "Check source and publication date before trusting a news item.";
            default -> null;
        };
    }

    private Long getNextStepId(List<LearningDayStepDTO> steps, long currentStepId) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).getId() == currentStepId) {
                return i + 1 < steps.size() ? steps.get(i + 1).getId() : null;
            }
        }
        return null;
    }

    private LearningUserState getOrCreateState(User user) {
        if (user == null || user.getId() == null) {
            throw new ApiException("Authenticated user is required", HttpStatus.UNAUTHORIZED);
        }
        return learningUserStateRepository.findById(user.getId())
                .map(this::toState)
                .orElseGet(LearningUserState::new);
    }

    private void persistState(Long userId, LearningUserState state) {
        LearningUserStateEntity existing = learningUserStateRepository.findById(userId).orElse(null);
        learningUserStateRepository.save(LearningUserStateEntity.builder()
                .userId(userId)
                .level(state.level)
                .point(state.point)
                .activeCourseId(state.activeCourseId)
                .streakDays(state.streakDays)
                .lastCompletedDate(state.lastCompletedDate)
                .currentDayByCourseJson(writeValue(stringifyMap(state.currentDayByCourse)))
                .completedDaysByCourseJson(writeValue(stringifyCompletedMap(state.completedDaysByCourse)))
                .submittedStepIdsJson(writeValue(state.submittedStepIds))
                .educationCurrentDayJson(existing == null ? "{}" : defaultObjectJson(existing.getEducationCurrentDayJson()))
                .educationCompletedDaysJson(existing == null ? "{}" : defaultObjectJson(existing.getEducationCompletedDaysJson()))
                .educationQuizAnswersJson(existing == null ? "{}" : defaultObjectJson(existing.getEducationQuizAnswersJson()))
                .build());
    }

    private void updateStreak(LearningUserState state) {
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

    private Map<String, Integer> stringifyMap(Map<Long, Integer> value) {
        Map<String, Integer> mapped = new HashMap<>();
        value.forEach((key, item) -> mapped.put(String.valueOf(key), item));
        return mapped;
    }

    private Map<String, Set<Integer>> stringifyCompletedMap(Map<Long, Set<Integer>> value) {
        Map<String, Set<Integer>> mapped = new HashMap<>();
        value.forEach((key, item) -> mapped.put(String.valueOf(key), item));
        return mapped;
    }

    private LearningUserState toState(LearningUserStateEntity entity) {
        LearningUserState state = new LearningUserState();
        state.level = entity.getLevel();
        state.point = entity.getPoint();
        state.activeCourseId = entity.getActiveCourseId();
        state.streakDays = entity.getStreakDays();
        state.lastCompletedDate = entity.getLastCompletedDate();

        readValue(entity.getCurrentDayByCourseJson(), CURRENT_DAY_TYPE).forEach((key, value) -> state.currentDayByCourse.put(Long.parseLong(key), value));
        readValue(entity.getCompletedDaysByCourseJson(), COMPLETED_DAY_TYPE).forEach((key, value) -> state.completedDaysByCourse.put(Long.parseLong(key), new HashSet<>(value)));
        state.submittedStepIds.addAll(readValue(entity.getSubmittedStepIdsJson(), SUBMITTED_STEP_TYPE));
        return state;
    }

    private LearningCourseCatalog getCourseOrThrow(long courseId) {
        LearningCourseEntity entity = learningCourseRepository.findById(courseId)
                .orElseThrow(() -> new ApiException("Learning course not found: " + courseId, HttpStatus.NOT_FOUND));
        return toCourseCatalog(entity);
    }

    private LearningMockDataProvider.LearningDayCatalog getDayOrThrow(long courseId, int dayId) {
        return getCourseOrThrow(courseId).days().stream()
                .filter(day -> day.day() == dayId)
                .findFirst()
                .orElseThrow(() -> new ApiException("Learning day not found: " + dayId, HttpStatus.NOT_FOUND));
    }

    private LearningStepLookup getStepOrThrow(long stepId) {
        return learningCourseRepository.findAll().stream()
                .map(this::toCourseCatalog)
                .flatMap(course -> course.days().stream().map(day -> new LearningStepContainer(course, day)))
                .flatMap(container -> container.day().steps().stream().map(step -> new LearningStepLookup(container.course(), container.day(), step)))
                .filter(lookup -> lookup.step().getId() == stepId)
                .findFirst()
                .orElseThrow(() -> new ApiException("Learning step not found: " + stepId, HttpStatus.NOT_FOUND));
    }

    private LearningCourseCatalog toCourseCatalog(LearningCourseEntity entity) {
        return new LearningCourseCatalog(
                entity.getId(),
                entity.getCategory(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getThumbnailUrl(),
                Boolean.TRUE.equals(entity.getLocked()),
                readValue(entity.getDaysJson(), DAY_LIST_TYPE)
        );
    }

    private String writeValue(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new ApiException("Failed to serialize learning data", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private <T> T readValue(String value, TypeReference<T> type) {
        try {
            return OBJECT_MAPPER.readValue(value, type);
        } catch (Exception e) {
            throw new ApiException("Failed to read learning data", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String defaultObjectJson(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }

    private record LearningCourseCatalog(
            Long id,
            String category,
            String title,
            String description,
            String thumbnailUrl,
            boolean locked,
            List<LearningMockDataProvider.LearningDayCatalog> days
    ) {
    }

    private record LearningStepContainer(
            LearningCourseCatalog course,
            LearningMockDataProvider.LearningDayCatalog day
    ) {
    }

    private record LearningStepLookup(
            LearningCourseCatalog course,
            LearningMockDataProvider.LearningDayCatalog day,
            LearningDayStepDTO step
    ) {
    }

    private static final class LearningUserState {
        private Integer level = 0;
        private Integer point = 0;
        private Long activeCourseId;
        private int streakDays = 0;
        private LocalDate lastCompletedDate;
        private final Map<Long, Integer> currentDayByCourse = new HashMap<>();
        private final Map<Long, Set<Integer>> completedDaysByCourse = new HashMap<>();
        private final Set<Long> submittedStepIds = new HashSet<>();
    }
}
