package com.uniport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "onboarding_result_catalog")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class OnboardingResultCatalog {

    @Id
    @Column(name = "character_id", nullable = false)
    private Integer characterId;

    @Column(name = "profile_key", nullable = false, unique = true, length = 80)
    private String profileKey;

    @Column(name = "canonical_name", nullable = false, length = 80)
    private String canonicalName;

    @Column(name = "legacy_aliases_json", nullable = false, columnDefinition = "TEXT")
    private String legacyAliasesJson;

    @Column(name = "level_label", nullable = false, length = 20)
    private String levelLabel;

    @Column(name = "card_summary", nullable = false, columnDefinition = "TEXT")
    private String cardSummary;

    @Column(name = "investment_type", nullable = false, columnDefinition = "TEXT")
    private String investmentType;

    @Column(name = "analysis_title", nullable = false, columnDefinition = "TEXT")
    private String analysisTitle;

    @Column(name = "analysis_subtitle", nullable = false, columnDefinition = "TEXT")
    private String analysisSubtitle;

    @Column(name = "traits_json", nullable = false, columnDefinition = "TEXT")
    private String traitsJson;

    @Column(name = "trait_descriptions_json", nullable = false, columnDefinition = "TEXT")
    private String traitDescriptionsJson;

    @Column(name = "principles_json", nullable = false, columnDefinition = "TEXT")
    private String principlesJson;

    @Column(name = "principle_descriptions_json", nullable = false, columnDefinition = "TEXT")
    private String principleDescriptionsJson;

    @Column(name = "strategies_json", nullable = false, columnDefinition = "TEXT")
    private String strategiesJson;

    @Column(name = "character_image_resource", nullable = false, length = 120)
    private String characterImageResource;

    @Column(name = "character_asset_url", nullable = false, columnDefinition = "TEXT")
    private String characterAssetUrl;

    @Column(nullable = false)
    private Boolean active;
}
