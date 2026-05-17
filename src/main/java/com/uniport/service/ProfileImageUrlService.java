package com.uniport.service;

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

    @Value("${app.public-base-url:https://uniportbe-production.up.railway.app}")
    private String publicBaseUrl = DEFAULT_PUBLIC_BASE_URL;

    public String profileOptionImageUrl(String code) {
        String selectedCode = code == null || code.isBlank() ? "SEED" : code.trim().toUpperCase(Locale.ROOT);
        String fileName = PROFILE_OPTION_IMAGE_FILES.getOrDefault(selectedCode, PROFILE_OPTION_IMAGE_FILES.get("SEED"));
        return normalizePublicBaseUrl(publicBaseUrl) + PROFILE_OPTION_IMAGE_PATH + fileName;
    }

    private String normalizePublicBaseUrl(String value) {
        String normalized = value == null || value.isBlank() ? DEFAULT_PUBLIC_BASE_URL : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
