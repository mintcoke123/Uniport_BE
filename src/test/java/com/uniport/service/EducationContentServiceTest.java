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
    void seedUsesKmpRenderManifestForDayThreeRenderTypes() {
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

        assertVisual(savedCards, 20, "raster_asset", "raster_asset", "raster_asset",
                "real_images_generated_examples_intro_day3_kospi_kosdaq_real_boards_20260509", null, true);
        assertVisual(savedCards, 21, "raster_asset", "raster_asset", "character_raster",
                "real_images_intro_character_day3_market_map_fox_generated_20260509", null, true);
        assertVisual(savedCards, 22, "component", "comparison", "component",
                "template_comparison", "\"상장 기업\"", false);
        assertVisual(savedCards, 23, "component", "checklist", "component",
                "template_checklist", "\"자금 조달 통로\"", false);
        assertVisual(savedCards, 24, "raster_asset", "raster_asset", "raster_asset",
                "real_images_generated_examples_intro_day3_trading_hours_phone_20260509", null, true);
        assertVisual(savedCards, 25, "component", "flow", "component", "template_flow", "\"정규장\"", false);
        assertVisual(savedCards, 26, "raster_asset", "raster_asset", "raster_asset",
                "real_images_generated_examples_intro_day3_financial_markets_real_desk_20260509", null, true);
        assertVisual(savedCards, 27, "component", "diagram", "component", "template_diagram", "\"코스피\"", false);
        assertVisual(savedCards, 28, "component", "flow", "component", "template_flow", "\"통화 교환\"", false);
        assertVisual(savedCards, 29, "component", "comparison", "component", "template_comparison", "\"상품시장\"", false);
        assertVisual(savedCards, 30, "component", "flow", "component", "template_flow", "\"금리\"", false);
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
        assertEquals("raster_asset", card.getImageType());
        assertEquals("raster_asset", card.getRendererType());
        assertEquals("raster_asset", card.getVisualType());
        assertEquals("real_images_generated_examples_intro_day3_kospi_kosdaq_real_boards_20260509", card.getVisualKey());
        assertEquals(null, card.getComponentKey());
        assertEquals("real_images_generated_examples_intro_day3_kospi_kosdaq_real_boards_20260509", card.getAssetKey());
        assertEquals("remote_url", card.getImageDelivery());
        assertTrue(card.getImageUrl().contains("intro_day3_kospi_kosdaq_real_boards_20260509.png"));
        assertEquals(null, card.getVisualPayload());
    }

    @Test
    void dayContentReturnsManifestComponentPayloadNotOnlyMetadata() {
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
                        .sourceIdx(22)
                        .assetId("intro-core-d3-card-22")
                        .sheet("입문_카드_FINAL")
                        .track("intro_core")
                        .dayNumber(3)
                        .section("② 상장 기업과 비상장 기업")
                        .cardNumber("1/2")
                        .title("상장 기업과 비상장 기업")
                        .text("본문")
                        .imageType("image")
                        .templateType("content_visual")
                        .visualType("component")
                        .visualKey("template_diagram")
                        .visualJson("{\"alt\":\"상장 기업과 비상장 기업\"}")
                        .visualPayloadJson("{\"template_visual_type\":\"diagram\",\"items\":[{\"text\":\"잘못된 다이어그램\"}]}")
                        .renderPolicyJson("{\"fit\":\"contain\",\"allow_crop\":false}")
                        .build()));
        when(educationQuizRepository.findByTrackAndSectorAndDayNumberOrderByQuizNumberAsc(eq("intro_core"), isNull(), eq(3)))
                .thenReturn(List.of());

        EducationDayContentResponseDTO response = service.getDayContent("intro_core", 3, null);

        EducationCardDTO card = response.getCards().getFirst();
        assertEquals("component", card.getRendererType());
        assertEquals("component", card.getVisualType());
        assertEquals("template_comparison", card.getVisualKey());
        assertEquals("template_comparison", card.getComponentKey());
        assertEquals(null, card.getAssetKey());
        assertEquals("none", card.getImageDelivery());
        assertEquals(null, card.getImageUrl());
        JsonNode payload = card.getVisualPayload();
        assertEquals("comparison", payload.path("type").asText());
        assertTrue(payload.has("left"));
        assertTrue(payload.has("right"));
        assertTrue(payload.toString().contains("상장 기업"));
        assertFalse(payload.toString().contains("잘못된 다이어그램"));
    }

    private void assertVisual(List<EducationCardEntity> savedCards,
                              int idx,
                              String rendererType,
                              String imageType,
                              String visualType,
                              String visualKey,
                              String expectedPayload,
                              boolean requiresImageUrl) {
        EducationCardEntity card = savedCards.stream()
                .filter(item -> Integer.valueOf(idx).equals(item.getSourceIdx()))
                .findFirst()
                .orElse(null);

        assertNotNull(card);
        assertEquals(imageType, card.getImageType());
        assertEquals("content_visual", card.getTemplateType());
        assertEquals(rendererType, card.getRendererType());
        assertEquals(visualType, card.getVisualType());
        assertEquals(visualKey, card.getVisualKey());
        if ("component".equals(visualType)) {
            assertEquals(visualKey, card.getComponentKey());
            assertEquals(null, card.getAssetKey());
            assertEquals("none", card.getImageDelivery());
            assertEquals(null, card.getImageUrl());
            assertTrue(card.getVisualPayloadJson().contains(expectedPayload));
        } else {
            assertEquals(null, card.getComponentKey());
            assertEquals(visualKey, card.getAssetKey());
            assertEquals("remote_url", card.getImageDelivery());
            assertEquals(null, card.getVisualPayloadJson());
        }
        if (requiresImageUrl) {
            assertNotNull(card.getImageUrl());
            assertTrue(card.getImageUrl().startsWith("https://static.uniport.app/education-assets/"));
        }
        assertFalse(String.valueOf(card.getVisualJson()).contains("\"image_url\":\"\""));
    }

}
