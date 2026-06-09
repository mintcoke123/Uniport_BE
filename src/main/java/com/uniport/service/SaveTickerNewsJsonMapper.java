package com.uniport.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class SaveTickerNewsJsonMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
    private static final String SAVETICKER_WEB_NEWS_BASE_URL = "https://www.saveticker.com/news/";

    public List<FetchedNewsArticle> extract(PublicWebIssueSource source, String json) {
        if (source == null || json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode newsList = root.path("news_list");
            if (!newsList.isArray()) {
                return List.of();
            }

            List<FetchedNewsArticle> articles = new ArrayList<>();
            for (JsonNode item : newsList) {
                FetchedNewsArticle article = toArticle(source, item);
                if (article != null) {
                    articles.add(article);
                }
            }
            return List.copyOf(articles);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private FetchedNewsArticle toArticle(PublicWebIssueSource source, JsonNode item) {
        String saveTickerId = text(item, "id");
        String title = firstNonBlank(translatedTitle(item, "ko_KR"), text(item, "title"));
        if (saveTickerId.isBlank() || title.length() < 8) {
            return null;
        }

        String tagText = tagText(item.path("tag_names"));
        String fullBody = firstNonBlankBody(
                translatedContent(item, "ko_KR", true),
                contentBlocks(item.path("content"), true)
        );
        return FetchedNewsArticle.builder()
                .id("saveticker_" + saveTickerId.replaceAll("[^A-Za-z0-9_-]", "_"))
                .category(source.category())
                .title(title)
                .summary(firstNonBlank(
                        text(item, "group_summary"),
                        translatedSummary(item, "ko_KR"),
                        contentBlocks(item.path("content"), false)
                ))
                .content(tagText)
                .fullBody(fullBody)
                .sourceName(sourceName(source, item))
                .publishedAt(parseDateTime(firstNonBlank(
                        text(item, "created_at"),
                        item.path("extra").path("source_created_at").asText("")
                )))
                .featured(item.path("is_top_story").asBoolean(false))
                .externalUrl(saveTickerUrl(saveTickerId, text(item, "news_group_id")))
                .build();
    }

    public FetchedNewsArticle enrichWithDetail(PublicWebIssueSource source,
                                               FetchedNewsArticle article,
                                               String json) {
        if (article == null || json == null || json.isBlank()) {
            return article;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode item = detailItem(root);
            if (item.isMissingNode() || item.isNull()) {
                return article;
            }

            String title = firstNonBlank(translatedTitle(item, "ko_KR"), text(item, "title"), article.getTitle());
            String detailFullBody = firstNonBlankBody(
                    translatedContent(item, "ko_KR", true),
                    contentBlocks(item.path("content"), true)
            );
            String summary = firstNonBlankBody(
                    detailFullBody,
                    translatedSummary(item, "ko_KR"),
                    article.getSummary()
            );
            String fullBody = firstNonBlankBody(detailFullBody, article.getFullBody());
            LocalDateTime publishedAt = parseDateTime(text(item, "created_at"));
            return FetchedNewsArticle.builder()
                    .id(article.getId())
                    .category(article.getCategory())
                    .title(title)
                    .summary(summary)
                    .content(article.getContent())
                    .fullBody(fullBody)
                    .sourceName(sourceName(source, item, article.getSourceName()))
                    .publishedAt(publishedAt == null ? article.getPublishedAt() : publishedAt)
                    .featured(article.isFeatured())
                    .externalUrl(article.getExternalUrl())
                    .build();
        } catch (Exception exception) {
            return article;
        }
    }

    private JsonNode detailItem(JsonNode root) {
        JsonNode wrapped = root.path("news");
        if (!wrapped.isMissingNode() && !wrapped.isNull()) {
            return wrapped;
        }
        if (!root.path("id").asText("").isBlank() || !root.path("title").asText("").isBlank()) {
            return root;
        }
        return wrapped;
    }

    private String translatedTitle(JsonNode item, String locale) {
        return text(item.path("translations").path("translated").path(locale), "title");
    }

    private String translatedSummary(JsonNode item, String locale) {
        JsonNode translated = item.path("translations").path("translated").path(locale);
        String summary = contentBlocks(translated.path("summary"), false);
        if (!summary.isBlank()) {
            return summary;
        }
        return contentBlocks(translated.path("content"), false);
    }

    private String translatedContent(JsonNode item, String locale, boolean preserveLines) {
        JsonNode translated = item.path("translations").path("translated").path(locale);
        return contentBlocks(translated.path("content"), preserveLines);
    }

    private String contentBlocks(JsonNode node, boolean preserveLines) {
        if (node.isTextual()) {
            return preserveLines ? cleanBodyText(node.asText("")) : cleanText(node.asText(""));
        }
        if (!node.isArray()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (JsonNode block : node) {
            String content = block.path("content").asText("");
            if (!content.isBlank()) {
                values.add(content);
            }
        }
        if (preserveLines) {
            return cleanBodyText(String.join("\n", values));
        }
        return cleanText(String.join(" ", values));
    }

    private String tagText(JsonNode tags) {
        if (!tags.isArray()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (JsonNode tag : tags) {
            String value = cleanText(tag.asText(""));
            if (!value.isBlank()) {
                values.add(value);
                if (value.startsWith("$") && value.length() > 1) {
                    values.add(value.substring(1));
                }
            }
        }
        return String.join(" ", values);
    }

    private String sourceName(PublicWebIssueSource source, JsonNode item) {
        return sourceName(source, item, source.sourceName());
    }

    private String sourceName(PublicWebIssueSource source, JsonNode item, String fallbackSourceName) {
        String rawSource = text(item, "source");
        if (rawSource.isBlank()) {
            return fallbackSourceName == null || fallbackSourceName.isBlank()
                    ? source.sourceName()
                    : fallbackSourceName;
        }
        return switch (rawSource.toLowerCase(Locale.ROOT)) {
            case "reuters" -> "Reuters";
            case "financial-juice" -> "Financial Juice";
            default -> rawSource;
        };
    }

    private LocalDateTime parseDateTime(String value) {
        if (value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(KST_ZONE).toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String saveTickerUrl(String saveTickerId, String groupId) {
        String url = SAVETICKER_WEB_NEWS_BASE_URL + encode(saveTickerId);
        if (groupId.isBlank()) {
            return url;
        }
        return url + "?groupId=" + encode(groupId);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String cleaned = cleanText(value);
            if (!cleaned.isBlank()) {
                return cleaned;
            }
        }
        return "";
    }

    private String firstNonBlankBody(String... values) {
        for (String value : values) {
            String cleaned = cleanBodyText(value);
            if (!cleaned.isBlank()) {
                return cleaned;
            }
        }
        return "";
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        return cleanText(node.path(fieldName).asText(""));
    }

    private String cleanText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String cleanBodyText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\u00A0', ' ')
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n+ *", "\n")
                .replaceAll("[ ]{2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
