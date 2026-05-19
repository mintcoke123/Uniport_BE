package com.uniport.service;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class RawNewsNormalizer {

    private static final Set<String> IGNORED_TITLE_TOKENS = Set.of("마감시황", "속보", "종합", "단독", "뉴스", "시황");

    public String cleanDisplayText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return HtmlUtils.htmlUnescape(value)
                .replaceAll("<[^>]+>", "")
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    public List<String> titleTokens(String title) {
        if (title == null || title.isBlank()) {
            return List.of();
        }
        String normalized = cleanDisplayText(title)
                .replaceAll("(?<=\\d),(?=\\d)", "")
                .replaceAll("\\[[^\\]]+]", " ")
                .replaceAll("[^0-9A-Za-z가-힣外國人兆↑↓]+", " ");
        List<String> tokens = new ArrayList<>();
        for (String rawToken : normalized.split("\\s+")) {
            String token = canonicalToken(rawToken);
            if (!token.isBlank() && !tokens.contains(token)) {
                tokens.add(token);
            }
        }
        return List.copyOf(tokens);
    }

    public String canonicalToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return "";
        }
        String token = rawToken.trim()
                .replace("外國人", "외국인")
                .replace("外人", "외국인")
                .replace("외인", "외국인")
                .replace("兆", "조")
                .replace("↓", "하락")
                .replace("↑", "상승")
                .toUpperCase(Locale.ROOT)
                .replaceAll("(으로|에서|에게|까지|부터|보다|에는|으로는|에|이|가|은|는|을|를|과|와|도)$", "");
        if (token.contains("외국인")) {
            return "외국인";
        }
        if (token.contains("순매도") || token.contains("매도") || token.contains("팔자") || token.contains("투매")) {
            return "매도";
        }
        if (token.contains("급락") || token.contains("하락") || token.contains("후퇴")
                || token.contains("내린") || token.contains("붕괴") || token.contains("털썩")) {
            return "하락";
        }
        if (token.matches("\\d+조.*")) {
            return token.replaceAll("^(\\d+)조.*", "$1조");
        }
        if (token.matches("\\d{4}선.*")) {
            return token.substring(0, 4) + "선";
        }
        if (IGNORED_TITLE_TOKENS.contains(token) || token.length() < 2) {
            return "";
        }
        return token;
    }
}
