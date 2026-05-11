package com.uniport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.HtmlUtils;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private Instant cachedAt;
    private List<FetchedNewsArticle> cachedArticles = List.of();

    @Autowired
    public NaverNewsFeedClient(RestTemplate restTemplate,
                               @Value("${naver.news.enabled:true}") boolean enabled,
                               @Value("${naver.news.client-id:}") String clientId,
                               @Value("${naver.news.client-secret:}") String clientSecret,
                               @Value("${naver.news.cache-ttl-seconds:300}") int cacheTtlSeconds,
                               @Value("${naver.news.display-per-query:10}") int displayPerQuery) {
        this(restTemplate, enabled, clientId, clientSecret, cacheTtlSeconds, displayPerQuery, defaultFeeds());
    }

    NaverNewsFeedClient(RestTemplate restTemplate,
                        boolean enabled,
                        String clientId,
                        String clientSecret,
                        int cacheTtlSeconds,
                        int displayPerQuery,
                        List<FeedDefinition> feeds) {
        this.restTemplate = restTemplate;
        this.enabled = enabled;
        this.clientId = clientId != null ? clientId.trim() : "";
        this.clientSecret = clientSecret != null ? clientSecret.trim() : "";
        this.cacheTtlSeconds = Math.max(cacheTtlSeconds, 0);
        this.displayPerQuery = Math.max(1, Math.min(displayPerQuery, 100));
        this.feeds = feeds != null ? feeds : List.of();
    }

    @Override
    public synchronized List<FetchedNewsArticle> fetchLatest() {
        if (!enabled || clientId.isBlank() || clientSecret.isBlank()) {
            return List.of();
        }
        if (cachedAt != null && Instant.now().minusSeconds(cacheTtlSeconds).isBefore(cachedAt)) {
            return cachedArticles;
        }

        Map<String, FetchedNewsArticle> deduped = new LinkedHashMap<>();
        for (FeedDefinition feed : feeds) {
            for (FetchedNewsArticle article : fetchFeed(feed)) {
                String key = article.getExternalUrl() != null && !article.getExternalUrl().isBlank()
                        ? article.getExternalUrl()
                        : article.getTitle();
                deduped.putIfAbsent(key, article);
            }
        }

        List<FetchedNewsArticle> articles = new ArrayList<>(deduped.values());
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
            String summary = cleanText(stringValue(item.get("description")));
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
                    .sourceName(!parsedTitle.source().isBlank() ? parsedTitle.source() : "네이버 뉴스")
                    .publishedAt(parsePublishedAt(stringValue(item.get("pubDate"))))
                    .featured(false)
                    .externalUrl(externalUrl)
                    .build());
        }
        return articles;
    }

    private ParsedTitle parseTitle(String rawTitle) {
        String title = cleanText(rawTitle);
        int separatorIndex = title.lastIndexOf(" - ");
        if (separatorIndex <= 0 || separatorIndex >= title.length() - 3) {
            return new ParsedTitle(title, "");
        }
        return new ParsedTitle(title.substring(0, separatorIndex).trim(), title.substring(separatorIndex + 3).trim());
    }

    private String cleanText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return HtmlUtils.htmlUnescape(value)
                .replaceAll("(?i)</?b>", "")
                .replaceAll("<[^>]+>", "")
                .replaceAll("\\s+", " ")
                .trim();
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

    private static List<FeedDefinition> defaultFeeds() {
        return List.of(
                FeedDefinition.market("코스피 시황"),
                FeedDefinition.market("환율 금리 증시"),
                FeedDefinition.domesticStock("국내주식 코스피 코스닥"),
                FeedDefinition.domesticStock("삼성전자 SK하이닉스"),
                FeedDefinition.overseasStock("해외주식 미국증시 나스닥"),
                FeedDefinition.overseasStock("엔비디아 애플 테슬라")
        );
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
