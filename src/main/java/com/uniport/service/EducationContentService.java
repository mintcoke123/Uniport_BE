package com.uniport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.dto.EducationCardDTO;
import com.uniport.dto.EducationCatalogResponseDTO;
import com.uniport.dto.EducationDayCompleteResponseDTO;
import com.uniport.dto.EducationDayContentResponseDTO;
import com.uniport.dto.EducationOverviewDTO;
import com.uniport.dto.EducationQuizMetaDTO;
import com.uniport.dto.EducationQuizOptionDTO;
import com.uniport.dto.EducationQuizQuestionDTO;
import com.uniport.dto.EducationQuizQuestionResultDTO;
import com.uniport.dto.EducationQuizResponseDTO;
import com.uniport.dto.EducationQuizSubmitRequestDTO;
import com.uniport.dto.EducationQuizSubmitResponseDTO;
import com.uniport.dto.EducationTrackSummaryDTO;
import com.uniport.entity.LearningUserStateEntity;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.LearningUserStateRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EducationContentService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CONTENT_VERSION = "2026-04-29";
    private static final Set<String> SUPPORTED_TRACKS = Set.of("intro_core", "advanced_core", "intro_sector", "advanced_sector");
    private static final Pattern SVG_MAP_PATTERN = Pattern.compile("(\\d+)\\s*:\\s*'([^']+)'");
    private static final TypeReference<Map<String, Integer>> CURRENT_DAY_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Set<Integer>>> COMPLETED_DAYS_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Map<String, Integer>>> QUIZ_ANSWERS_TYPE = new TypeReference<>() {};

    private final LearningUserStateRepository learningUserStateRepository;

    private List<JsonNode> cards = List.of();
    private List<JsonNode> overviews = List.of();
    private List<JsonNode> quizzes = List.of();
    private Map<Integer, String> svgPresetByIdx = Map.of();

    public EducationContentService(LearningUserStateRepository learningUserStateRepository) {
        this.learningUserStateRepository = learningUserStateRepository;
    }

    @PostConstruct
    public void loadResources() {
        this.cards = readArray("education/cards.json");
        this.overviews = readArray("education/education_overviews.json");
        this.quizzes = readArray("education/education_quizzes.json");
        this.svgPresetByIdx = parseSvgPresetMap("education/chart_svgs.js");
    }

    public EducationCatalogResponseDTO getCatalog() {
        LinkedHashSet<String> sectorOrder = new LinkedHashSet<>();
        Map<String, EducationTrackSummaryDTO> grouped = new LinkedHashMap<>();

        for (JsonNode node : overviews) {
            String track = normalizeTrack(text(node, "track"));
            String sector = nullableText(node, "sector");
            int day = node.path("day").asInt(0);
            String key = track + "|" + Objects.toString(sector, "");

            if (sector != null && !sector.isBlank()) {
                sectorOrder.add(sector);
            }

            grouped.compute(key, (ignored, current) -> {
                String levelLabel = text(node, "levelLabel");
                String title = sector == null
                        ? levelLabel + " " + dayCountLabel(track, day)
                        : sector + " " + levelLabel + " 모듈";
                if (current == null) {
                    return EducationTrackSummaryDTO.builder()
                            .track(track)
                            .levelLabel(levelLabel)
                            .title(title)
                            .totalDays(day)
                            .sector(sector)
                            .build();
                }
                return EducationTrackSummaryDTO.builder()
                        .track(current.getTrack())
                        .levelLabel(current.getLevelLabel())
                        .title(current.getTitle())
                        .totalDays(Math.max(current.getTotalDays(), day))
                        .sector(current.getSector())
                        .build();
            });
        }

        List<EducationTrackSummaryDTO> orderedTracks = new ArrayList<>();
        appendTracks(orderedTracks, grouped, "intro_core", null, sectorOrder);
        appendTracks(orderedTracks, grouped, "advanced_core", null, sectorOrder);
        appendTracks(orderedTracks, grouped, "intro_sector", "sector", sectorOrder);
        appendTracks(orderedTracks, grouped, "advanced_sector", "sector", sectorOrder);

        return EducationCatalogResponseDTO.builder()
                .contentVersion(CONTENT_VERSION)
                .tracks(orderedTracks)
                .sectors(new ArrayList<>(sectorOrder))
                .build();
    }

    public EducationDayContentResponseDTO getDayContent(String track, int day, String sector) {
        String normalizedTrack = normalizeRequestTrack(track);
        String normalizedSector = normalizeSector(normalizedTrack, sector);

        JsonNode overviewNode = overviews.stream()
                .filter(node -> normalizedTrack.equals(normalizeTrack(text(node, "track"))))
                .filter(node -> day == node.path("day").asInt())
                .filter(node -> Objects.equals(normalizedSector, nullableText(node, "sector")))
                .findFirst()
                .orElse(null);

        List<EducationCardDTO> dayCards = cards.stream()
                .filter(node -> normalizedTrack.equals(normalizeTrack(text(node, "track"))))
                .filter(node -> day == node.path("day").asInt())
                .filter(node -> Objects.equals(normalizedSector, cardSector(node)))
                .sorted(Comparator.comparingInt(node -> node.path("idx").asInt()))
                .map(node -> toCardDto(node, normalizedTrack))
                .toList();

        List<JsonNode> dayQuizzes = filterQuizzes(normalizedTrack, day, normalizedSector, null);
        if (overviewNode == null && dayCards.isEmpty() && dayQuizzes.isEmpty()) {
            throw new ApiException("Education day content not found", HttpStatus.NOT_FOUND);
        }

        String mode = dayQuizzes.isEmpty() ? inferMode(normalizedTrack, day) : normalizeMode(text(dayQuizzes.getFirst(), "source"));
        return EducationDayContentResponseDTO.builder()
                .contentVersion(CONTENT_VERSION)
                .track(normalizedTrack)
                .sector(normalizedSector)
                .day(day)
                .overview(overviewNode == null ? null : toOverviewDto(overviewNode))
                .cards(dayCards)
                .quiz(EducationQuizMetaDTO.builder()
                        .mode(mode)
                        .questionCount(dayQuizzes.size())
                        .available(!dayQuizzes.isEmpty())
                        .build())
                .build();
    }

    public EducationQuizResponseDTO getQuiz(String track, int day, String sector, String mode) {
        String normalizedTrack = normalizeRequestTrack(track);
        String normalizedSector = normalizeSector(normalizedTrack, sector);
        String normalizedMode = mode == null || mode.isBlank() ? null : normalizeMode(mode);

        List<JsonNode> filtered = filterQuizzes(normalizedTrack, day, normalizedSector, normalizedMode);
        if (filtered.isEmpty()) {
            throw new ApiException("Education quiz not found", HttpStatus.NOT_FOUND);
        }

        String responseMode = normalizedMode != null ? normalizedMode : normalizeMode(text(filtered.getFirst(), "source"));
        List<EducationQuizQuestionDTO> questions = filtered.stream()
                .sorted(Comparator.comparingInt(node -> node.path("quizNumber").asInt()))
                .map(node -> toQuestionDto(node, normalizedTrack, normalizedSector, day))
                .toList();

        return EducationQuizResponseDTO.builder()
                .contentVersion(CONTENT_VERSION)
                .track(normalizedTrack)
                .sector(normalizedSector)
                .day(day)
                .mode(responseMode)
                .questions(questions)
                .build();
    }

    @Transactional
    public EducationQuizSubmitResponseDTO submitQuiz(User user,
                                                     String track,
                                                     int day,
                                                     String sector,
                                                     String mode,
                                                     EducationQuizSubmitRequestDTO request) {
        if (request == null || request.getAnswers() == null || request.getAnswers().isEmpty()) {
            throw new ApiException("answers is required", HttpStatus.BAD_REQUEST);
        }

        EducationQuizResponseDTO quiz = getQuiz(track, day, sector, mode);
        EducationProgressState state = getOrCreateState(user);
        String dayKey = buildDayKey(quiz.getTrack(), quiz.getSector(), quiz.getDay());
        Map<String, Integer> storedAnswers = state.quizAnswersByDay.computeIfAbsent(dayKey, ignored -> new HashMap<>());
        request.getAnswers().forEach((questionId, selectedOptionId) -> {
            if (questionId != null && !questionId.isBlank() && selectedOptionId != null) {
                storedAnswers.put(questionId, selectedOptionId);
            }
        });

        persistState(user.getId(), state);

        List<EducationQuizQuestionResultDTO> results = new ArrayList<>();
        int answeredQuestions = 0;
        int correctCount = 0;
        for (EducationQuizQuestionDTO question : quiz.getQuestions()) {
            Integer selectedOptionId = storedAnswers.get(question.getId());
            Integer correctOptionId = question.getAnswerIndex() + 1;
            Boolean correct = null;
            if (selectedOptionId != null) {
                answeredQuestions += 1;
                correct = selectedOptionId.equals(correctOptionId);
                if (Boolean.TRUE.equals(correct)) {
                    correctCount += 1;
                }
            }
            results.add(EducationQuizQuestionResultDTO.builder()
                    .questionId(question.getId())
                    .selectedOptionId(selectedOptionId)
                    .correctOptionId(correctOptionId)
                    .correct(correct)
                    .build());
        }

        String trackKey = buildTrackKey(quiz.getTrack(), quiz.getSector());
        return EducationQuizSubmitResponseDTO.builder()
                .track(quiz.getTrack())
                .sector(quiz.getSector())
                .day(quiz.getDay())
                .mode(quiz.getMode())
                .submitted(true)
                .totalQuestions(quiz.getQuestions().size())
                .answeredQuestions(answeredQuestions)
                .correctCount(correctCount)
                .dayReadyToComplete(answeredQuestions == quiz.getQuestions().size())
                .dayCompleted(state.completedDaysByTrack.getOrDefault(trackKey, Set.of()).contains(quiz.getDay()))
                .results(results)
                .build();
    }

    @Transactional
    public EducationDayCompleteResponseDTO completeDay(User user, String track, int day, String sector) {
        String normalizedTrack = normalizeRequestTrack(track);
        String normalizedSector = normalizeSector(normalizedTrack, sector);
        EducationQuizResponseDTO quiz = getQuiz(normalizedTrack, day, normalizedSector, null);
        EducationProgressState state = getOrCreateState(user);
        String trackKey = buildTrackKey(normalizedTrack, normalizedSector);

        if (state.completedDaysByTrack.getOrDefault(trackKey, Set.of()).contains(day)) {
            throw new ApiException("Education day already completed", HttpStatus.BAD_REQUEST);
        }
        if (!isDayReadyToComplete(quiz, state)) {
            throw new ApiException("Education day completion requirements are not met", HttpStatus.BAD_REQUEST);
        }

        state.completedDaysByTrack.computeIfAbsent(trackKey, ignored -> new HashSet<>()).add(day);
        state.currentDayByTrack.put(trackKey, day + 1);
        state.point += 30;
        state.level = Math.max(0, state.point / 300);
        state.streakDays += 1;
        state.lastCompletedDate = java.time.LocalDate.now();
        persistState(user.getId(), state);

        return EducationDayCompleteResponseDTO.builder()
                .track(normalizedTrack)
                .sector(normalizedSector)
                .day(day)
                .completed(true)
                .streakDays(state.streakDays)
                .earnedPoint(30)
                .earnedExp(80)
                .nextDay(day + 1)
                .completionTitle("Education day completed")
                .completionDescription("Quiz progress has been saved to the learning database.")
                .build();
    }

    private void appendTracks(List<EducationTrackSummaryDTO> target,
                              Map<String, EducationTrackSummaryDTO> grouped,
                              String track,
                              String mode,
                              LinkedHashSet<String> sectorOrder) {
        if (mode == null) {
            EducationTrackSummaryDTO dto = grouped.get(track + "|");
            if (dto != null) {
                target.add(dto);
            }
            return;
        }

        for (String sector : sectorOrder) {
            EducationTrackSummaryDTO dto = grouped.get(track + "|" + sector);
            if (dto != null) {
                target.add(dto);
            }
        }
    }

    private List<JsonNode> filterQuizzes(String track, int day, String sector, String mode) {
        return quizzes.stream()
                .filter(node -> track.equals(normalizeTrack(text(node, "track"))))
                .filter(node -> day == node.path("day").asInt())
                .filter(node -> Objects.equals(sector, nullableText(node, "sector")))
                .filter(node -> mode == null || mode.equals(normalizeMode(text(node, "source"))))
                .toList();
    }

    private EducationOverviewDTO toOverviewDto(JsonNode node) {
        return EducationOverviewDTO.builder()
                .levelLabel(text(node, "levelLabel"))
                .dayLabel(text(node, "dayLabel"))
                .title(text(node, "title"))
                .summary1(text(node, "summary1"))
                .summary2(text(node, "summary2"))
                .keyPoints(readStringList(node.path("keyPoints")))
                .ctaLabel(text(node, "ctaLabel"))
                .build();
    }

    private EducationCardDTO toCardDto(JsonNode node, String track) {
        int idx = node.path("idx").asInt();
        return EducationCardDTO.builder()
                .idx(idx)
                .sheet(text(node, "sheet"))
                .track(track)
                .sector(cardSector(node))
                .day(node.path("day").asInt())
                .section(text(node, "section"))
                .cardNumber(text(node, "card_number"))
                .assetId(text(node, "asset_id"))
                .title(text(node, "title"))
                .text(text(node, "text"))
                .imageType(text(node, "image_type"))
                .svgPreset(svgPresetByIdx.get(idx))
                .visual(node.get("card_visual"))
                .build();
    }

    private EducationQuizQuestionDTO toQuestionDto(JsonNode node, String track, String sector, int day) {
        List<EducationQuizOptionDTO> options = new ArrayList<>();
        JsonNode optionNode = node.path("options");
        for (int i = 0; i < optionNode.size(); i++) {
            options.add(EducationQuizOptionDTO.builder()
                    .id(i + 1)
                    .text(optionNode.get(i).asText())
                    .build());
        }
        int quizNumber = node.path("quizNumber").asInt();
        return EducationQuizQuestionDTO.builder()
                .id(buildQuestionId(track, sector, day, quizNumber))
                .quizNumber(quizNumber)
                .quizType(text(node, "quizType"))
                .question(text(node, "question"))
                .options(options)
                .answerIndex(node.path("answerIndex").asInt())
                .topic(text(node, "topic"))
                .area(text(node, "area"))
                .intent(text(node, "intent"))
                .build();
    }

    private String buildQuestionId(String track, String sector, int day, int quizNumber) {
        StringBuilder builder = new StringBuilder(track)
                .append("-d")
                .append(day)
                .append("-q")
                .append(quizNumber);
        if (sector != null && !sector.isBlank()) {
            builder.append("-").append(Math.abs(sector.hashCode()));
        }
        return builder.toString();
    }

    private boolean isDayReadyToComplete(EducationQuizResponseDTO quiz, EducationProgressState state) {
        String dayKey = buildDayKey(quiz.getTrack(), quiz.getSector(), quiz.getDay());
        Map<String, Integer> storedAnswers = state.quizAnswersByDay.getOrDefault(dayKey, Map.of());
        return quiz.getQuestions().stream().allMatch(question -> storedAnswers.containsKey(question.getId()));
    }

    private EducationProgressState getOrCreateState(User user) {
        if (user == null || user.getId() == null) {
            throw new ApiException("Authenticated user is required", HttpStatus.UNAUTHORIZED);
        }

        return learningUserStateRepository.findById(user.getId())
                .map(this::toEducationState)
                .orElseGet(EducationProgressState::new);
    }

    private EducationProgressState toEducationState(LearningUserStateEntity entity) {
        EducationProgressState state = new EducationProgressState();
        state.level = entity.getLevel() == null ? 0 : entity.getLevel();
        state.point = entity.getPoint() == null ? 0 : entity.getPoint();
        state.streakDays = entity.getStreakDays() == null ? 0 : entity.getStreakDays();
        state.lastCompletedDate = entity.getLastCompletedDate();
        state.currentDayByTrack.putAll(readObject(entity.getEducationCurrentDayJson(), CURRENT_DAY_TYPE));
        readObject(entity.getEducationCompletedDaysJson(), COMPLETED_DAYS_TYPE)
                .forEach((key, value) -> state.completedDaysByTrack.put(key, new HashSet<>(value)));
        readObject(entity.getEducationQuizAnswersJson(), QUIZ_ANSWERS_TYPE)
                .forEach((key, value) -> state.quizAnswersByDay.put(key, new HashMap<>(value)));
        return state;
    }

    private void persistState(Long userId, EducationProgressState state) {
        LearningUserStateEntity existing = learningUserStateRepository.findById(userId).orElse(null);
        learningUserStateRepository.save(LearningUserStateEntity.builder()
                .userId(userId)
                .level(state.level)
                .point(state.point)
                .activeCourseId(existing == null ? null : existing.getActiveCourseId())
                .streakDays(state.streakDays)
                .lastCompletedDate(state.lastCompletedDate)
                .currentDayByCourseJson(existing == null ? "{}" : defaultObjectJson(existing.getCurrentDayByCourseJson()))
                .completedDaysByCourseJson(existing == null ? "{}" : defaultObjectJson(existing.getCompletedDaysByCourseJson()))
                .submittedStepIdsJson(existing == null ? "[]" : defaultArrayJson(existing.getSubmittedStepIdsJson()))
                .educationCurrentDayJson(writeValue(state.currentDayByTrack))
                .educationCompletedDaysJson(writeValue(state.completedDaysByTrack))
                .educationQuizAnswersJson(writeValue(state.quizAnswersByDay))
                .build());
    }

    private String buildTrackKey(String track, String sector) {
        return sector == null || sector.isBlank() ? track : track + "|" + sector;
    }

    private String buildDayKey(String track, String sector, int day) {
        return buildTrackKey(track, sector) + "|day:" + day;
    }

    private String normalizeRequestTrack(String track) {
        String normalized = normalizeTrack(track);
        if (!SUPPORTED_TRACKS.contains(normalized)) {
            throw new ApiException("Unsupported education track: " + track, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeSector(String track, String sector) {
        if (!track.contains("sector")) {
            return null;
        }
        if (sector == null || sector.isBlank()) {
            throw new ApiException("sector is required for sector tracks", HttpStatus.BAD_REQUEST);
        }
        return sector.trim();
    }

    private String normalizeTrack(String track) {
        if (track == null) {
            return null;
        }
        String trimmed = track.trim();
        if (trimmed.startsWith("intro_sector")) {
            return "intro_sector";
        }
        if (trimmed.startsWith("advanced_sector")) {
            return "advanced_sector";
        }
        return trimmed;
    }

    private String normalizeMode(String mode) {
        return mode == null ? null : mode.trim().toLowerCase(Locale.ROOT);
    }

    private String inferMode(String track, int day) {
        if (track.contains("sector")) {
            return "sector";
        }
        return day == 20 || day == 26 ? "review" : "daily";
    }

    private String cardSector(JsonNode node) {
        String sector = nullableText(node, "sector");
        if (sector != null) {
            return sector;
        }
        String track = normalizeTrack(text(node, "track"));
        return track != null && track.contains("sector") ? nullableText(node, "section") : null;
    }

    private List<String> readStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> result.add(item.asText()));
        }
        return result;
    }

    private List<JsonNode> readArray(String resourcePath) {
        try (InputStream inputStream = new ClassPathResource(resourcePath).getInputStream()) {
            JsonNode root = OBJECT_MAPPER.readTree(inputStream);
            if (!root.isArray()) {
                throw new ApiException("Education resource is not an array: " + resourcePath, HttpStatus.INTERNAL_SERVER_ERROR);
            }
            List<JsonNode> items = new ArrayList<>();
            root.forEach(items::add);
            return items;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Failed to load education resource: " + resourcePath, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String writeValue(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new ApiException("Failed to serialize education state", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private <T> T readObject(String value, TypeReference<T> typeReference) {
        try {
            return OBJECT_MAPPER.readValue(defaultObjectJson(value), typeReference);
        } catch (Exception e) {
            throw new ApiException("Failed to read education state", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Map<Integer, String> parseSvgPresetMap(String resourcePath) {
        try (InputStream inputStream = new ClassPathResource(resourcePath).getInputStream()) {
            String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            int start = text.indexOf("const IDX_TO_SVG = {");
            if (start < 0) {
                return Map.of();
            }
            int end = text.indexOf("};", start);
            if (end < 0) {
                return Map.of();
            }
            String block = text.substring(start, end);
            Matcher matcher = SVG_MAP_PATTERN.matcher(block);
            Map<Integer, String> result = new LinkedHashMap<>();
            while (matcher.find()) {
                result.put(Integer.parseInt(matcher.group(1)), matcher.group(2));
            }
            return result;
        } catch (Exception e) {
            throw new ApiException("Failed to parse svg preset map", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String dayCountLabel(String track, int totalDays) {
        if (track.contains("sector")) {
            return totalDays + "일 모듈";
        }
        return totalDays + "일 코스";
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field == null || field.isNull() ? null : field.asText();
    }

    private String nullableText(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        return value == null || value.isBlank() ? null : value;
    }

    private String defaultObjectJson(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }

    private String defaultArrayJson(String value) {
        return value == null || value.isBlank() ? "[]" : value;
    }

    private static final class EducationProgressState {
        private Integer level = 0;
        private Integer point = 0;
        private int streakDays = 0;
        private java.time.LocalDate lastCompletedDate;
        private final Map<String, Integer> currentDayByTrack = new HashMap<>();
        private final Map<String, Set<Integer>> completedDaysByTrack = new HashMap<>();
        private final Map<String, Map<String, Integer>> quizAnswersByDay = new HashMap<>();
    }
}
