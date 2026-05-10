package com.uniport.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.uniport.dto.EducationCardDTO;
import com.uniport.dto.EducationDayContentResponseDTO;
import com.uniport.entity.EducationCardEntity;
import com.uniport.entity.EducationOverviewEntity;
import com.uniport.repository.EducationCardRepository;
import com.uniport.repository.EducationOverviewRepository;
import com.uniport.repository.EducationQuizRepository;
import com.uniport.repository.LearningUserStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationContentServiceTest {

    @Mock
    private LearningUserStateRepository learningUserStateRepository;

    @Mock
    private EducationOverviewRepository educationOverviewRepository;

    @Mock
    private EducationCardRepository educationCardRepository;

    @Mock
    private EducationQuizRepository educationQuizRepository;

    @Test
    void seedNormalizesLegacyImageCardsUsingImageTypeOld() {
        EducationContentService service = new EducationContentService(
                learningUserStateRepository,
                educationOverviewRepository,
                educationCardRepository,
                educationQuizRepository,
                false);

        service.seedDatabaseIfNeeded();

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Iterable<EducationCardEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(educationCardRepository).saveAll(captor.capture());
        List<EducationCardEntity> savedCards = new ArrayList<>();
        captor.getValue().forEach(savedCards::add);
        EducationCardEntity card = savedCards.stream()
                .filter(item -> Integer.valueOf(21).equals(item.getSourceIdx()))
                .findFirst()
                .orElse(null);

        assertNotNull(card);
        assertEquals("checklist", card.getImageType());
        assertEquals("content_visual", card.getTemplateType());
        assertEquals("component", card.getVisualType());
        assertEquals("template_checklist", card.getVisualKey());
        assertEquals(null, card.getAssetKey());
        assertTrue(card.getVisualPayloadJson().contains("\"template_visual_type\":\"checklist\""));
        assertTrue(card.getVisualPayloadJson().contains("\"items\""));
        assertFalse(card.getVisualPayloadJson().contains("\"image_url\":\"\""));
    }

    @Test
    void seedUsesDayThreeStructuredVisualMappingsInsteadOfDiagramFallbacks() {
        EducationContentService service = new EducationContentService(
                learningUserStateRepository,
                educationOverviewRepository,
                educationCardRepository,
                educationQuizRepository,
                false);

        service.seedDatabaseIfNeeded();

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Iterable<EducationCardEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(educationCardRepository).saveAll(captor.capture());
        List<EducationCardEntity> savedCards = new ArrayList<>();
        captor.getValue().forEach(savedCards::add);

        assertVisual(savedCards, 20, "comparison", "template_comparison", "\"코스피\"");
        assertVisual(savedCards, 23, "checklist", "template_checklist", "\"자금 조달 통로\"");
        assertVisual(savedCards, 25, "flow", "template_flow", "\"정규장\"");
        assertVisual(savedCards, 29, "comparison", "template_comparison", "\"상품시장\"");
        assertVisual(savedCards, 30, "flow", "template_flow", "\"금리\"");
    }

    @Test
    void dayContentReturnsNormalizedVisualPayloadNotOnlyMetadata() {
        EducationContentService service = new EducationContentService(
                learningUserStateRepository,
                educationOverviewRepository,
                educationCardRepository,
                educationQuizRepository,
                false);
        when(educationOverviewRepository.findByTrackAndSectorAndDayNumber(eq("intro_core"), isNull(), eq(3)))
                .thenReturn(Optional.of(EducationOverviewEntity.builder()
                        .track("intro_core")
                        .dayNumber(3)
                        .levelLabel("입문")
                        .dayLabel("Day 3")
                        .title("주식시장의 구조")
                        .summary1("summary")
                        .summary2("summary")
                        .keyPointsJson("[]")
                        .ctaLabel("시작")
                        .build()));
        when(educationCardRepository.findByTrackAndSectorAndDayNumberOrderBySourceIdxAsc(eq("intro_core"), isNull(), eq(3)))
                .thenReturn(List.of(EducationCardEntity.builder()
                        .sourceIdx(20)
                        .assetId("intro-core-d3-card-20")
                        .sheet("입문_카드_FINAL")
                        .track("intro_core")
                        .dayNumber(3)
                        .section("① 코스피와 코스닥의 정의")
                        .cardNumber("1/2")
                        .title("코스피와 코스닥의 정의")
                        .text("• 코스피는 대형 우량 기업이 모인 시장이에요.\n• 코스닥은 성장 기업 비중이 높은 시장이에요.")
                        .imageType("diagram")
                        .templateType("content_visual")
                        .visualType("component")
                        .visualKey("template_diagram")
                        .visualJson("{\"alt\":\"코스피와 코스닥의 정의\",\"category\":\"illustration\"}")
                        .visualPayloadJson("{\"template_visual_type\":\"diagram\",\"items\":[{\"text\":\"잘못된 다이어그램\"}]}")
                        .renderPolicyJson("{\"fit\":\"contain\",\"allow_crop\":false}")
                        .build()));
        when(educationQuizRepository.findByTrackAndSectorAndDayNumberOrderByQuizNumberAsc(eq("intro_core"), isNull(), eq(3)))
                .thenReturn(List.of());

        EducationDayContentResponseDTO response = service.getDayContent("intro_core", 3, null);

        EducationCardDTO card = response.getCards().getFirst();
        assertEquals("comparison", card.getImageType());
        assertEquals("component", card.getVisualType());
        assertEquals("template_comparison", card.getVisualKey());
        JsonNode payload = card.getVisualPayload();
        assertEquals("comparison", payload.path("template_visual_type").asText());
        assertTrue(payload.has("left"));
        assertTrue(payload.has("right"));
        assertTrue(payload.toString().contains("코스피"));
        assertFalse(payload.toString().contains("잘못된 다이어그램"));
    }

    private void assertVisual(List<EducationCardEntity> savedCards,
                              int idx,
                              String imageType,
                              String visualKey,
                              String expectedPayload) {
        EducationCardEntity card = savedCards.stream()
                .filter(item -> Integer.valueOf(idx).equals(item.getSourceIdx()))
                .findFirst()
                .orElse(null);

        assertNotNull(card);
        assertEquals(imageType, card.getImageType());
        assertEquals("content_visual", card.getTemplateType());
        assertEquals("component", card.getVisualType());
        assertEquals(visualKey, card.getVisualKey());
        assertEquals(null, card.getAssetKey());
        assertTrue(card.getVisualPayloadJson().contains(expectedPayload));
        assertFalse(card.getVisualPayloadJson().contains("\"image_url\":\"\""));
    }
}
