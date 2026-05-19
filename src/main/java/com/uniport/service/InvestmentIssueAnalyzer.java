package com.uniport.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class InvestmentIssueAnalyzer {

    private static final List<Cue> POSITIVE_CUES = List.of(
            new Cue("HBM", 1),
            new Cue("AI", 1),
            new Cue("수요 증가", 3),
            new Cue("강세", 2),
            new Cue("상승", 2),
            new Cue("수주", 2),
            new Cue("실적 개선", 3),
            new Cue("흑자", 3),
            new Cue("서프라이즈", 3)
    );

    private static final List<Cue> NEGATIVE_CUES = List.of(
            new Cue("급락", 3),
            new Cue("하락", 2),
            new Cue("약세", 2),
            new Cue("우려", 2),
            new Cue("쇼크", 3),
            new Cue("적자", 3),
            new Cue("감소", 3),
            new Cue("둔화", 2),
            new Cue("규제 강화", 3),
            new Cue("파업", 3),
            new Cue("비용 부담", 2)
    );

    private static final List<Cue> NEUTRAL_CUES = List.of(
            new Cue("FOMC", 2),
            new Cue("관망", 2),
            new Cue("대기", 2),
            new Cue("방향성 탐색", 2),
            new Cue("동결 여부", 2),
            new Cue("발표 앞두고", 2)
    );

    private static final List<Cue> MIXED_CUES = List.of(
            new Cue("환율 상승", 4),
            new Cue("유가 상승", 4),
            new Cue("금리 상승", 4),
            new Cue("수혜와 비용 부담", 3),
            new Cue("수혜와 피해", 3),
            new Cue("업종이 갈", 2),
            new Cue("업종별 영향", 2)
    );

    private static final List<Replacement> FORBIDDEN_REPLACEMENTS = List.of(
            new Replacement("비중을 늘려라", "노출을 키워라"),
            new Replacement("비중을 줄여라", "노출을 낮춰라"),
            new Replacement("추천한다", "제시한다"),
            new Replacement("팔아라", "정리하라"),
            new Replacement("사라", "확보하라"),
            new Replacement("매수", "유입"),
            new Replacement("매도", "유출")
    );

    private final StockMappingService stockMappingService;
    private final EtfMappingService etfMappingService;

    public InvestmentIssueAnalyzer(StockMappingService stockMappingService, EtfMappingService etfMappingService) {
        this.stockMappingService = stockMappingService;
        this.etfMappingService = etfMappingService;
    }

    public InvestmentIssue analyze(IssueCluster cluster) {
        Objects.requireNonNull(cluster, "cluster must not be null");

        String searchableText = searchableText(cluster);
        MatchedCues matchedCues = matchCues(searchableText);
        InvestmentIssueLabel label = classify(matchedCues);
        List<MappedStock> relatedStocks = stockMappingService.mapStocks(searchableText);
        List<MappedEtf> relatedEtfs = etfMappingService.mapEtfs(relatedThemes(cluster, searchableText));
        List<FetchedNewsArticle> sourceArticles = cluster.articles();

        return new InvestmentIssue(
                cluster.clusterKey(),
                cluster.category(),
                cluster.mainEntity(),
                cluster.mainEvent(),
                sanitizeGeneratedText(buildTitle(cluster)),
                label,
                sanitizeGeneratedText(buildSummary(label, cluster)),
                sanitizeGeneratedTexts(buildReasonBullets(label, matchedCues, relatedStocks, relatedEtfs, sourceArticles)),
                sanitizeGeneratedTexts(buildWatchPoints(label)),
                relatedStocks,
                relatedEtfs,
                sourceArticles,
                sourceArticles.size(),
                firstPublishedAt(sourceArticles),
                latestPublishedAt(sourceArticles)
        );
    }

    private InvestmentIssueLabel classify(MatchedCues matchedCues) {
        int positiveScore = score(matchedCues.positive());
        int negativeScore = score(matchedCues.negative());
        int neutralScore = score(matchedCues.neutral());
        int mixedScore = score(matchedCues.mixed());

        if (hasSevereNegativeCue(matchedCues.negative()) && mixedScore == 0) {
            return InvestmentIssueLabel.NEGATIVE;
        }
        if (negativeScore >= 4 && negativeScore > positiveScore) {
            return InvestmentIssueLabel.NEGATIVE;
        }
        if (mixedScore > 0 && negativeScore < 4) {
            return InvestmentIssueLabel.MIXED;
        }
        if (negativeScore > positiveScore && negativeScore >= neutralScore) {
            return InvestmentIssueLabel.NEGATIVE;
        }
        if (positiveScore > negativeScore && positiveScore >= neutralScore) {
            return InvestmentIssueLabel.POSITIVE;
        }
        if (mixedScore > 0) {
            return InvestmentIssueLabel.MIXED;
        }
        if (neutralScore > 0) {
            return InvestmentIssueLabel.NEUTRAL;
        }
        if (negativeScore > 0) {
            return InvestmentIssueLabel.NEGATIVE;
        }
        if (positiveScore > 0) {
            return InvestmentIssueLabel.POSITIVE;
        }
        return InvestmentIssueLabel.NEUTRAL;
    }

    private MatchedCues matchCues(String text) {
        return new MatchedCues(
                matches(POSITIVE_CUES, text),
                matches(NEGATIVE_CUES, text),
                matches(NEUTRAL_CUES, text),
                matches(MIXED_CUES, text)
        );
    }

    private List<Cue> matches(List<Cue> cues, String text) {
        return cues.stream()
                .filter(cue -> containsNeedle(text, cue.text()))
                .toList();
    }

    private int score(List<Cue> cues) {
        return cues.stream()
                .mapToInt(Cue::weight)
                .sum();
    }

    private boolean hasSevereNegativeCue(List<Cue> cues) {
        return cues.stream()
                .anyMatch(cue -> cue.weight() >= 3 || cue.text().equals("비용 부담"));
    }

    private String buildTitle(IssueCluster cluster) {
        String mainEntity = clean(cluster.mainEntity());
        String mainEvent = clean(cluster.mainEvent());
        String articleTitle = bestArticleTitle(cluster);
        String generatedTitle = "";
        if (!mainEntity.isBlank() && !mainEvent.isBlank()) {
            generatedTitle = mainEntity + " " + mainEvent;
        } else if (!mainEntity.isBlank()) {
            generatedTitle = mainEntity;
        } else if (!mainEvent.isBlank()) {
            generatedTitle = mainEvent;
        }

        if (shouldPreferArticleTitle(generatedTitle, articleTitle, mainEntity)) {
            return articleTitle;
        }
        if (!generatedTitle.isBlank()) {
            return generatedTitle;
        }
        return articleTitle.isBlank() ? "시장 이슈" : articleTitle;
    }

    private String bestArticleTitle(IssueCluster cluster) {
        return cluster.articles().stream()
                .map(FetchedNewsArticle::getTitle)
                .map(this::cleanEvidenceText)
                .filter(title -> !title.isBlank())
                .findFirst()
                .orElse("");
    }

    private boolean shouldPreferArticleTitle(String generatedTitle, String articleTitle, String mainEntity) {
        if (articleTitle.isBlank()) {
            return false;
        }
        if (generatedTitle.isBlank()) {
            return true;
        }
        if (isGenericGeneratedTitle(generatedTitle)) {
            return true;
        }
        return articleTitle.length() >= generatedTitle.length() + 6
                && sharesMainEntity(articleTitle, mainEntity);
    }

    private boolean isGenericGeneratedTitle(String generatedTitle) {
        String normalizedTitle = clean(generatedTitle);
        return normalizedTitle.equals("시장")
                || normalizedTitle.equals("시장 기타")
                || normalizedTitle.equals("시장 이슈")
                || normalizedTitle.endsWith(" 기타")
                || normalizedTitle.endsWith(" 실적")
                || normalizedTitle.endsWith(" 상승")
                || normalizedTitle.endsWith(" 하락")
                || normalizedTitle.endsWith(" 규제")
                || normalizedTitle.endsWith(" 파업");
    }

    private boolean sharesMainEntity(String articleTitle, String mainEntity) {
        String normalizedEntity = normalize(mainEntity);
        if (normalizedEntity.isBlank() || normalizedEntity.equals("시장")) {
            return true;
        }
        return normalize(articleTitle).contains(normalizedEntity);
    }

    private String buildSummary(InvestmentIssueLabel label, IssueCluster cluster) {
        String subject = subject(cluster);
        List<String> evidenceSnippets = evidenceSnippets(cluster.articles(), 2);
        if (!evidenceSnippets.isEmpty()) {
            return subject + " 이슈입니다. " + String.join(" ", evidenceSnippets);
        }
        return switch (label) {
            case POSITIVE -> subject + " 관련 실적 또는 수요 기대가 강화된 이슈입니다.";
            case NEGATIVE -> subject + " 관련 불확실성과 부담 요인이 커진 이슈입니다.";
            case MIXED -> subject + " 관련 업종별 영향이 엇갈리는 재료입니다.";
            case NEUTRAL -> subject + " 관련 확인 대기 흐름이 이어지는 이슈입니다.";
        };
    }

    private List<String> buildReasonBullets(InvestmentIssueLabel label,
                                            MatchedCues matchedCues,
                                            List<MappedStock> relatedStocks,
                                            List<MappedEtf> relatedEtfs,
                                            List<FetchedNewsArticle> sourceArticles) {
        List<String> reasons = new ArrayList<>();
        List<Cue> labelCues = switch (label) {
            case POSITIVE -> matchedCues.positive();
            case NEGATIVE -> matchedCues.negative();
            case NEUTRAL -> matchedCues.neutral();
            case MIXED -> matchedCues.mixed();
        };

        if (labelCues.isEmpty()) {
            reasons.add("기사 흐름과 대표 이벤트를 기준으로 " + label.labelText() + " 성격을 판단했어요.");
        } else {
            reasons.add(label.labelText() + " 판단 단서: " + cueTexts(labelCues));
        }
        evidenceSnippets(sourceArticles, 2).forEach(snippet -> reasons.add("기사 핵심: " + snippet));
        if (sourceArticles.size() > 1) {
            reasons.add("같은 이슈로 묶인 기사 " + sourceArticles.size() + "건에서 반복 확인됐어요.");
        }
        if (!relatedStocks.isEmpty() || !relatedEtfs.isEmpty()) {
            reasons.add("연관 자산 후보가 함께 확인돼 후속 영향 점검이 필요해요.");
        }
        return List.copyOf(reasons);
    }

    private List<String> buildWatchPoints(InvestmentIssueLabel label) {
        return switch (label) {
            case POSITIVE -> List.of(
                    "수요와 실적 개선 흐름이 다음 기사에서도 반복되는지 확인하세요.",
                    "관련 기업의 공급 일정과 실적 발표를 함께 확인하세요."
            );
            case NEGATIVE -> List.of(
                    "실적 전망과 비용 부담이 추가로 커지는지 확인하세요.",
                    "주가 변동이 단기 이슈인지 업종 전반 흐름인지 구분하세요."
            );
            case MIXED -> List.of(
                    "수혜 업종과 부담 업종이 어떻게 나뉘는지 확인하세요.",
                    "환율, 유가, 금리 같은 변수의 방향 전환 여부를 확인하세요."
            );
            case NEUTRAL -> List.of(
                    "발표 이후 시장 해석이 한쪽으로 쏠리는지 확인하세요.",
                    "대기 흐름이 거래량과 변동성 변화로 이어지는지 확인하세요."
            );
        };
    }

    private String cueTexts(List<Cue> cues) {
        return cues.stream()
                .map(Cue::text)
                .distinct()
                .toList()
                .stream()
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private List<String> evidenceSnippets(List<FetchedNewsArticle> articles, int limit) {
        List<String> snippets = new ArrayList<>();
        for (FetchedNewsArticle article : articles) {
            addEvidenceSnippet(snippets, article.getSummary(), limit);
            if (snippets.size() >= limit) {
                break;
            }
            addEvidenceSnippet(snippets, article.getTitle(), limit);
            if (snippets.size() >= limit) {
                break;
            }
        }
        return List.copyOf(snippets);
    }

    private void addEvidenceSnippet(List<String> snippets, String value, int limit) {
        if (snippets.size() >= limit) {
            return;
        }
        String snippet = cleanEvidenceText(value);
        if (snippet.length() < 8 || snippets.contains(snippet)) {
            return;
        }
        snippets.add(snippet);
    }

    private String cleanEvidenceText(String value) {
        String cleaned = clean(value)
                .replaceAll("\\.{2,}|…", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.length() <= 90) {
            return cleaned;
        }
        return cleaned.substring(0, 90).replaceAll("\\s+\\S*$", "").trim();
    }

    private String subject(IssueCluster cluster) {
        String mainEntity = clean(cluster.mainEntity());
        if (!mainEntity.isBlank()) {
            return mainEntity;
        }
        InvestmentIssueCategory category = cluster.category();
        return category == null ? "시장" : category.label();
    }

    private List<String> relatedThemes(IssueCluster cluster, String searchableText) {
        Set<String> themes = new LinkedHashSet<>();
        addIfPresent(themes, cluster.mainEntity());
        addIfPresent(themes, cluster.mainEvent());
        addThemeWhenTextContains(themes, searchableText, "HBM", "HBM");
        addThemeWhenTextContains(themes, searchableText, "AI 반도체", "AI반도체");
        addThemeWhenTextContains(themes, searchableText, "AI반도체", "AI반도체");
        addThemeWhenTextContains(themes, searchableText, "반도체", "반도체");
        addThemeWhenTextContains(themes, searchableText, "AI", "AI");
        addThemeWhenTextContains(themes, searchableText, "빅테크", "빅테크");
        addThemeWhenTextContains(themes, searchableText, "2차전지", "2차전지");
        addThemeWhenTextContains(themes, searchableText, "배터리", "배터리");
        addThemeWhenTextContains(themes, searchableText, "전기차", "전기차");
        addThemeWhenTextContains(themes, searchableText, "방산", "방산");
        addThemeWhenTextContains(themes, searchableText, "원전", "원전");
        return List.copyOf(themes);
    }

    private void addThemeWhenTextContains(Set<String> themes, String text, String needle, String theme) {
        if (containsNeedle(text, needle)) {
            addIfPresent(themes, theme);
        }
    }

    private void addIfPresent(Set<String> values, String value) {
        String cleaned = clean(value);
        if (!cleaned.isBlank()) {
            values.add(cleaned);
        }
    }

    private LocalDateTime firstPublishedAt(List<FetchedNewsArticle> sourceArticles) {
        return sourceArticles.stream()
                .map(FetchedNewsArticle::getPublishedAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private LocalDateTime latestPublishedAt(List<FetchedNewsArticle> sourceArticles) {
        return sourceArticles.stream()
                .map(FetchedNewsArticle::getPublishedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private String searchableText(IssueCluster cluster) {
        List<String> texts = new ArrayList<>();
        texts.add(cluster.mainEntity());
        texts.add(cluster.mainEvent());
        for (FetchedNewsArticle article : cluster.articles()) {
            texts.add(article.getTitle());
            texts.add(article.getSummary());
            texts.add(article.getContent());
        }
        return normalize(String.join(" ", texts.stream()
                .filter(Objects::nonNull)
                .toList()));
    }

    private String clean(String text) {
        return text == null ? "" : text.trim();
    }

    private String normalize(String text) {
        return clean(text).toUpperCase(Locale.ROOT);
    }

    private boolean containsNeedle(String text, String needle) {
        String normalizedNeedle = normalize(needle);
        if (normalizedNeedle.isBlank()) {
            return false;
        }
        if (!isPureEnglishAcronym(normalizedNeedle)) {
            return text.contains(normalizedNeedle);
        }

        int searchFrom = 0;
        while (searchFrom <= text.length() - normalizedNeedle.length()) {
            int index = text.indexOf(normalizedNeedle, searchFrom);
            if (index < 0) {
                return false;
            }
            if (hasEnglishTokenBoundaries(text, index, normalizedNeedle.length())) {
                return true;
            }
            searchFrom = index + 1;
        }
        return false;
    }

    private boolean isPureEnglishAcronym(String text) {
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character < 'A' || character > 'Z') {
                return false;
            }
        }
        return true;
    }

    private boolean hasEnglishTokenBoundaries(String text, int index, int length) {
        int before = index - 1;
        int after = index + length;
        return (before < 0 || !isEnglishTokenCharacter(text.charAt(before)))
                && (after >= text.length() || !isEnglishTokenCharacter(text.charAt(after)));
    }

    private boolean isEnglishTokenCharacter(char character) {
        return (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9')
                || character == '_';
    }

    private List<String> sanitizeGeneratedTexts(List<String> texts) {
        return texts.stream()
                .map(this::sanitizeGeneratedText)
                .toList();
    }

    private String sanitizeGeneratedText(String text) {
        String sanitized = text == null ? "" : text;
        for (Replacement replacement : FORBIDDEN_REPLACEMENTS) {
            sanitized = sanitized.replace(replacement.forbidden(), replacement.safe());
        }
        return sanitized;
    }

    private record Cue(String text, int weight) {
    }

    private record MatchedCues(List<Cue> positive, List<Cue> negative, List<Cue> neutral, List<Cue> mixed) {
    }

    private record Replacement(String forbidden, String safe) {
    }
}
