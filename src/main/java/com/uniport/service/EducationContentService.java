package com.uniport.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EducationContentService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CONTENT_VERSION = "2026-05-09.1";
    private static final Set<String> SUPPORTED_TRACKS = Set.of("intro_core", "advanced_core", "intro_sector", "advanced_sector");
    private static final Pattern SVG_MAP_PATTERN = Pattern.compile("(\\d+)\\s*:\\s*'([^']+)'");
    private static final TypeReference<Map<String, Integer>> CURRENT_DAY_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Set<Integer>>> COMPLETED_DAYS_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Map<String, Integer>>> QUIZ_ANSWERS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final LearningUserStateRepository learningUserStateRepository;
    private final EducationOverviewRepository educationOverviewRepository;
    private final EducationCardRepository educationCardRepository;
    private final EducationQuizRepository educationQuizRepository;
    private final boolean forceRefreshOnStartup;

    public EducationContentService(LearningUserStateRepository learningUserStateRepository,
                                   EducationOverviewRepository educationOverviewRepository,
                                   EducationCardRepository educationCardRepository,
                                   EducationQuizRepository educationQuizRepository,
                                   @Value("${uniport.education.seed.force-refresh:false}") boolean forceRefreshOnStartup) {
        this.learningUserStateRepository = learningUserStateRepository;
        this.educationOverviewRepository = educationOverviewRepository;
        this.educationCardRepository = educationCardRepository;
        this.educationQuizRepository = educationQuizRepository;
        this.forceRefreshOnStartup = forceRefreshOnStartup;
    }

    @PostConstruct
    @Transactional
    public void seedDatabaseIfNeeded() {
        boolean hasExistingContent = educationOverviewRepository.count() > 0
                && educationCardRepository.count() > 0
                && educationQuizRepository.count() > 0;
        if (hasExistingContent && !forceRefreshOnStartup) {
            return;
        }

        List<JsonNode> overviewNodes = readArray("education/education_overviews.json");
        List<JsonNode> cardNodes = readArray("education/cards.json");
        List<JsonNode> quizNodes = readArray("education/education_quizzes.json");
        Map<Integer, String> svgPresetByIdx = parseSvgPresetMap("education/chart_svgs.js");

        if (forceRefreshOnStartup) {
            educationQuizRepository.deleteAllInBatch();
            educationCardRepository.deleteAllInBatch();
            educationOverviewRepository.deleteAllInBatch();
        }

        if (educationOverviewRepository.count() == 0) {
            List<EducationOverviewEntity> overviewEntities = overviewNodes.stream()
                    .map(this::toOverviewEntity)
                    .toList();
            educationOverviewRepository.saveAll(overviewEntities);
        }

        if (educationCardRepository.count() == 0) {
            List<EducationCardEntity> cardEntities = cardNodes.stream()
                    .map(node -> toCardEntity(node, svgPresetByIdx))
                    .toList();
            educationCardRepository.saveAll(cardEntities);
        }

        if (educationQuizRepository.count() == 0) {
            List<EducationQuizEntity> quizEntities = quizNodes.stream()
                    .map(this::toQuizEntity)
                    .toList();
            educationQuizRepository.saveAll(quizEntities);
        }
    }

    public EducationCatalogResponseDTO getCatalog() {
        LinkedHashSet<String> sectorOrder = new LinkedHashSet<>();
        Map<String, EducationTrackSummaryDTO> grouped = new LinkedHashMap<>();

        for (EducationOverviewEntity entity : educationOverviewRepository.findAllByOrderByTrackAscSectorAscDayNumberAsc()) {
            String track = normalizeTrack(entity.getTrack());
            String sector = entity.getSector();
            int day = safeInt(entity.getDayNumber());
            String key = track + "|" + Objects.toString(sector, "");

            if (sector != null && !sector.isBlank()) {
                sectorOrder.add(sector);
            }

            grouped.compute(key, (ignored, current) -> {
                String levelLabel = entity.getLevelLabel();
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

        EducationOverviewEntity overviewEntity = educationOverviewRepository
                .findByTrackAndSectorAndDayNumber(normalizedTrack, normalizedSector, day)
                .orElse(null);

        List<EducationCardDTO> dayCards = educationCardRepository
                .findByTrackAndSectorAndDayNumberOrderBySourceIdxAsc(normalizedTrack, normalizedSector, day).stream()
                .map(this::toCardDto)
                .toList();

        List<EducationQuizEntity> dayQuizEntities = filterQuizzes(normalizedTrack, day, normalizedSector, null);
        if (overviewEntity == null && dayCards.isEmpty() && dayQuizEntities.isEmpty()) {
            throw new ApiException("Education day content not found", HttpStatus.NOT_FOUND);
        }

        String mode = dayQuizEntities.isEmpty()
                ? inferMode(normalizedTrack, day)
                : normalizeMode(dayQuizEntities.getFirst().getSourceMode());

        return EducationDayContentResponseDTO.builder()
                .contentVersion(CONTENT_VERSION)
                .track(normalizedTrack)
                .sector(normalizedSector)
                .day(day)
                .overview(overviewEntity == null ? null : toOverviewDto(overviewEntity))
                .cards(dayCards)
                .quiz(EducationQuizMetaDTO.builder()
                        .mode(mode)
                        .questionCount(dayQuizEntities.size())
                        .available(!dayQuizEntities.isEmpty())
                        .build())
                .build();
    }

    public EducationQuizResponseDTO getQuiz(String track, int day, String sector, String mode) {
        String normalizedTrack = normalizeRequestTrack(track);
        String normalizedSector = normalizeSector(normalizedTrack, sector);
        String normalizedMode = mode == null || mode.isBlank() ? null : normalizeMode(mode);

        List<EducationQuizEntity> filtered = filterQuizzes(normalizedTrack, day, normalizedSector, normalizedMode);
        if (filtered.isEmpty()) {
            throw new ApiException("Education quiz not found", HttpStatus.NOT_FOUND);
        }

        String responseMode = normalizedMode != null ? normalizedMode : normalizeMode(filtered.getFirst().getSourceMode());
        List<EducationQuizQuestionDTO> questions = filtered.stream()
                .sorted(Comparator.comparingInt(entity -> safeInt(entity.getQuizNumber())))
                .map(entity -> toQuestionDto(entity, normalizedTrack, normalizedSector, day))
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

        int totalDays = getTotalDays(normalizedTrack, normalizedSector);
        state.completedDaysByTrack.computeIfAbsent(trackKey, ignored -> new HashSet<>()).add(day);
        Integer nextDay = day < totalDays ? day + 1 : null;
        if (nextDay == null) {
            state.currentDayByTrack.remove(trackKey);
        } else {
            state.currentDayByTrack.put(trackKey, nextDay);
        }
        state.point += 30;
        state.level = Math.max(0, state.point / 300);
        updateStreak(state);
        persistState(user.getId(), state);

        return EducationDayCompleteResponseDTO.builder()
                .track(normalizedTrack)
                .sector(normalizedSector)
                .day(day)
                .completed(true)
                .streakDays(state.streakDays)
                .earnedPoint(30)
                .earnedExp(80)
                .nextDay(nextDay)
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

    private List<EducationQuizEntity> filterQuizzes(String track, int day, String sector, String mode) {
        if (mode == null) {
            return educationQuizRepository.findByTrackAndSectorAndDayNumberOrderByQuizNumberAsc(track, sector, day);
        }
        return educationQuizRepository.findByTrackAndSectorAndDayNumberAndSourceModeOrderByQuizNumberAsc(track, sector, day, mode);
    }

    private EducationOverviewDTO toOverviewDto(EducationOverviewEntity entity) {
        return EducationOverviewDTO.builder()
                .levelLabel(entity.getLevelLabel())
                .dayLabel(entity.getDayLabel())
                .title(entity.getTitle())
                .summary1(entity.getSummary1())
                .summary2(entity.getSummary2())
                .keyPoints(readStringList(entity.getKeyPointsJson()))
                .ctaLabel(entity.getCtaLabel())
                .build();
    }

    private EducationCardDTO toCardDto(EducationCardEntity entity) {
        JsonNode visual = readJsonNode(entity.getVisualJson());
        JsonNode visualPayload = readJsonNode(entity.getVisualPayloadJson());
        EducationVisualContractNormalizer.NormalizedVisual normalizedVisual = EducationVisualContractNormalizer.normalize(
                entity.getImageType(),
                null,
                entity.getRendererType(),
                entity.getVisualType(),
                entity.getVisualKey(),
                entity.getComponentKey(),
                entity.getAssetKey(),
                entity.getImageDelivery(),
                entity.getImageUrl(),
                entity.getAssetId(),
                entity.getSourceIdx(),
                visual,
                visualPayload,
                readJsonNode(entity.getRenderPolicyJson()),
                entity.getTitle(),
                entity.getText());
        return EducationCardDTO.builder()
                .idx(safeInt(entity.getSourceIdx()))
                .sheet(entity.getSheet())
                .track(entity.getTrack())
                .sector(entity.getSector())
                .day(safeInt(entity.getDayNumber()))
                .section(entity.getSection())
                .cardNumber(entity.getCardNumber())
                .assetId(entity.getAssetId())
                .title(entity.getTitle())
                .text(entity.getText())
                .imageType(normalizedVisual.imageType())
                .svgPreset(entity.getSvgPreset())
                .templateType(resolveTemplateType(entity.getTemplateType(), normalizedVisual.imageType()))
                .rendererType(normalizedVisual.rendererType())
                .visualType(normalizedVisual.visualType())
                .visualKey(normalizedVisual.visualKey())
                .componentKey(normalizedVisual.componentKey())
                .assetKey(normalizedVisual.assetKey())
                .imageDelivery(normalizedVisual.imageDelivery())
                .imageUrl(normalizedVisual.imageUrl())
                .visual(toJsonNode(normalizedVisual.cardVisual()))
                .visualPayload(toJsonNode(normalizedVisual.payload()))
                .renderPolicy(toJsonNode(normalizedVisual.renderPolicy()))
                .build();
    }

    private EducationQuizQuestionDTO toQuestionDto(EducationQuizEntity entity, String track, String sector, int day) {
        List<String> optionTexts = readStringList(entity.getOptionsJson());
        List<EducationQuizOptionDTO> options = new ArrayList<>();
        for (int i = 0; i < optionTexts.size(); i++) {
            options.add(EducationQuizOptionDTO.builder()
                    .id(i + 1)
                    .text(optionTexts.get(i))
                    .build());
        }

        int quizNumber = safeInt(entity.getQuizNumber());
        return EducationQuizQuestionDTO.builder()
                .id(buildQuestionId(track, sector, day, quizNumber))
                .quizNumber(quizNumber)
                .quizType(entity.getQuizType())
                .question(entity.getQuestion())
                .options(options)
                .answerIndex(safeInt(entity.getAnswerIndex()))
                .topic(entity.getTopic())
                .area(entity.getArea())
                .intent(entity.getIntent())
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

    private int getTotalDays(String track, String sector) {
        return educationOverviewRepository.findByTrackAndSectorOrderByDayNumberAsc(track, sector).stream()
                .mapToInt(entity -> safeInt(entity.getDayNumber()))
                .max()
                .orElse(0);
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
                .roadmapLastCompletedDate(existing == null ? null : existing.getRoadmapLastCompletedDate())
                .currentDayByCourseJson(existing == null ? "{}" : defaultObjectJson(existing.getCurrentDayByCourseJson()))
                .completedDaysByCourseJson(existing == null ? "{}" : defaultObjectJson(existing.getCompletedDaysByCourseJson()))
                .submittedStepIdsJson(existing == null ? "[]" : defaultArrayJson(existing.getSubmittedStepIdsJson()))
                .educationCurrentDayJson(writeValue(state.currentDayByTrack))
                .educationCompletedDaysJson(writeValue(state.completedDaysByTrack))
                .educationQuizAnswersJson(writeValue(state.quizAnswersByDay))
                .educationCardProgressJson(existing == null ? "{}" : defaultObjectJson(existing.getEducationCardProgressJson()))
                .educationSectorSelectionsJson(existing == null ? "{}" : defaultObjectJson(existing.getEducationSectorSelectionsJson()))
                .build());
    }

    private void updateStreak(EducationProgressState state) {
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

    private EducationOverviewEntity toOverviewEntity(JsonNode node) {
        return EducationOverviewEntity.builder()
                .track(normalizeTrack(text(node, "track")))
                .sector(nullableText(node, "sector"))
                .dayNumber(node.path("day").asInt())
                .levelLabel(text(node, "levelLabel"))
                .dayLabel(text(node, "dayLabel"))
                .title(text(node, "title"))
                .summary1(text(node, "summary1"))
                .summary2(text(node, "summary2"))
                .keyPointsJson(writeValue(readStringList(node.path("keyPoints"))))
                .ctaLabel(text(node, "ctaLabel"))
                .build();
    }

    private EducationCardEntity toCardEntity(JsonNode node, Map<Integer, String> svgPresetByIdx) {
        int idx = node.path("idx").asInt();
        String assetId = nullableText(node, "asset_id");
        EducationVisualContractNormalizer.NormalizedVisual normalizedVisual = EducationVisualContractNormalizer.normalize(
                text(node, "image_type"),
                nullableText(node, "image_type_old"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                assetId,
                idx,
                node.get("card_visual"),
                node.get("card_visual"),
                null,
                text(node, "title"),
                text(node, "text"));
        return EducationCardEntity.builder()
                .sourceIdx(idx)
                .assetId(assetId)
                .sheet(text(node, "sheet"))
                .track(normalizeTrack(text(node, "track")))
                .sector(cardSector(node))
                .dayNumber(node.path("day").asInt())
                .section(text(node, "section"))
                .cardNumber(text(node, "card_number"))
                .title(text(node, "title"))
                .text(text(node, "text"))
                .imageType(normalizedVisual.imageType())
                .svgPreset(svgPresetByIdx.get(idx))
                .templateType(resolveTemplateType(null, normalizedVisual.imageType()))
                .rendererType(normalizedVisual.rendererType())
                .visualType(normalizedVisual.visualType())
                .visualKey(normalizedVisual.visualKey())
                .componentKey(normalizedVisual.componentKey())
                .assetKey(normalizedVisual.assetKey())
                .imageDelivery(normalizedVisual.imageDelivery())
                .imageUrl(normalizedVisual.imageUrl())
                .visualJson(writeNullableValue(normalizedVisual.cardVisual()))
                .visualPayloadJson(writeNullableValue(normalizedVisual.payload()))
                .renderPolicyJson(writeValue(normalizedVisual.renderPolicy()))
                .build();
    }

    private EducationQuizEntity toQuizEntity(JsonNode node) {
        return EducationQuizEntity.builder()
                .sourceMode(normalizeMode(text(node, "source")))
                .track(normalizeTrack(text(node, "track")))
                .sector(nullableText(node, "sector"))
                .dayNumber(node.path("day").asInt())
                .quizNumber(node.path("quizNumber").asInt())
                .quizType(text(node, "quizType"))
                .question(text(node, "question"))
                .optionsJson(writeValue(readStringList(node.path("options"))))
                .answerIndex(node.path("answerIndex").asInt())
                .topic(text(node, "topic"))
                .area(text(node, "area"))
                .intent(text(node, "intent"))
                .build();
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

    private String defaultRenderPolicyJson() {
        return "{\"fit\":\"contain\",\"allow_crop\":false}";
    }

    private String cardSector(JsonNode node) {
        String sector = nullableText(node, "sector");
        if (sector != null) {
            return sector;
        }
        String track = normalizeTrack(text(node, "track"));
        return track != null && track.contains("sector") ? nullableText(node, "section") : null;
    }

    private List<JsonNode> readArray(String resourcePath) {
        try (InputStream inputStream = new ClassPathResource(resourcePath).getInputStream()) {
            JsonNode root = OBJECT_MAPPER.readTree(inputStream);
            if (!root.isArray()) {
                throw new IllegalStateException("Expected array resource: " + resourcePath);
            }
            List<JsonNode> items = new ArrayList<>();
            root.forEach(items::add);
            return items;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read education resource: " + resourcePath, exception);
        }
    }

    private Map<Integer, String> parseSvgPresetMap(String resourcePath) {
        String content = readResourceText(resourcePath);
        Matcher matcher = SVG_MAP_PATTERN.matcher(content);
        Map<Integer, String> result = new HashMap<>();
        while (matcher.find()) {
            result.put(Integer.parseInt(matcher.group(1)), matcher.group(2));
        }
        return result;
    }

    private String readResourceText(String resourcePath) {
        try (InputStream inputStream = new ClassPathResource(resourcePath).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read education resource: " + resourcePath, exception);
        }
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            return List.of(node.asText());
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return values;
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse string list json", exception);
        }
    }

    private JsonNode readJsonNode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse education card visual json", exception);
        }
    }

    private JsonNode toJsonNode(Object value) {
        return value == null ? null : OBJECT_MAPPER.valueToTree(value);
    }

    private <T> T readObject(String json, TypeReference<T> typeReference) {
        if (json == null || json.isBlank()) {
            return defaultValue(typeReference);
        }
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException exception) {
            return defaultValue(typeReference);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T defaultValue(TypeReference<T> typeReference) {
        if (typeReference.getType().getTypeName().contains("Set")) {
            return (T) new HashMap<String, Set<Integer>>();
        }
        if (typeReference.getType().getTypeName().contains("Map<java.lang.String, java.lang.Integer>")) {
            return (T) new HashMap<String, Integer>();
        }
        if (typeReference.getType().getTypeName().contains("Map<java.lang.String, java.util.Map")) {
            return (T) new HashMap<String, Map<String, Integer>>();
        }
        return (T) new HashMap<>();
    }

    private String writeValue(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize education content payload", exception);
        }
    }

    private String writeNullableValue(Object value) {
        return value == null ? null : writeValue(value);
    }

    private String defaultObjectJson(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }

    private String defaultArrayJson(String value) {
        return value == null || value.isBlank() ? "[]" : value;
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        return field.isMissingNode() || field.isNull() ? null : field.asText();
    }

    private String nullableText(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        return value == null || value.isBlank() ? null : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String dayCountLabel(String track, int day) {
        if (track.contains("advanced")) {
            return day + "일";
        }
        return day + "일 코스";
    }

    private static class EducationProgressState {
        private int level;
        private int point;
        private int streakDays;
        private LocalDate lastCompletedDate;
        private final Map<String, Integer> currentDayByTrack = new HashMap<>();
        private final Map<String, Set<Integer>> completedDaysByTrack = new HashMap<>();
        private final Map<String, Map<String, Integer>> quizAnswersByDay = new HashMap<>();
    }
}
