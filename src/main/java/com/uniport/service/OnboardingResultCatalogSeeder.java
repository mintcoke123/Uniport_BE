package com.uniport.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.entity.OnboardingResultCatalog;
import com.uniport.repository.OnboardingResultCatalogRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.List;

@Component
public class OnboardingResultCatalogSeeder implements ApplicationRunner {

    private static final TypeReference<List<CatalogSeedItem>> CATALOG_SEED_TYPE = new TypeReference<>() {};

    private final OnboardingResultCatalogRepository repository;
    private final ObjectMapper objectMapper;
    private final Resource catalogResource;

    public OnboardingResultCatalogSeeder(OnboardingResultCatalogRepository repository,
                                         ObjectMapper objectMapper,
                                         @Value("classpath:onboarding/onboarding-result-catalog.json") Resource catalogResource) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.catalogResource = catalogResource;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<CatalogSeedItem> seedItems = readSeedItems();
        List<OnboardingResultCatalog> catalogs = seedItems.stream()
                .map(this::toCatalog)
                .toList();
        repository.saveAll(catalogs);
    }

    private List<CatalogSeedItem> readSeedItems() {
        try {
            return objectMapper.readValue(catalogResource.getInputStream(), CATALOG_SEED_TYPE);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load onboarding result catalog seed", exception);
        }
    }

    private OnboardingResultCatalog toCatalog(CatalogSeedItem item) {
        OnboardingResultCatalog catalog = repository.findById(item.characterId())
                .orElseGet(() -> OnboardingResultCatalog.builder()
                        .characterId(item.characterId())
                        .build());
        catalog.setProfileKey(required(item.profileKey(), "profileKey", item.characterId()));
        catalog.setCanonicalName(required(item.canonicalName(), "canonicalName", item.characterId()));
        catalog.setLegacyAliasesJson(writeList(item.legacyAliases(), "legacyAliases", item.characterId()));
        catalog.setLevelLabel(required(item.levelLabel(), "levelLabel", item.characterId()));
        catalog.setCardSummary(required(item.cardSummary(), "cardSummary", item.characterId()));
        catalog.setInvestmentType(required(item.investmentType(), "investmentType", item.characterId()));
        catalog.setAnalysisTitle(required(item.analysisTitle(), "analysisTitle", item.characterId()));
        catalog.setAnalysisSubtitle(required(item.analysisSubtitle(), "analysisSubtitle", item.characterId()));
        catalog.setTraitsJson(writeRequiredList(item.traits(), "traits", item.characterId()));
        catalog.setTraitDescriptionsJson(writeRequiredList(item.traitDescriptions(), "traitDescriptions", item.characterId()));
        catalog.setPrinciplesJson(writeRequiredList(item.principles(), "principles", item.characterId()));
        catalog.setPrincipleDescriptionsJson(writeRequiredList(item.principleDescriptions(), "principleDescriptions", item.characterId()));
        catalog.setStrategiesJson(writeRequiredList(item.strategies(), "strategies", item.characterId()));
        catalog.setStrategyHighlightsJson(writeRequiredHighlights(item.strategies(), item.strategyHighlights(), item.characterId()));
        catalog.setCharacterImageResource(required(item.characterImageResource(), "characterImageResource", item.characterId()));
        catalog.setCharacterAssetUrl(required(item.characterAssetUrl(), "characterAssetUrl", item.characterId()));
        catalog.setActive(Boolean.TRUE);
        return catalog;
    }

    private String writeRequiredList(List<String> values, String fieldName, int characterId) {
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException("Missing onboarding result catalog field " + fieldName + " for character " + characterId);
        }
        return writeList(values, fieldName, characterId);
    }

    private String writeRequiredHighlights(List<String> strategies, List<List<String>> highlights, int characterId) {
        if (strategies == null || strategies.isEmpty()) {
            throw new IllegalStateException("Missing onboarding result catalog field strategies for character " + characterId);
        }
        if (highlights == null || highlights.size() != strategies.size()) {
            throw new IllegalStateException(
                    "Missing onboarding result catalog field strategyHighlights for character " + characterId);
        }
        for (int index = 0; index < highlights.size(); index++) {
            List<String> itemHighlights = highlights.get(index);
            if (itemHighlights == null || itemHighlights.isEmpty()) {
                throw new IllegalStateException(
                        "Missing onboarding result catalog field strategyHighlights[" + index + "] for character " + characterId);
            }
            for (String highlight : itemHighlights) {
                validateHighlight(strategies.get(index), highlight, characterId);
            }
        }
        return writeList(highlights, "strategyHighlights", characterId);
    }

    private String writeList(Object values, String fieldName, int characterId) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize onboarding result catalog field " + fieldName + " for character " + characterId, exception);
        }
    }

    private String required(String value, String fieldName, int characterId) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing onboarding result catalog field " + fieldName + " for character " + characterId);
        }
        return value.trim();
    }

    private void validateHighlight(String strategy, String highlight, int characterId) {
        if (highlight == null || highlight.isBlank()) {
            throw new IllegalStateException("Blank onboarding result catalog strategyHighlight for character " + characterId);
        }
        if (strategy == null || !strategy.contains(highlight.trim())) {
            throw new IllegalStateException(
                    "Onboarding result catalog strategyHighlight is not contained in strategy for character " + characterId);
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
