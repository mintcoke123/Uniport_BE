package com.uniport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

@Component
public class NaverNewsFeedClient implements NewsFeedClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String API_URL = "https://openapi.naver.com/v1/search/news.json";
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final String clientId;
    private final String clientSecret;
    private final int cacheTtlSeconds;
    private final int displayPerQuery;
    private final List<FeedDefinition> feeds;
    private final RawNewsNormalizer normalizer;
    private final RawNewsDeduplicator deduplicator;

    private Instant cachedAt;
    private List<FetchedNewsArticle> cachedArticles = List.of();

    @Autowired
    public NaverNewsFeedClient(RestTemplate restTemplate,
                               @Value("${naver.news.enabled:true}") boolean enabled,
                               @Value("${naver.news.client-id:}") String clientId,
                               @Value("${naver.news.client-secret:}") String clientSecret,
                               @Value("${naver.news.cache-ttl-seconds:300}") int cacheTtlSeconds,
                               @Value("${naver.news.display-per-query:10}") int displayPerQuery,
                               Environment environment,
                               RawNewsNormalizer normalizer,
                               RawNewsDeduplicator deduplicator) {
        this(restTemplate, enabled, clientId, clientSecret, cacheTtlSeconds, displayPerQuery,
                feedsFromEnvironment(environment), normalizer, deduplicator);
    }

    NaverNewsFeedClient(RestTemplate restTemplate,
                        boolean enabled,
                        String clientId,
                        String clientSecret,
                        int cacheTtlSeconds,
                        int displayPerQuery) {
        this(restTemplate, enabled, clientId, clientSecret, cacheTtlSeconds, displayPerQuery, defaultFeeds());
    }

    NaverNewsFeedClient(RestTemplate restTemplate,
                        boolean enabled,
                        String clientId,
                        String clientSecret,
                        int cacheTtlSeconds,
                        int displayPerQuery,
                        Environment environment) {
        this(restTemplate, enabled, clientId, clientSecret, cacheTtlSeconds, displayPerQuery,
                feedsFromEnvironment(environment));
    }

    NaverNewsFeedClient(RestTemplate restTemplate,
                        boolean enabled,
                        String clientId,
                        String clientSecret,
                        int cacheTtlSeconds,
                        int displayPerQuery,
                        List<FeedDefinition> feeds) {
        this(restTemplate, enabled, clientId, clientSecret, cacheTtlSeconds, displayPerQuery,
                feeds, new RawNewsNormalizer(), new RawNewsDeduplicator());
    }

    private NaverNewsFeedClient(RestTemplate restTemplate,
                                boolean enabled,
                                String clientId,
                                String clientSecret,
                                int cacheTtlSeconds,
                                int displayPerQuery,
                                List<FeedDefinition> feeds,
                                RawNewsNormalizer normalizer,
                                RawNewsDeduplicator deduplicator) {
        this.restTemplate = restTemplate;
        this.enabled = enabled;
        this.clientId = clientId != null ? clientId.trim() : "";
        this.clientSecret = clientSecret != null ? clientSecret.trim() : "";
        this.cacheTtlSeconds = Math.max(cacheTtlSeconds, 0);
        this.displayPerQuery = Math.max(1, Math.min(displayPerQuery, 100));
        this.feeds = feeds != null ? feeds : List.of();
        this.normalizer = normalizer != null ? normalizer : new RawNewsNormalizer();
        this.deduplicator = deduplicator != null ? deduplicator : new RawNewsDeduplicator(this.normalizer);
    }

    @Override
    public synchronized List<FetchedNewsArticle> fetchLatest() {
        if (!enabled || clientId.isBlank() || clientSecret.isBlank()) {
            return List.of();
        }
        if (cachedAt != null && Instant.now().minusSeconds(cacheTtlSeconds).isBefore(cachedAt)) {
            return cachedArticles;
        }

        List<FetchedNewsArticle> fetched = new ArrayList<>();
        for (FeedDefinition feed : feeds) {
            fetched.addAll(fetchFeed(feed));
        }

        List<FetchedNewsArticle> articles = new ArrayList<>(deduplicator.deduplicate(fetched));
        articles.sort((left, right) -> {
            LocalDateTime l = left.getPublishedAt();
            LocalDateTime r = right.getPublishedAt();
            if (l == null && r == null) {
                return 0;
            }
            if (l == null) {
                return 1;
            }
            if (r == null) {
                return -1;
            }
            return r.compareTo(l);
        });
        if (!articles.isEmpty()) {
            FetchedNewsArticle first = articles.get(0);
            articles.set(0, FetchedNewsArticle.builder()
                    .id(first.getId())
                    .category(first.getCategory())
                    .title(first.getTitle())
                    .summary(first.getSummary())
                    .content(first.getContent())
                    .sourceName(first.getSourceName())
                    .publishedAt(first.getPublishedAt())
                    .featured(true)
                    .externalUrl(first.getExternalUrl())
                    .build());
        }
        cachedAt = Instant.now();
        cachedArticles = List.copyOf(articles);
        return cachedArticles;
    }

    private List<FetchedNewsArticle> fetchFeed(FeedDefinition feed) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(API_URL)
                    .queryParam("query", feed.query())
                    .queryParam("display", displayPerQuery)
                    .queryParam("start", 1)
                    .queryParam("sort", "date")
                    .build()
                    .encode(StandardCharsets.UTF_8)
                    .toUri();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Naver-Client-Id", clientId);
            headers.set("X-Naver-Client-Secret", clientSecret);
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<Void>(headers), String.class);
            return parseResponse(feed, response.getBody());
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<FetchedNewsArticle> parseResponse(FeedDefinition feed, String body) throws Exception {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        Map<String, Object> root = OBJECT_MAPPER.readValue(body, MAP_TYPE);
        Object rawItems = root.get("items");
        if (!(rawItems instanceof List<?> items)) {
            return List.of();
        }

        List<FetchedNewsArticle> articles = new ArrayList<>();
        for (Object rawItem : items) {
            if (!(rawItem instanceof Map<?, ?> item)) {
                continue;
            }
            String rawTitle = stringValue(item.get("title"));
            ParsedTitle parsedTitle = parseTitle(rawTitle);
            String summary = normalizer.cleanDisplayText(stringValue(item.get("description")));
            String originalLink = stringValue(item.get("originallink"));
            String naverLink = stringValue(item.get("link"));
            String externalUrl = !originalLink.isBlank() ? originalLink : naverLink;
            if (parsedTitle.title().isBlank() || externalUrl.isBlank()) {
                continue;
            }
            articles.add(FetchedNewsArticle.builder()
                    .id(buildId(externalUrl))
                    .category(feed.category())
                    .title(parsedTitle.title())
                    .summary(summary)
                    .content("")
                    .sourceName(!parsedTitle.source().isBlank() ? parsedTitle.source() : "네이버 뉴스")
                    .publishedAt(parsePublishedAt(stringValue(item.get("pubDate"))))
                    .featured(false)
                    .externalUrl(externalUrl)
                    .build());
        }
        return articles;
    }

    private ParsedTitle parseTitle(String rawTitle) {
        String title = normalizer.cleanDisplayText(rawTitle);
        int separatorIndex = title.lastIndexOf(" - ");
        if (separatorIndex <= 0 || separatorIndex >= title.length() - 3) {
            return new ParsedTitle(title, "");
        }
        return new ParsedTitle(title.substring(0, separatorIndex).trim(), title.substring(separatorIndex + 3).trim());
    }

    private LocalDateTime parsePublishedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .withZoneSameInstant(SEOUL_ZONE)
                    .toLocalDateTime();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildId(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder("naver_");
        for (int i = 0; i < 8 && i < hash.length; i++) {
            builder.append(String.format(Locale.ROOT, "%02x", hash[i]));
        }
        return builder.toString();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static List<FeedDefinition> feedsFromEnvironment(Environment environment) {
        if (environment == null) {
            return defaultFeeds();
        }
        List<FeedDefinition> feeds = new ArrayList<>();
        addFeeds(feeds, configuredQueries(environment, "naver.news.queries.market", defaultMarketQueries()), FeedDefinition::market);
        addFeeds(feeds, configuredQueries(environment, "naver.news.queries.theme", defaultThemeQueries()), FeedDefinition::domesticStock);
        addFeeds(feeds, configuredQueries(environment, "naver.news.queries.company", defaultCompanyQueries()), FeedDefinition::domesticStock);
        addFeeds(feeds, configuredQueries(environment, "naver.news.queries.overseas", defaultOverseasQueries()), FeedDefinition::overseasStock);
        return List.copyOf(feeds);
    }

    private static List<String> configuredQueries(Environment environment, String key, List<String> defaultQueries) {
        List<String> queries = Binder.get(environment)
                .bind(key, Bindable.listOf(String.class))
                .orElse(defaultQueries);
        return queries.stream()
                .map(query -> query != null ? query.trim() : "")
                .filter(query -> !query.isBlank())
                .toList();
    }

    private static void addFeeds(List<FeedDefinition> feeds,
                                 List<String> queries,
                                 Function<String, FeedDefinition> factory) {
        queries.stream()
                .map(factory)
                .forEach(feeds::add);
    }

    private static List<FeedDefinition> defaultFeeds() {
        List<FeedDefinition> feeds = new ArrayList<>();
        addFeeds(feeds, defaultMarketQueries(), FeedDefinition::market);
        addFeeds(feeds, defaultCompanyQueries(), FeedDefinition::domesticStock);
        addFeeds(feeds, defaultThemeQueries(), FeedDefinition::domesticStock);
        addFeeds(feeds, defaultOverseasQueries(), FeedDefinition::overseasStock);
        return List.copyOf(feeds);
    }

    private static List<String> defaultMarketQueries() {
        return List.of("코스피 코스닥 환율 금리", "증시 시황 외국인 기관");
    }

    private static List<String> defaultThemeQueries() {
        return List.of("반도체 자동차 배터리 바이오 금융", "AI 로봇 원전 방산");
    }

    private static List<String> defaultCompanyQueries() {
        return List.of("국내증시 실적 어닝");
    }

    private static List<String> defaultOverseasQueries() {
        return List.of("미국증시 빅테크 AI", "나스닥 엔비디아 테슬라 애플");
    }

    private record ParsedTitle(String title, String source) {
    }

    public record FeedDefinition(NewsCategory category, String query) {
        public static FeedDefinition market(String query) {
            return new FeedDefinition(NewsCategory.MARKET, query);
        }

        public static FeedDefinition domesticStock(String query) {
            return new FeedDefinition(NewsCategory.DOMESTIC_STOCK, query);
        }

        public static FeedDefinition overseasStock(String query) {
            return new FeedDefinition(NewsCategory.OVERSEAS_STOCK, query);
        }
    }
}
