package com.uniport.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

@Service
public class IssueClusterService {

    private static final DateTimeFormatter CLUSTER_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Duration DEFAULT_WINDOW = Duration.ofHours(24);
    private static final double SAME_ISSUE_THRESHOLD = 0.62;

    private static final List<String> DOMESTIC_COMPANIES = List.of(
            "삼성전자",
            "SK하이닉스",
            "현대차",
            "기아",
            "LG에너지솔루션",
            "삼성SDI",
            "한화에어로스페이스",
            "두산에너빌리티",
            "POSCO홀딩스",
            "NAVER",
            "카카오"
    );

    private static final List<String> OVERSEAS_COMPANIES = List.of(
            "엔비디아",
            "NVIDIA",
            "테슬라",
            "TESLA",
            "애플",
            "APPLE",
            "마이크로소프트",
            "MICROSOFT",
            "Meta",
            "META",
            "Amazon",
            "AMAZON",
            "Google",
            "GOOGLE",
            "Alphabet",
            "ALPHABET",
            "AMD",
            "Broadcom",
            "BROADCOM",
            "Oracle",
            "ORACLE",
            "Dell",
            "DELL",
            "Dell Technologies",
            "델",
            "TSMC",
            "Intel",
            "INTEL",
            "인텔",
            "Fluence",
            "FLUENCE",
            "플루언스",
            "Strategy",
            "STRATEGY",
            "스트레티지",
            "MicroStrategy",
            "MICROSTRATEGY",
            "JetBlue",
            "JETBLUE",
            "제트블루",
            "Robinhood",
            "ROBINHOOD",
            "로빈후드",
            "BP",
            "Voya",
            "VOYA",
            "보야",
            "Edgewise",
            "EDGEWISE",
            "엣지와이스",
            "에지와이즈",
            "Weatherford",
            "WEATHERFORD",
            "웨더포드",
            "Volkswagen",
            "VOLKSWAGEN",
            "폭스바겐",
            "Prosus",
            "PROSUS",
            "Delivery Hero",
            "DELIVERY HERO",
            "Honeywell",
            "HONEYWELL",
            "하니웰",
            "Moderna",
            "MODERNA",
            "모더나",
            "MGM",
            "Wise",
            "WISE",
            "BYD"
    );

    private static final List<String> MARKET_ENTITIES = List.of(
            "환율",
            "원달러",
            "코스피",
            "코스닥",
            "금리",
            "국채",
            "국채금리",
            "FOMC",
            "CPI",
            "고용지표",
            "유가"
    );
    private static final List<String> OVERSEAS_ENTITIES = List.of("미국", "중국", "일본");
    private static final List<String> THEME_ENTITIES = List.of(
            "HBM",
            "AI반도체",
            "반도체",
            "ETF",
            "AI",
            "AI서버",
            "데이터센터",
            "클라우드",
            "빅테크"
    );
    private static final List<String> COMPANY_SPECIFIC_EVENTS = List.of("실적", "파업");

    private final RawNewsNormalizer normalizer;
    private final Duration window;

    public IssueClusterService() {
        this(new RawNewsNormalizer());
    }

    public IssueClusterService(RawNewsNormalizer normalizer) {
        this(normalizer, DEFAULT_WINDOW);
    }

    IssueClusterService(RawNewsNormalizer normalizer, Duration window) {
        this.normalizer = normalizer;
        this.window = window;
    }

    public List<IssueCluster> cluster(List<FetchedNewsArticle> articles) {
        if (articles == null || articles.isEmpty()) {
            return List.of();
        }

        List<ArticleFeatures> features = new ArrayList<>();
        for (FetchedNewsArticle article : articles) {
            if (article != null) {
                features.add(features(article));
            }
        }
        features.sort(Comparator
                .comparing(ArticleFeatures::publishedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(this::stableArticleKey));

        List<SimilarityEdge> edges = similarityEdges(features);
        int[] parents = initialParents(features.size());
        LocalDateTime[] firstPublishedAt = publishedAtArray(features);
        LocalDateTime[] lastPublishedAt = publishedAtArray(features);

        for (SimilarityEdge edge : edges) {
            union(edge, features, parents, firstPublishedAt, lastPublishedAt);
        }

        List<MutableCluster> clusters = clusters(features, parents);
        clusters.sort(Comparator
                .comparing(MutableCluster::firstPublishedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(cluster -> cluster.representative.category().name())
                .thenComparing(MutableCluster::mainEntity)
                .thenComparing(MutableCluster::mainEvent)
                .thenComparing(cluster -> stableArticleKey(cluster.representative)));

        List<String> baseKeys = clusters.stream()
                .map(MutableCluster::baseClusterKey)
                .toList();
        List<String> keyCandidates = clusters.stream()
                .map(cluster -> keyCandidate(cluster, hasDuplicate(baseKeys, cluster.baseClusterKey())))
                .toList();

        List<IssueCluster> issueClusters = new ArrayList<>();
        for (int index = 0; index < clusters.size(); index++) {
            issueClusters.add(toIssueCluster(
                    clusters.get(index),
                    finalClusterKey(keyCandidates, index)
            ));
        }
        return List.copyOf(issueClusters);
    }

    private ArticleFeatures features(FetchedNewsArticle article) {
        List<String> tokens = normalizer.titleTokens(article.getTitle());
        List<String> entities = extractEntities(article, tokens);
        List<String> events = extractEvents(article, tokens);
        InvestmentIssueCategory category = classify(article, entities, events);
        return new ArticleFeatures(
                article,
                article.getPublishedAt(),
                tokens,
                entities,
                events,
                category
        );
    }

    private List<String> extractEntities(FetchedNewsArticle article, List<String> tokens) {
        String title = cleanTitle(article);
        String upperTitle = title.toUpperCase(Locale.ROOT);
        Set<String> entities = new LinkedHashSet<>();

        if (upperTitle.contains("HBM")) {
            entities.add("HBM");
        }
        for (String company : DOMESTIC_COMPANIES) {
            if (title.contains(company)) {
                entities.add(company);
            }
        }
        for (String company : OVERSEAS_COMPANIES) {
            if (upperTitle.contains(company.toUpperCase(Locale.ROOT))) {
                entities.add(canonicalOverseasCompany(company));
            }
        }
        for (String entity : MARKET_ENTITIES) {
            if (title.contains(entity)) {
                entities.add(entity);
            }
        }
        for (String entity : OVERSEAS_ENTITIES) {
            if (title.contains(entity)) {
                entities.add(entity);
            }
        }
        if ((tokens.contains("AI") && title.contains("반도체")) || title.contains("AI 반도체")) {
            entities.add("AI반도체");
        }
        if ((tokens.contains("AI") && title.contains("서버")) || title.contains("AI 서버")) {
            entities.add("AI서버");
        }
        if (title.contains("데이터센터") || title.toUpperCase(Locale.ROOT).contains("DATA CENTER")) {
            entities.add("데이터센터");
        }
        if (title.contains("클라우드") || title.toUpperCase(Locale.ROOT).contains("CLOUD")) {
            entities.add("클라우드");
        }
        if (title.contains("빅테크") || title.toUpperCase(Locale.ROOT).contains("BIG TECH")) {
            entities.add("빅테크");
        }
        if (tokens.contains("AI")) {
            entities.add("AI");
        }
        if (title.contains("반도체")) {
            entities.add("반도체");
        }
        if (upperTitle.contains("ETF")) {
            entities.add("ETF");
        }
        if (tokens.contains("외국인")) {
            entities.add("외국인");
        }

        return List.copyOf(entities);
    }

    private List<String> extractEvents(FetchedNewsArticle article, List<String> tokens) {
        String title = cleanTitle(article);
        Set<String> events = new LinkedHashSet<>();

        if (containsAny(title, "실적", "어닝", "매출", "영업이익", "순이익", "EPS",
                "예상 상회", "예상치 상회", "예상치를 웃돌", "서프라이즈", "가이던스 상향", "마진 개선")) {
            events.add("실적");
        }
        if (containsAny(title, "파업", "노조", "쟁의")) {
            events.add("파업");
        }
        if (containsAny(title, "규제", "수출 제한", "수출통제", "제재")) {
            events.add("규제");
        }
        if (tokens.contains("상승") || tokens.contains("매수")
                || containsAny(title, "강세", "반등", "랠리", "신고가", "기대", "모멘텀", "수혜", "수요", "확대",
                "상향", "호조", "예상 상회", "예상치 상회", "예상치를 웃돌", "서프라이즈")) {
            events.add("상승");
        }
        if (tokens.contains("하락") || tokens.contains("매도")
                || containsAny(title, "급락", "약세", "우려", "둔화", "부진", "예상 하회", "예상치 하회",
                "가이던스 하향")) {
            events.add("하락");
        }

        return List.copyOf(events);
    }

    private InvestmentIssueCategory classify(FetchedNewsArticle article, List<String> entities, List<String> events) {
        boolean hasDomesticCompany = overlaps(entities, DOMESTIC_COMPANIES);
        boolean hasOverseasCompany = overlaps(entities, List.of("엔비디아", "테슬라", "애플", "마이크로소프트", "TSMC"));
        boolean hasOverseasPolicy = overlaps(entities, OVERSEAS_ENTITIES) && events.contains("규제");
        boolean hasThemeEntity = overlaps(entities, THEME_ENTITIES);
        boolean hasMarketEntity = overlaps(entities, MARKET_ENTITIES);
        boolean hasCompanySpecificEvent = overlaps(events, COMPANY_SPECIFIC_EVENTS);

        if (article.getCategory() == NewsCategory.OVERSEAS_STOCK || hasOverseasCompany || hasOverseasPolicy) {
            return InvestmentIssueCategory.OVERSEAS;
        }
        if (hasDomesticCompany && hasCompanySpecificEvent) {
            return InvestmentIssueCategory.COMPANY;
        }
        if (hasThemeEntity) {
            return InvestmentIssueCategory.THEME;
        }
        if (hasMarketEntity || article.getCategory() == NewsCategory.MARKET) {
            return InvestmentIssueCategory.MARKET;
        }
        if (hasDomesticCompany || article.getCategory() == NewsCategory.DOMESTIC_STOCK) {
            return InvestmentIssueCategory.COMPANY;
        }
        return InvestmentIssueCategory.THEME;
    }

    private String mainEntity(InvestmentIssueCategory category, List<String> entities) {
        if (category == InvestmentIssueCategory.THEME) {
            return firstMatching(entities, List.of("HBM", "AI반도체", "AI서버", "데이터센터", "반도체", "ETF",
                    "클라우드", "빅테크", "AI"));
        }
        if (category == InvestmentIssueCategory.COMPANY) {
            return firstMatching(entities, DOMESTIC_COMPANIES);
        }
        if (category == InvestmentIssueCategory.OVERSEAS) {
            return firstMatching(entities, List.of("엔비디아", "테슬라", "애플", "마이크로소프트", "Meta",
                    "Amazon", "Alphabet", "AMD", "Broadcom", "Oracle", "Dell", "TSMC", "Intel", "Fluence",
                    "Strategy", "JetBlue", "Robinhood", "BP", "Voya", "Edgewise", "Weatherford",
                    "Volkswagen", "Prosus", "Delivery Hero", "Honeywell", "Moderna", "MGM", "Wise", "BYD",
                    "미국", "중국", "일본"));
        }
        if (category == InvestmentIssueCategory.MARKET) {
            return firstMatching(entities, List.of("CPI", "FOMC", "환율", "원달러", "코스피", "코스닥", "금리",
                    "국채금리", "국채", "고용지표", "유가"));
        }
        return entities.isEmpty() ? "시장" : entities.get(0);
    }

    private String mainEvent(List<String> events) {
        if (events.contains("실적")) {
            return "실적";
        }
        if (events.contains("파업")) {
            return "파업";
        }
        if (events.contains("규제")) {
            return "규제";
        }
        if (events.contains("상승")) {
            return "상승";
        }
        if (events.contains("하락")) {
            return "하락";
        }
        return events.isEmpty() ? "기타" : events.get(0);
    }

    private String firstMatching(List<String> values, List<String> priorities) {
        for (String priority : priorities) {
            if (values.contains(priority)) {
                return priority;
            }
        }
        return values.isEmpty() ? "시장" : values.get(0);
    }

    private List<SimilarityEdge> similarityEdges(List<ArticleFeatures> features) {
        List<SimilarityEdge> edges = new ArrayList<>();
        for (int left = 0; left < features.size(); left++) {
            for (int right = left + 1; right < features.size(); right++) {
                ArticleFeatures leftArticle = features.get(left);
                ArticleFeatures rightArticle = features.get(right);
                if (leftArticle.category() != rightArticle.category()) {
                    continue;
                }
                double score = similarity(leftArticle, rightArticle);
                if (score >= SAME_ISSUE_THRESHOLD) {
                    edges.add(new SimilarityEdge(left, right, score));
                }
            }
        }
        edges.sort(Comparator
                .comparingDouble(SimilarityEdge::score)
                .reversed()
                .thenComparing(edge -> stableArticleKey(features.get(edge.leftIndex())))
                .thenComparing(edge -> stableArticleKey(features.get(edge.rightIndex()))));
        return edges;
    }

    private int[] initialParents(int size) {
        int[] parents = new int[size];
        for (int index = 0; index < size; index++) {
            parents[index] = index;
        }
        return parents;
    }

    private LocalDateTime[] publishedAtArray(List<ArticleFeatures> features) {
        LocalDateTime[] publishedAt = new LocalDateTime[features.size()];
        for (int index = 0; index < features.size(); index++) {
            publishedAt[index] = features.get(index).publishedAt();
        }
        return publishedAt;
    }

    private void union(SimilarityEdge edge,
                       List<ArticleFeatures> features,
                       int[] parents,
                       LocalDateTime[] firstPublishedAt,
                       LocalDateTime[] lastPublishedAt) {
        int leftRoot = find(parents, edge.leftIndex());
        int rightRoot = find(parents, edge.rightIndex());
        if (leftRoot == rightRoot || !combinedWithinWindow(
                firstPublishedAt[leftRoot],
                lastPublishedAt[leftRoot],
                firstPublishedAt[rightRoot],
                lastPublishedAt[rightRoot])) {
            return;
        }

        int targetRoot = stableArticleKey(features.get(leftRoot)).compareTo(stableArticleKey(features.get(rightRoot))) <= 0
                ? leftRoot
                : rightRoot;
        int sourceRoot = targetRoot == leftRoot ? rightRoot : leftRoot;
        parents[sourceRoot] = targetRoot;
        firstPublishedAt[targetRoot] = earliest(firstPublishedAt[targetRoot], firstPublishedAt[sourceRoot]);
        lastPublishedAt[targetRoot] = latest(lastPublishedAt[targetRoot], lastPublishedAt[sourceRoot]);
    }

    private int find(int[] parents, int index) {
        if (parents[index] != index) {
            parents[index] = find(parents, parents[index]);
        }
        return parents[index];
    }

    private List<MutableCluster> clusters(List<ArticleFeatures> features, int[] parents) {
        List<MutableCluster> clusters = new ArrayList<>();
        for (int index = 0; index < features.size(); index++) {
            int root = find(parents, index);
            MutableCluster cluster = null;
            for (MutableCluster candidate : clusters) {
                if (candidate.representative == features.get(root)) {
                    cluster = candidate;
                    break;
                }
            }
            if (cluster == null) {
                cluster = new MutableCluster(features.get(root));
                clusters.add(cluster);
            }
            cluster.add(features.get(index));
        }
        return clusters;
    }

    private double similarity(ArticleFeatures left, ArticleFeatures right) {
        if (!withinWindow(left.publishedAt(), right.publishedAt())) {
            return 0;
        }
        return (jaccard(left.tokens(), right.tokens()) * 0.30)
                + (overlapScore(left.entities(), right.entities()) * 0.35)
                + (overlapScore(left.events(), right.events()) * 0.25)
                + (timeProximity(left.publishedAt(), right.publishedAt()) * 0.10);
    }

    private double jaccard(List<String> left, List<String> right) {
        if (left.isEmpty() && right.isEmpty()) {
            return 0;
        }
        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        Set<String> intersection = new LinkedHashSet<>(left);
        intersection.retainAll(right);
        return intersection.size() / (double) union.size();
    }

    private double overlapScore(List<String> left, List<String> right) {
        return overlaps(left, Set.copyOf(right)) ? 1 : 0;
    }

    private boolean overlaps(List<String> values, List<String> candidates) {
        return overlaps(values, Set.copyOf(candidates));
    }

    private boolean overlaps(List<String> values, Set<String> candidates) {
        for (String value : values) {
            if (candidates.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean withinWindow(LocalDateTime left, LocalDateTime right) {
        if (left == null || right == null) {
            return true;
        }
        return absoluteDuration(left, right).compareTo(window) <= 0;
    }

    private boolean combinedWithinWindow(LocalDateTime leftFirst,
                                         LocalDateTime leftLast,
                                         LocalDateTime rightFirst,
                                         LocalDateTime rightLast) {
        if (leftFirst == null || leftLast == null || rightFirst == null || rightLast == null) {
            return true;
        }
        return absoluteDuration(earliest(leftFirst, rightFirst), latest(leftLast, rightLast)).compareTo(window) <= 0;
    }

    private double timeProximity(LocalDateTime left, LocalDateTime right) {
        if (left == null || right == null) {
            return 0;
        }
        Duration duration = absoluteDuration(left, right);
        if (duration.compareTo(window) > 0) {
            return 0;
        }
        return 1 - (duration.toMillis() / (double) window.toMillis());
    }

    private Duration absoluteDuration(LocalDateTime left, LocalDateTime right) {
        Duration duration = Duration.between(left, right);
        return duration.isNegative() ? duration.negated() : duration;
    }

    private LocalDateTime earliest(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }

    private LocalDateTime latest(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private String cleanTitle(FetchedNewsArticle article) {
        return normalizer.cleanDisplayText(article.getTitle());
    }

    private String stableArticleKey(ArticleFeatures article) {
        return article.category().name()
                + "|" + valueOrEmpty(article.publishedAt() == null ? null : article.publishedAt().toString())
                + "|" + normalizer.cleanDisplayText(article.article().getTitle())
                + "|" + normalizer.cleanDisplayText(article.article().getSourceName())
                + "|" + normalizer.cleanDisplayText(article.article().getSummary())
                + "|" + normalizer.cleanDisplayText(article.article().getContent())
                + "|" + valueOrEmpty(article.article().getExternalUrl())
                + "|" + valueOrEmpty(article.article().getId());
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private IssueCluster toIssueCluster(MutableCluster cluster, String key) {
        String mainEntity = cluster.mainEntity();
        String mainEvent = cluster.mainEvent();
        return new IssueCluster(
                key,
                cluster.representative.category(),
                mainEntity,
                mainEvent,
                cluster.articles.stream()
                        .map(ArticleFeatures::article)
                        .toList()
        );
    }

    private String keyCandidate(MutableCluster cluster, boolean appendUniqueSuffix) {
        String key = cluster.baseClusterKey();
        if (appendUniqueSuffix) {
            key = key + "|" + shortStableSuffix(cluster);
        }
        return key;
    }

    private String finalClusterKey(List<String> keyCandidates, int index) {
        String key = keyCandidates.get(index);
        if (!hasDuplicate(keyCandidates, key)) {
            return key;
        }
        int occurrence = 1;
        for (int current = 0; current < index; current++) {
            if (keyCandidates.get(current).equals(key)) {
                occurrence++;
            }
        }
        return key + "|" + String.format(Locale.ROOT, "%02d", occurrence);
    }

    private boolean hasDuplicate(List<String> values, String value) {
        int count = 0;
        for (String candidate : values) {
            if (candidate.equals(value) && ++count > 1) {
                return true;
            }
        }
        return false;
    }

    private String shortStableSuffix(MutableCluster cluster) {
        String source = cluster.articles.stream()
                .map(this::stableArticleKey)
                .sorted()
                .collect(Collectors.joining("\n"));
        CRC32 crc32 = new CRC32();
        crc32.update(source.getBytes(StandardCharsets.UTF_8));
        return String.format(Locale.ROOT, "%08X", crc32.getValue());
    }

    private String canonicalOverseasCompany(String company) {
        String upperCompany = company.toUpperCase(Locale.ROOT);
        if (upperCompany.equals("NVIDIA")) {
            return "엔비디아";
        }
        if (upperCompany.equals("TESLA")) {
            return "테슬라";
        }
        if (upperCompany.equals("APPLE")) {
            return "애플";
        }
        if (upperCompany.equals("MICROSOFT")) {
            return "마이크로소프트";
        }
        if (upperCompany.equals("META")) {
            return "Meta";
        }
        if (upperCompany.equals("AMAZON")) {
            return "Amazon";
        }
        if (upperCompany.equals("GOOGLE") || upperCompany.equals("ALPHABET")) {
            return "Alphabet";
        }
        if (upperCompany.equals("BROADCOM")) {
            return "Broadcom";
        }
        if (upperCompany.equals("ORACLE")) {
            return "Oracle";
        }
        if (upperCompany.equals("DELL") || upperCompany.equals("DELL TECHNOLOGIES") || company.equals("델")) {
            return "Dell";
        }
        if (upperCompany.equals("STRATEGY") || upperCompany.equals("MICROSTRATEGY") || company.equals("스트레티지")) {
            return "Strategy";
        }
        if (upperCompany.equals("JETBLUE") || company.equals("제트블루")) {
            return "JetBlue";
        }
        if (upperCompany.equals("ROBINHOOD") || company.equals("로빈후드")) {
            return "Robinhood";
        }
        if (upperCompany.equals("VOYA") || company.equals("보야")) {
            return "Voya";
        }
        if (upperCompany.equals("EDGEWISE") || company.equals("엣지와이스") || company.equals("에지와이즈")) {
            return "Edgewise";
        }
        if (upperCompany.equals("WEATHERFORD") || company.equals("웨더포드")) {
            return "Weatherford";
        }
        if (upperCompany.equals("VOLKSWAGEN") || company.equals("폭스바겐")) {
            return "Volkswagen";
        }
        if (upperCompany.equals("HONEYWELL") || company.equals("하니웰")) {
            return "Honeywell";
        }
        if (upperCompany.equals("MODERNA") || company.equals("모더나")) {
            return "Moderna";
        }
        return company;
    }

    private String clusterKey(MutableCluster cluster, String mainEntity, String mainEvent) {
        return datePart(cluster.firstPublishedAt)
                + "|" + cluster.representative.category().name()
                + "|" + mainEntity
                + "|" + mainEvent;
    }

    private String datePart(LocalDateTime publishedAt) {
        if (publishedAt == null) {
            return "00000000";
        }
        return publishedAt.format(CLUSTER_DATE_FORMATTER);
    }

    private record ArticleFeatures(
            FetchedNewsArticle article,
            LocalDateTime publishedAt,
            List<String> tokens,
            List<String> entities,
            List<String> events,
            InvestmentIssueCategory category
    ) {
    }

    private record SimilarityEdge(
            int leftIndex,
            int rightIndex,
            double score
    ) {
    }

    private final class MutableCluster {

        private final ArticleFeatures representative;
        private final List<ArticleFeatures> articles = new ArrayList<>();
        private LocalDateTime firstPublishedAt;
        private LocalDateTime lastPublishedAt;

        private MutableCluster(ArticleFeatures representative) {
            this.representative = representative;
        }

        private void add(ArticleFeatures article) {
            articles.add(article);
            if (article.publishedAt() != null) {
                if (firstPublishedAt == null || article.publishedAt().isBefore(firstPublishedAt)) {
                    firstPublishedAt = article.publishedAt();
                }
                if (lastPublishedAt == null || article.publishedAt().isAfter(lastPublishedAt)) {
                    lastPublishedAt = article.publishedAt();
                }
            }
        }

        private String baseClusterKey() {
            return clusterKey(this, mainEntity(), mainEvent());
        }

        private LocalDateTime firstPublishedAt() {
            return firstPublishedAt;
        }

        private String mainEntity() {
            return IssueClusterService.this.mainEntity(representative.category(), entityEvidence());
        }

        private String mainEvent() {
            return IssueClusterService.this.mainEvent(eventEvidence());
        }

        private List<String> entityEvidence() {
            return articles.stream()
                    .flatMap(article -> article.entities().stream())
                    .toList();
        }

        private List<String> eventEvidence() {
            return articles.stream()
                    .flatMap(article -> article.events().stream())
                    .toList();
        }
    }
}
