package com.uniport.service;

import com.uniport.entity.EducationCardEntity;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

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
}
