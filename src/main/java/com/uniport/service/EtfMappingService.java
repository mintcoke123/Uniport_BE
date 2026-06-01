package com.uniport.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class EtfMappingService {

    private static final String MARKET_KRX = "KRX";

    private static final List<EtfThemeDefinition> ETF_THEMES = List.of(
            new EtfThemeDefinition(
                    "반도체",
                    List.of("반도체", "HBM", "AI반도체", "AI 반도체"),
                    List.of(
                            new EtfDefinition("KODEX 반도체", "091160"),
                            new EtfDefinition("TIGER 반도체", "ETF_TIGER_SEMICONDUCTOR")
                    )
            ),
            new EtfThemeDefinition(
                    "2차전지",
                    List.of("2차전지", "배터리", "전기차"),
                    List.of(
                            new EtfDefinition("TIGER 2차전지테마", "ETF_TIGER_BATTERY_THEME"),
                            new EtfDefinition("KODEX 2차전지산업", "ETF_KODEX_BATTERY_INDUSTRY")
                    )
            ),
            new EtfThemeDefinition(
                    "AI/빅테크",
                    List.of("AI/빅테크", "AI", "빅테크", "AI서버", "AI 서버", "데이터센터", "클라우드"),
                    List.of(
                            new EtfDefinition("TIGER 미국테크TOP10", "ETF_TIGER_US_TECH_TOP10"),
                            new EtfDefinition("KODEX 미국나스닥100", "ETF_KODEX_US_NASDAQ100")
                    )
            ),
            new EtfThemeDefinition(
                    "방산",
                    List.of("방산"),
                    List.of(
                            new EtfDefinition("PLUS K방산", "ETF_PLUS_K_DEFENSE"),
                            new EtfDefinition("HANARO Fn K-방산", "ETF_HANARO_FN_K_DEFENSE")
                    )
            ),
            new EtfThemeDefinition(
                    "원전",
                    List.of("원전"),
                    List.of(
                            new EtfDefinition("ACE 원자력테마딥서치", "ETF_ACE_NUCLEAR_DEEPSEARCH"),
                            new EtfDefinition("HANARO 원자력iSelect", "ETF_HANARO_NUCLEAR_ISELECT")
                    )
            )
    );

    public List<MappedEtf> mapEtfs(List<String> themes) {
        if (themes == null || themes.isEmpty()) {
            return List.of();
        }

        List<MappedEtf> mappedEtfs = new ArrayList<>();
        Set<String> seenNames = new LinkedHashSet<>();
        for (String theme : themes) {
            String normalizedTheme = normalize(theme);
            if (normalizedTheme.isBlank()) {
                continue;
            }
            for (EtfThemeDefinition definition : ETF_THEMES) {
                if (!definition.matches(normalizedTheme)) {
                    continue;
                }
                for (EtfDefinition etf : definition.etfs()) {
                    if (seenNames.add(etf.name())) {
                        mappedEtfs.add(new MappedEtf(etf.name(), etf.symbol(), definition.theme(), MARKET_KRX));
                    }
                }
            }
        }
        return List.copyOf(mappedEtfs);
    }

    public List<MappedEtf> mapEtfs(String... themes) {
        if (themes == null || themes.length == 0) {
            return List.of();
        }
        return mapEtfs(Arrays.asList(themes));
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
    }

    private record EtfThemeDefinition(String theme, List<String> aliases, List<EtfDefinition> etfs) {

        private boolean matches(String normalizedTheme) {
            return aliases.stream()
                    .map(alias -> alias.toUpperCase(Locale.ROOT))
                    .anyMatch(normalizedTheme::equals);
        }
    }

    private record EtfDefinition(String name, String symbol) {
    }
}
