package com.uniport.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class PublicWebIssueHtmlExtractor {

    private static final int MIN_TITLE_LENGTH = 12;
    private static final int MAX_SUMMARY_LENGTH = 180;
    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter SLASH_DATE_FORMATTER = DateTimeFormatter.ofPattern("M/d/yyyy");
    private static final DateTimeFormatter ENGLISH_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US);
    private static final Set<String> WEAK_LINK_TEXTS = Set.of(
            "ABOUT",
            "CONTACT",
            "LOGIN",
            "SIGN IN",
            "SUBSCRIBE",
            "MENU",
            "READ MORE",
            "MORE",
            "HOME",
            "ADVISORY COUNCILS",
            "ANNUAL REPORTS",
            "BOARD MEMBERS",
            "EXPAND SUB-MENU",
            "FEDERAL RESERVE BANKS",
            "INCOME STATEMENTS",
            "INVESTOR INFORMATION",
            "INVESTOR RELATIONS",
            "MEDIA ASSETS"
    );

    public List<FetchedNewsArticle> extract(PublicWebIssueSource source, String html) {
        if (source == null || html == null || html.isBlank()) {
            return List.of();
        }

        Document document = Jsoup.parse(html, source.url().toString());
        List<FetchedNewsArticle> articles = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();

        if (isTelegramPublicChannel(source.url())) {
            addFromTelegramMessages(source, document, seenUrls, articles);
            return List.copyOf(articles);
        }

        addFromDocument(source, document, seenUrls, articles);
        if (!articles.isEmpty()) {
            return List.copyOf(articles);
        }

        for (Element container : document.select("article")) {
            addFromContainer(source, container, seenUrls, articles);
            if (articles.size() >= source.maxItems()) {
                return List.copyOf(articles);
            }
        }

        for (Element anchor : document.select("a[href]")) {
            if (anchor.parents().stream().anyMatch(parent -> parent.tagName().equals("article"))) {
                continue;
            }
            if (isNavigationAnchor(anchor)) {
                continue;
            }
            addFromAnchor(source, anchor, seenUrls, articles);
            if (articles.size() >= source.maxItems()) {
                return List.copyOf(articles);
            }
        }

        return List.copyOf(articles);
    }

    private void addFromTelegramMessages(PublicWebIssueSource source,
                                         Document document,
                                         Set<String> seenUrls,
                                         List<FetchedNewsArticle> articles) {
        for (Element message : document.select(".tgme_widget_message.js-widget_message")) {
            Element textElement = message.selectFirst(".tgme_widget_message_text.js-message_text");
            String messageText = textElement == null ? "" : textWithBreaks(textElement);
            Element replyTextElement = message.selectFirst(".tgme_widget_message_text.js-message_reply_text");
            String replyText = replyTextElement == null ? "" : textWithBreaks(replyTextElement);
            String previewTitle = firstText(message.select(".link_preview_title"));
            String title = telegramTitle(telegramLines(messageText), previewTitle);
            if (isWeakTitle(title) || isTelegramLowSignalTitle(title)) {
                title = telegramTitle(telegramLines(replyText), previewTitle);
            }
            if (isWeakTitle(title) || isTelegramNoise(title)) {
                continue;
            }

            String externalUrl = telegramMessageUrl(source.url(), message);
            if (externalUrl.isBlank() || !seenUrls.add(externalUrl)) {
                continue;
            }

            articles.add(FetchedNewsArticle.builder()
                    .id(buildId(externalUrl))
                    .category(source.category())
                    .title(title)
                    .summary(trimSummary(stripUrls(messageText.isBlank() ? title : messageText)))
                    .content("")
                    .sourceName(source.sourceName())
                    .publishedAt(parsePublishedAt(message.selectFirst("a.tgme_widget_message_date time[datetime], time")))
                    .featured(false)
                    .externalUrl(externalUrl)
                    .build());

            if (articles.size() >= source.maxItems()) {
                return;
            }
        }
    }

    private void addFromDocument(PublicWebIssueSource source,
                                 Document document,
                                 Set<String> seenUrls,
                                 List<FetchedNewsArticle> articles) {
        if (!isLikelySingleArticlePage(source.url())) {
            return;
        }
        String title = documentTitle(document);
        if (isWeakTitle(title)) {
            return;
        }
        String summary = documentSummary(document);
        String externalUrl = source.url().toString();
        if (!seenUrls.add(externalUrl)) {
            return;
        }

        articles.add(FetchedNewsArticle.builder()
                .id(buildId(externalUrl))
                .category(source.category())
                .title(title)
                .summary(trimSummary(summary))
                .content("")
                .sourceName(source.sourceName())
                .publishedAt(parsePublishedAt(document.selectFirst("time[datetime], time")))
                .featured(false)
                .externalUrl(externalUrl)
                .build());
    }

    private void addFromContainer(PublicWebIssueSource source,
                                  Element container,
                                  Set<String> seenUrls,
                                  List<FetchedNewsArticle> articles) {
        Element anchor = container.selectFirst("a[href]");
        if (anchor == null) {
            return;
        }
        String summary = firstText(container.select("p, [class*=summary], [class*=description], [class*=excerpt]"));
        Element time = container.selectFirst("time[datetime], time, [class*=date]");
        addArticle(source, anchor, summary, time, seenUrls, articles);
    }

    private void addFromAnchor(PublicWebIssueSource source,
                               Element anchor,
                               Set<String> seenUrls,
                               List<FetchedNewsArticle> articles) {
        addArticle(source, anchor, "", nearbyTime(anchor), true, seenUrls, articles);
    }

    private void addArticle(PublicWebIssueSource source,
                            Element anchor,
                            String summary,
                            Element time,
                            boolean requireArticleSignal,
                            Set<String> seenUrls,
                            List<FetchedNewsArticle> articles) {
        String title = cleanText(anchor.text());
        if (isWeakTitle(title)) {
            return;
        }
        URI externalUri = resolveSameHost(source.url(), anchor.attr("href"));
        if (externalUri == null || !seenUrls.add(externalUri.toString())) {
            return;
        }
        if (requireArticleSignal && !isLikelyArticleAnchor(anchor, title, externalUri)) {
            return;
        }

        articles.add(FetchedNewsArticle.builder()
                .id(buildId(externalUri.toString()))
                .category(source.category())
                .title(title)
                .summary(trimSummary(summary))
                .content("")
                .sourceName(source.sourceName())
                .publishedAt(parsePublishedAt(time))
                .featured(false)
                .externalUrl(externalUri.toString())
                .build());
    }

    private void addArticle(PublicWebIssueSource source,
                            Element anchor,
                            String summary,
                            Element time,
                            Set<String> seenUrls,
                            List<FetchedNewsArticle> articles) {
        addArticle(source, anchor, summary, time, false, seenUrls, articles);
    }

    private boolean isTelegramPublicChannel(URI url) {
        String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
        String path = url.getPath() == null ? "" : url.getPath().toLowerCase(Locale.ROOT);
        return (host.equals("t.me") || host.equals("telegram.me")) && path.startsWith("/s/");
    }

    private String telegramMessageUrl(URI sourceUrl, Element message) {
        Element permalink = message.selectFirst("a.tgme_widget_message_date[href]");
        if (permalink != null) {
            return sourceUrl.resolve(permalink.attr("href")).toString();
        }
        String post = message.attr("data-post");
        if (!post.isBlank()) {
            return "https://t.me/" + post;
        }
        return "";
    }

    private String telegramTitle(List<String> lines, String previewTitle) {
        String companyName = "";
        String reportName = "";
        for (String line : lines) {
            if (line.startsWith("기업명:")) {
                companyName = telegramFieldValue(line, "기업명:")
                        .replaceAll("\\(.*$", "")
                        .replaceAll("\\s+A?\\d{6}.*$", "")
                        .trim();
            } else if (line.startsWith("보고서명:")) {
                reportName = telegramFieldValue(line, "보고서명:");
            }
        }
        if (!companyName.isBlank() && !reportName.isBlank()) {
            return cleanText(companyName + " " + reportName);
        }

        for (String line : lines) {
            String candidate = stripTelegramLead(line);
            if (candidate.isBlank()
                    || candidate.matches("\\d{4}\\.\\d{2}\\.\\d{2}.*")
                    || candidate.startsWith("공시링크:")
                    || candidate.startsWith("회사정보:")
                    || candidate.startsWith("https://")
                    || candidate.startsWith("http://")) {
                continue;
            }
            return candidate;
        }
        return stripTelegramLead(previewTitle);
    }

    private String telegramFieldValue(String line, String label) {
        return cleanText(line.substring(label.length()));
    }

    private List<String> telegramLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R+")) {
            String cleaned = cleanText(line).replaceAll("https?://\\S+", "").trim();
            if (!cleaned.isBlank()) {
                lines.add(cleaned);
            }
        }
        return lines;
    }

    private String stripTelegramLead(String value) {
        return cleanText(value)
                .replaceAll("^[✅📌🚨🔥⭐️\\s]+", "")
                .trim();
    }

    private boolean isTelegramNoise(String title) {
        String normalized = title.toUpperCase(Locale.ROOT);
        return normalized.contains("문의 @")
                || normalized.contains("제보 @")
                || normalized.contains("공유는 맘껏")
                || normalized.contains("시간외 단일가 등락률")
                || normalized.contains("JOIN")
                || normalized.contains("DOWNLOAD TELEGRAM");
    }

    private boolean isTelegramLowSignalTitle(String title) {
        String normalized = title.toLowerCase(Locale.ROOT);
        return normalized.startsWith("다만 ")
                || normalized.startsWith("이들은 ")
                || normalized.startsWith("이에 ");
    }

    private String textWithBreaks(Element element) {
        String html = element.html()
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</blockquote>", "\n")
                .replaceAll("(?i)</p>", "\n");
        return Jsoup.parseBodyFragment(html).body().wholeText();
    }

    private String stripUrls(String value) {
        return cleanText(value).replaceAll("https?://\\S+", "").trim();
    }

    private URI resolveSameHost(URI baseUri, String href) {
        if (href == null || href.isBlank()) {
            return null;
        }
        try {
            URI resolved = baseUri.resolve(href.trim());
            if (resolved.getHost() == null || baseUri.getHost() == null) {
                return null;
            }
            if (!resolved.getHost().equalsIgnoreCase(baseUri.getHost())) {
                return null;
            }
            return resolved;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isWeakTitle(String title) {
        if (title.length() < MIN_TITLE_LENGTH) {
            return true;
        }
        return WEAK_LINK_TEXTS.contains(title.toUpperCase(Locale.ROOT));
    }

    private boolean isNavigationAnchor(Element anchor) {
        return anchor.parents().stream().anyMatch(parent -> {
            String tagName = parent.tagName();
            if ("header".equals(tagName) || "nav".equals(tagName) || "footer".equals(tagName)) {
                return true;
            }
            String searchable = (parent.id() + " " + parent.className() + " " + parent.attr("role"))
                    .toLowerCase(Locale.ROOT);
            return containsAny(searchable,
                    "breadcrumb",
                    "footer",
                    "header",
                    "menu",
                    "nav",
                    "pagination",
                    "search",
                    "share",
                    "sidebar",
                    "social",
                    "utility");
        });
    }

    private boolean isLikelyArticleAnchor(Element anchor, String title, URI externalUri) {
        if (isNavigationAnchor(anchor)) {
            return false;
        }
        String path = externalUri.getPath() == null
                ? ""
                : externalUri.getPath().toLowerCase(Locale.ROOT);
        String normalizedTitle = title.toLowerCase(Locale.ROOT);
        if (path.contains("/newsevents/pressreleases/")) {
            return path.contains("/newsevents/pressreleases/monetary")
                    || containsMarketSignal(normalizedTitle);
        }
        if (containsAny(path,
                "/earnings/",
                "/news-release",
                "/news-releases/",
                "/press-release",
                "/pressreleases/",
                "/news.release/",
                "/stories/",
                "/articles/")) {
            return true;
        }

        return containsMarketSignal(normalizedTitle);
    }

    private boolean containsMarketSignal(String normalizedTitle) {
        return containsAny(normalizedTitle,
                "cpi",
                "discount rate",
                "earnings",
                "economic projections",
                "employment",
                "fomc",
                "guidance",
                "inflation",
                "interest rate",
                "payroll",
                "profit",
                "rate decision",
                "reports",
                "revenue",
                "unemployment",
                "공시",
                "금리",
                "매출",
                "수주",
                "실적",
                "예상",
                "영업이익",
                "증시",
                "환율");
    }

    private boolean isLikelySingleArticlePage(URI url) {
        String path = url.getPath() == null ? "" : url.getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith("/news")
                || path.endsWith("/news/")
                || path.endsWith("/pressreleases.htm")
                || path.matches(".*/\\d{4}-press.*\\.htm")
                || path.endsWith("/newsrels.htm")) {
            return false;
        }
        return containsAny(path,
                "/earnings/",
                "/news-release",
                "/news-releases/news-release-details/",
                "/newsevents/pressreleases/",
                "/press-release",
                "/news.release/");
    }

    private String documentTitle(Document document) {
        String title = firstText(document.select("main h1, article h1, h1"));
        if (!title.isBlank()) {
            return title;
        }
        title = cleanText(document.select("meta[property=og:title], meta[name=twitter:title]")
                .stream()
                .map(element -> element.attr("content"))
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(""));
        if (!title.isBlank()) {
            return title;
        }
        return cleanText(document.title());
    }

    private String documentSummary(Document document) {
        String summary = cleanText(document.select("meta[name=description], meta[property=og:description]")
                .stream()
                .map(element -> element.attr("content"))
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(""));
        if (!summary.isBlank()) {
            return summary;
        }
        return firstText(document.select("main p, article p"));
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String firstText(Elements elements) {
        for (Element element : elements) {
            String text = cleanText(element.text());
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private String trimSummary(String value) {
        String summary = cleanText(value);
        if (summary.length() <= MAX_SUMMARY_LENGTH) {
            return summary;
        }
        return summary.substring(0, MAX_SUMMARY_LENGTH).replaceAll("\\s+\\S*$", "").trim();
    }

    private LocalDateTime parsePublishedAt(Element time) {
        if (time == null) {
            return null;
        }
        String value = cleanText(time.hasAttr("datetime") ? time.attr("datetime") : time.text());
        if (value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(KST_ZONE).toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            return ZonedDateTime.parse(value).withZoneSameInstant(KST_ZONE).toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(value, SLASH_DATE_FORMATTER).atStartOfDay();
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(value, ENGLISH_DATE_FORMATTER).atStartOfDay();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Element nearbyTime(Element anchor) {
        Element current = anchor;
        for (int depth = 0; depth < 5 && current != null; depth++) {
            Element time = current.selectFirst("time[datetime], time");
            if (time != null) {
                return time;
            }
            current = current.parent();
        }
        return null;
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

    private String buildId(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return "web_" + HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
