package com.uniport.service;

import com.uniport.dto.LearningCourseStartResponseDTO;
import com.uniport.dto.LearningCourseSummaryDTO;
import com.uniport.dto.LearningCoursesResponseDTO;
import com.uniport.dto.LearningCurrentContentDTO;
import com.uniport.dto.LearningDayCompleteResponseDTO;
import com.uniport.dto.LearningDayContentResponseDTO;
import com.uniport.dto.LearningHomeCourseDTO;
import com.uniport.dto.LearningHomeResponseDTO;
import com.uniport.dto.LearningRoadmapItemDTO;
import com.uniport.dto.LearningStepSubmitRequestDTO;
import com.uniport.dto.LearningStepSubmitResponseDTO;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LearningService {

    private final LearningMockDataProvider learningMockDataProvider;
    private final ConcurrentHashMap<Long, LearningUserState> states = new ConcurrentHashMap<>();

    public LearningService(LearningMockDataProvider learningMockDataProvider) {
        this.learningMockDataProvider = learningMockDataProvider;
    }

    public LearningCoursesResponseDTO getCourses(User user, String category) {
        LearningUserState state = getOrCreateState(user);
        String selectedCategory = category == null || category.isBlank() ? "MAIN" : category.trim().toUpperCase();
        if (!Set.of("MAIN", "MINI", "ADVANCED").contains(selectedCategory)) {
            throw new ApiException("Invalid learning category: " + selectedCategory, HttpStatus.BAD_REQUEST);
        }
        List<LearningCourseSummaryDTO> courses = learningMockDataProvider.getCoursesByCategory(selectedCategory).stream()
                .map(course -> toCourseSummary(course, state))
                .toList();
        return LearningCoursesResponseDTO.builder()
                .categories(learningMockDataProvider.getCategories())
                .selectedCategory(selectedCategory)
                .courses(courses)
                .build();
    }

    public LearningCourseStartResponseDTO startCourse(User user, long courseId) {
        LearningUserState state = getOrCreateState(user);
        LearningMockDataProvider.LearningCourseCatalog course = getCourseOrThrow(courseId);
        if (course.locked()) {
            throw new ApiException("Locked course cannot be started", HttpStatus.CONFLICT);
        }
        state.activeCourseId = courseId;
        state.currentDayByCourse.putIfAbsent(courseId, 1);
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
        LearningMockDataProvider.LearningCourseCatalog course = getCourseOrThrow(state.activeCourseId);
        int currentDay = state.currentDayByCourse.getOrDefault(course.id(), 1);
        LearningMockDataProvider.LearningDayCatalog day = learningMockDataProvider.getDay(course.id(), currentDay);
        int completedDays = state.completedDaysByCourse.getOrDefault(course.id(), Set.of()).size();
        int totalDays = course.days().size();
        int progressPercent = (int) Math.floor((completedDays * 100.0) / totalDays);
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
                        String.format("Day %02d / %02d", currentDay, totalDays)))
                .roadmap(buildRoadmap(course.id(), totalDays, currentDay, state))
                .currentContent(new LearningCurrentContentDTO(day.day(), day.title(), "CURRENT"))
                .build();
    }

    public com.uniport.dto.LearningCourseDetailResponseDTO getCourseDetail(User user, long courseId) {
        LearningUserState state = getOrCreateState(user);
        LearningMockDataProvider.LearningCourseCatalog course = getCourseOrThrow(courseId);
        int currentDay = state.currentDayByCourse.getOrDefault(courseId, 1);
        return learningMockDataProvider.toCourseDetail(course, currentDay);
    }

    public LearningDayContentResponseDTO getDayContent(User user, long courseId, int dayId) {
        LearningUserState state = getOrCreateState(user);
        LearningMockDataProvider.LearningCourseCatalog course = getCourseOrThrow(courseId);
        LearningMockDataProvider.LearningDayCatalog day = getDayOrThrow(courseId, dayId);
        int currentDay = state.currentDayByCourse.getOrDefault(courseId, 1);
        return learningMockDataProvider.toDayContent(course, currentDay, day);
    }

    public LearningStepSubmitResponseDTO submitStep(User user, long stepId, LearningStepSubmitRequestDTO request) {
        LearningUserState state = getOrCreateState(user);
        if (request == null || request.getSelectedAnswerId() == null) {
            throw new ApiException("selectedAnswerId is required", HttpStatus.BAD_REQUEST);
        }
        LearningMockDataProvider.LearningStepLookup lookup = getStepOrThrow(stepId);
        if ("THEORY".equals(lookup.step().getType())) {
            throw new ApiException("THEORY step cannot be submitted", HttpStatus.BAD_REQUEST);
        }
        Long correctAnswerId = getCorrectAnswerId(stepId);
        boolean isCorrect = correctAnswerId != null && correctAnswerId.equals(request.getSelectedAnswerId());
        state.submittedStepIds.add(stepId);
        boolean dayCompleted = isDayReadyToComplete(lookup.course().id(), lookup.day().day(), state);
        return LearningStepSubmitResponseDTO.builder()
                .stepId(stepId)
                .isCorrect(isCorrect)
                .correctAnswerId(correctAnswerId)
                .explanation(getExplanation(stepId))
                .submitted(true)
                .nextStepId(getNextStepId(lookup.day().steps(), stepId))
                .dayCompleted(dayCompleted)
                .resultTitle(isCorrect ? "정답이에요!" : "다시 생각해볼까요?")
                .resultDescription(getExplanation(stepId))
                .build();
    }

    public LearningDayCompleteResponseDTO completeDay(User user, long courseId, int dayId) {
        LearningUserState state = getOrCreateState(user);
        LearningMockDataProvider.LearningCourseCatalog course = getCourseOrThrow(courseId);
        LearningMockDataProvider.LearningDayCatalog day = getDayOrThrow(courseId, dayId);
        if (state.completedDaysByCourse.getOrDefault(courseId, Set.of()).contains(day.day())) {
            throw new ApiException("Day already completed", HttpStatus.BAD_REQUEST);
        }
        if (!isDayReadyToComplete(courseId, day.day(), state)) {
            throw new ApiException("Day completion requirements are not met", HttpStatus.BAD_REQUEST);
        }

        state.completedDaysByCourse.computeIfAbsent(courseId, ignored -> new HashSet<>()).add(day.day());
        state.point += 50;
        state.level = state.point / 300;
        state.streakDays += 1;
        state.lastCompletedDate = LocalDate.now();

        int nextDay = Math.min(day.day() + 1, course.days().size());
        if (day.day() < course.days().size()) {
            state.currentDayByCourse.put(courseId, nextDay);
        } else {
            state.currentDayByCourse.put(courseId, course.days().size());
            state.activeCourseId = courseId;
        }

        return LearningDayCompleteResponseDTO.builder()
                .courseId(courseId)
                .day(day.day())
                .completed(true)
                .streakDays(state.streakDays)
                .earnedPoint(50)
                .earnedExp(120)
                .completionTitle("오늘도 정복 완료!")
                .completionDescription("고생 많으셨어요")
                .build();
    }

    private LearningCourseSummaryDTO toCourseSummary(LearningMockDataProvider.LearningCourseCatalog course, LearningUserState state) {
        Set<Integer> completedDays = state.completedDaysByCourse.getOrDefault(course.id(), Set.of());
        Integer currentDay = state.currentDayByCourse.get(course.id());
        String status;
        if (course.locked()) {
            status = "LOCKED";
            currentDay = null;
        } else if (completedDays.size() >= course.days().size()) {
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
        return java.util.stream.IntStream.rangeClosed(1, totalDays)
                .mapToObj(day -> LearningRoadmapItemDTO.builder()
                        .day(day)
                        .status(completedDays.contains(day) ? "COMPLETED" : day == currentDay ? "CURRENT" : "LOCKED")
                        .statusLabel(completedDays.contains(day) ? "학습 완료됨" : day == currentDay ? "오늘 학습 진행중" : "잠김")
                        .build())
                .toList();
    }

    private String toStatusLabel(String status) {
        return switch (status) {
            case "IN_PROGRESS" -> "현재 이수중";
            case "COMPLETED" -> "학습 완료됨";
            case "LOCKED" -> "잠김";
            default -> "잠금 해제됨";
        };
    }

    private String toActionLabel(String status) {
        return switch (status) {
            case "IN_PROGRESS" -> "퀴즈 풀기";
            case "COMPLETED" -> "복습하기";
            case "LOCKED" -> "잠금";
            default -> "도전하기";
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
            case 1002 -> 1L;
            case 1003 -> 1L;
            case 102 -> 2L;
            case 103 -> 1L;
            case 302 -> 1L;
            case 2002 -> 1L;
            case 4002 -> 1L;
            case 5002 -> 1L;
            default -> null;
        };
    }

    private String getExplanation(long stepId) {
        if (stepId == 4002L) {
            return "매수는 특정 가격에 주식을 사는 행동을 의미합니다.";
        }
        if (stepId == 5002L) {
            return "뉴스는 출처와 날짜를 먼저 확인해야 신뢰도를 판단할 수 있습니다.";
        }
        return switch ((int) stepId) {
            case 1002 -> "주식은 기업의 소유권 일부를 의미합니다.";
            case 1003 -> "매수 관심 증가는 가격 상승 기대와 더 가까운 신호입니다.";
            case 102 -> "몸통은 시가와 종가의 차이를 의미합니다.";
            case 103 -> "양봉은 종가가 시가보다 높은 상태를 의미합니다.";
            case 302 -> "상승과 거래량 증가가 함께 나타나면 상승의 신뢰도가 높다고 해석합니다.";
            case 2002 -> "이동평균선은 가격 흐름의 추세를 파악할 때 자주 사용됩니다.";
            default -> null;
        };
    }

    private Long getNextStepId(List<com.uniport.dto.LearningDayStepDTO> steps, long currentStepId) {
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
        return states.computeIfAbsent(user.getId(), ignored -> new LearningUserState());
    }

    private LearningMockDataProvider.LearningCourseCatalog getCourseOrThrow(long courseId) {
        try {
            return learningMockDataProvider.getCourse(courseId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ex.getMessage(), HttpStatus.NOT_FOUND);
        }
    }

    private LearningMockDataProvider.LearningDayCatalog getDayOrThrow(long courseId, int dayId) {
        try {
            return learningMockDataProvider.getDay(courseId, dayId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ex.getMessage(), HttpStatus.NOT_FOUND);
        }
    }

    private LearningMockDataProvider.LearningStepLookup getStepOrThrow(long stepId) {
        try {
            return learningMockDataProvider.findStep(stepId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ex.getMessage(), HttpStatus.NOT_FOUND);
        }
    }

    private static final class LearningUserState {
        private Integer level = 0;
        private Integer point = 0;
        private Long activeCourseId;
        private int streakDays = 0;
        private LocalDate lastCompletedDate;
        private final ConcurrentHashMap<Long, Integer> currentDayByCourse = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Long, Set<Integer>> completedDaysByCourse = new ConcurrentHashMap<>();
        private final Set<Long> submittedStepIds = ConcurrentHashMap.newKeySet();
    }
}
