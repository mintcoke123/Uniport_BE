package com.uniport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.entity.OnboardingResultCatalog;
import com.uniport.repository.OnboardingResultCatalogRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

final class OnboardingResultProviderTestFactory {

    private static final TypeReference<List<CatalogSeedItem>> CATALOG_SEED_TYPE = new TypeReference<>() {};

    private OnboardingResultProviderTestFactory() {
    }

    static OnboardingResultProvider create() {
        ObjectMapper objectMapper = new ObjectMapper();
        List<OnboardingResultCatalog> catalogs = loadCatalogs(objectMapper);
        OnboardingResultCatalogRepository repository = mock(OnboardingResultCatalogRepository.class);
        lenient().when(repository.findAllByActiveTrueOrderByCharacterIdAsc()).thenReturn(catalogs);
        for (OnboardingResultCatalog catalog : catalogs) {
            lenient().when(repository.findByCharacterIdAndActiveTrue(catalog.getCharacterId())).thenReturn(Optional.of(catalog));
        }
        return new OnboardingResultProvider(repository, objectMapper);
    }

    private static List<OnboardingResultCatalog> loadCatalogs(ObjectMapper objectMapper) {
        try (InputStream inputStream = OnboardingResultProviderTestFactory.class
                .getClassLoader()
                .getResourceAsStream("onboarding/onboarding-result-catalog.json")) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing onboarding result catalog test resource");
            }
            return objectMapper.readValue(inputStream, CATALOG_SEED_TYPE).stream()
                    .map(seedItem -> toCatalog(seedItem, objectMapper))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load onboarding result catalog test resource", exception);
        }
    }

    private static OnboardingResultCatalog toCatalog(CatalogSeedItem item, ObjectMapper objectMapper) {
        return OnboardingResultCatalog.builder()
                .characterId(item.characterId())
                .profileKey(item.profileKey())
                .canonicalName(item.canonicalName())
                .legacyAliasesJson(writeList(objectMapper, item.legacyAliases()))
                .levelLabel(item.levelLabel())
                .cardSummary(item.cardSummary())
                .investmentType(item.investmentType())
                .analysisTitle(item.analysisTitle())
                .analysisSubtitle(item.analysisSubtitle())
                .traitsJson(writeList(objectMapper, item.traits()))
                .traitDescriptionsJson(writeList(objectMapper, item.traitDescriptions()))
                .principlesJson(writeList(objectMapper, item.principles()))
                .principleDescriptionsJson(writeList(objectMapper, item.principleDescriptions()))
                .strategiesJson(writeList(objectMapper, item.strategies()))
                .strategyHighlightsJson(writeList(objectMapper, item.strategyHighlights()))
                .characterImageResource(item.characterImageResource())
                .characterAssetUrl(item.characterAssetUrl())
                .active(Boolean.TRUE)
                .build();
    }

    private static String writeList(ObjectMapper objectMapper, Object values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize onboarding result catalog test field", exception);
        }
    }

    private record CatalogSeedItem(
            int characterId,
            String profileKey,
            String canonicalName,
            List<String> legacyAliases,
            String levelLabel,
            String cardSummary,
            String investmentType,
            String analysisTitle,
            String analysisSubtitle,
            List<String> traits,
            List<String> traitDescriptions,
            List<String> principles,
            List<String> principleDescriptions,
            List<String> strategies,
            List<List<String>> strategyHighlights,
            String characterImageResource,
            String characterAssetUrl
    ) {
    }
}
