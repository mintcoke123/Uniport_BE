package com.uniport.service;

import com.uniport.entity.User;
import com.uniport.entity.UserMyPagePreference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Service
public class ProfileImageUrlService {

    private static final String DEFAULT_PUBLIC_BASE_URL = "https://uniportbe-production.up.railway.app";
    private static final String PROFILE_OPTION_IMAGE_PATH = "/assets/mypage/profile-options/";
    private static final Map<String, String> PROFILE_OPTION_IMAGE_FILES = Map.of(
            "SEED", "seed.png",
            "PANDA", "panda.png",
            "DOLPHIN", "dolphin.png",
            "RESEARCHER", "researcher.png",
            "FOX", "fox.png",
            "FARMER", "farmer.png",
            "OWL", "owl.png",
            "SURFER", "surfer.png"
    );
    private static final Map<String, String> ONBOARDING_CHARACTER_CODES = Map.ofEntries(
            Map.entry(normalizeCharacterName("조심스러운 거북이"), "SEED"),
            Map.entry(normalizeCharacterName("조심스러운 거북이형"), "SEED"),
            Map.entry(normalizeCharacterName("균형 잡힌 판다"), "PANDA"),
            Map.entry(normalizeCharacterName("균형잡힌 판다형"), "PANDA"),
            Map.entry(normalizeCharacterName("감각형 돌고래"), "DOLPHIN"),
            Map.entry(normalizeCharacterName("감각적인 돌고래형"), "DOLPHIN"),
            Map.entry(normalizeCharacterName("호기심 많은 연구자"), "RESEARCHER"),
            Map.entry(normalizeCharacterName("호기심 많은 탐구자형"), "RESEARCHER"),
            Map.entry(normalizeCharacterName("호기심 많은 치타"), "FOX"),
            Map.entry(normalizeCharacterName("기회를 찾는 여우형"), "FOX"),
            Map.entry(normalizeCharacterName("성실한 농부"), "FARMER"),
            Map.entry(normalizeCharacterName("성실한 농부형"), "FARMER"),
            Map.entry(normalizeCharacterName("전략 짜는 올빼미"), "OWL"),
            Map.entry(normalizeCharacterName("전략적인 올빼미형"), "OWL"),
            Map.entry(normalizeCharacterName("파도타는 서퍼"), "SURFER"),
            Map.entry(normalizeCharacterName("파도타는 서퍼형"), "SURFER")
    );

    @Value("${app.public-base-url:https://uniportbe-production.up.railway.app}")
    private String publicBaseUrl = DEFAULT_PUBLIC_BASE_URL;

    public String profileOptionImageUrl(String code) {
        String selectedCode = code == null || code.isBlank() ? "SEED" : code.trim().toUpperCase(Locale.ROOT);
        String fileName = PROFILE_OPTION_IMAGE_FILES.getOrDefault(selectedCode, PROFILE_OPTION_IMAGE_FILES.get("SEED"));
        return normalizePublicBaseUrl(publicBaseUrl) + PROFILE_OPTION_IMAGE_PATH + fileName;
    }

    public String profileOptionCodeForCharacterName(String characterName) {
        return ONBOARDING_CHARACTER_CODES.getOrDefault(normalizeCharacterName(characterName), "SEED");
    }

    public boolean isProfileOptionImageUrl(String profileImageUrl) {
        return profileOptionCodeForProfileImageValue(profileImageUrl) != null;
    }

    public String normalizeProfileImageUrl(String profileImageUrl) {
        if (profileImageUrl == null) {
            return null;
        }
        String trimmed = profileImageUrl.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        String profileOptionCode = profileOptionCodeForProfileImageValue(trimmed);
        return profileOptionCode == null ? trimmed : profileOptionImageUrl(profileOptionCode);
    }

    public String resolveCharacterProfileImageUrl(User user, UserMyPagePreference preference) {
        if (user != null && user.getProfileImageUrl() != null && !user.getProfileImageUrl().isBlank()) {
            return normalizeProfileImageUrl(user.getProfileImageUrl());
        }
        if (preference != null && preference.getSelectedCharacterCode() != null && !preference.getSelectedCharacterCode().isBlank()) {
            return profileOptionImageUrl(preference.getSelectedCharacterCode());
        }
        return profileOptionImageUrl(profileOptionCodeForCharacterName(user == null ? null : user.getInvestmentProfileResult()));
    }

    private String profileOptionCodeForProfileImageValue(String profileImageUrl) {
        if (profileImageUrl == null || profileImageUrl.isBlank()) {
            return null;
        }
        String normalizedValue = profileImageUrl.trim().toLowerCase(Locale.ROOT);
        String normalizedPath = PROFILE_OPTION_IMAGE_PATH.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : PROFILE_OPTION_IMAGE_FILES.entrySet()) {
            String code = entry.getKey();
            String fileName = entry.getValue().toLowerCase(Locale.ROOT);
            String segment = fileName.endsWith(".png")
                    ? fileName.substring(0, fileName.length() - ".png".length())
                    : fileName;
            if (normalizedValue.equals(segment)
                    || normalizedValue.equals(code.toLowerCase(Locale.ROOT))
                    || normalizedValue.startsWith("character_" + segment)
                    || normalizedValue.endsWith(normalizedPath + fileName)) {
                return code;
            }
        }
        return null;
    }

    private String normalizePublicBaseUrl(String value) {
        String normalized = value == null || value.isBlank() ? DEFAULT_PUBLIC_BASE_URL : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizeCharacterName(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }
}
