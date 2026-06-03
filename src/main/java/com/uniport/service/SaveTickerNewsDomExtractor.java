package com.uniport.service;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SaveTickerNewsDomExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SaveTickerNewsDomExtractor.class);
    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
    private static final String SAVETICKER_ID_PREFIX = "saveticker_";
    private static final Pattern TICKER_PATTERN = Pattern.compile("\\$[A-Za-z][A-Za-z0-9.\\-]{0,15}");
    private static final Pattern KOREAN_DATE_TIME_PATTERN = Pattern.compile(
            "(\\d{4})\\s*년\\s*(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일(?:\\s*(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?"
    );
    private static final Pattern NUMERIC_DATE_TIME_PATTERN = Pattern.compile(
            "(\\d{4})[./-](\\d{1,2})[./-](\\d{1,2})(?:[ T](\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?"
    );
    private static final String LIST_ITEMS_SCRIPT = """
            (maxItems) => {
              const clean = (value) => (value || "")
                .replace(/\\u00a0/g, " ")
                .replace(/[\\t\\v\\f\\r]+/g, " ")
                .replace(/ *\\n+ */g, "\\n")
                .replace(/[ ]{2,}/g, " ")
                .trim();
              const isNewsPath = (path) => /^\\/news\\/\\d+/.test(path || "");
              const containerFor = (anchor) => {
                const anchorText = clean(anchor.innerText || anchor.textContent || "");
                let current = anchor;
                for (let depth = 0; current && depth < 7; depth += 1) {
                  const tag = (current.tagName || "").toLowerCase();
                  const text = clean(current.innerText || current.textContent || "");
                  if ((tag === "article" || tag === "li" || tag === "div") && text.length > anchorText.length) {
                    return current;
                  }
                  current = current.parentElement;
                }
                return anchor;
              };
              const nearestTime = (container) => {
                const scoped = container && container.querySelector
                  ? container.querySelector("time[datetime], time")
                  : null;
                const time = scoped || document.querySelector("time[datetime], time");
                return {
                  datetime: time ? (time.getAttribute("datetime") || "") : "",
                  text: time ? clean(time.innerText || time.textContent || "") : "",
                };
              };
              const anchors = Array.from(document.querySelectorAll(
                "a[href^='/news/'], a[href^='https://www.saveticker.com/news/']"
              ));
              const seen = new Set();
              const items = [];
              for (const anchor of anchors) {
                let url;
                try {
                  url = new URL(anchor.getAttribute("href") || anchor.href || "", location.origin);
                } catch (error) {
                  continue;
                }
                if (!isNewsPath(url.pathname) || seen.has(url.pathname)) {
                  continue;
                }
                const container = containerFor(anchor);
                const time = nearestTime(container);
                items.push({
                  url: url.href,
                  path: url.pathname,
                  title: clean(anchor.innerText || anchor.textContent || ""),
                  listText: clean(container.innerText || container.textContent || ""),
                  datetime: time.datetime,
                  timeText: time.text,
                });
                seen.add(url.pathname);
                if (items.length >= maxItems) {
                  break;
                }
              }
              return items;
            }
            """;
    private static final String REVEAL_FULL_BODY_SCRIPT = """
            () => {
              const clean = (value) => (value || "")
                .replace(/\\u00a0/g, " ")
                .replace(/[\\t\\v\\f\\r\\n]+/g, " ")
                .replace(/[ ]{2,}/g, " ")
                .trim();
              const compact = (value) => clean(value).replace(/\\s+/g, "").toLowerCase();
              const isVisible = (element) => Boolean(
                element &&
                (element.offsetWidth || element.offsetHeight || element.getClientRects().length)
              );
              const labelOf = (element) => clean([
                element.innerText,
                element.textContent,
                element.getAttribute("aria-label"),
                element.getAttribute("title"),
              ].filter(Boolean).join(" "));
              const matchesFullBody = (element) => {
                const label = compact(labelOf(element));
                if (!label) {
                  return false;
                }
                return [
                  "본문전체",
                  "전체본문",
                  "원문전체",
                  "전체기사",
                  "기사본문",
                  "본문보기",
                  "원문보기",
                  "fulltext",
                  "fullarticle",
                  "articlebody",
                  "originalarticle",
                ].some((keyword) => label.includes(keyword));
              };
              const candidates = Array.from(document.querySelectorAll(
                "button, a, [role='tab'], [role='button'], [tabindex]"
              ));
              const target = candidates.find((element) => isVisible(element) && matchesFullBody(element));
              if (!target) {
                return false;
              }
              target.scrollIntoView({ block: "center", inline: "center" });
              target.click();
              target.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
              return true;
            }
            """;
    private static final String DETAIL_SCRIPT = """
            () => {
              const clean = (value) => (value || "")
                .replace(/\\u00a0/g, " ")
                .replace(/[\\t\\v\\f\\r]+/g, " ")
                .replace(/ *\\n+ */g, "\\n")
                .replace(/[ ]{2,}/g, " ")
                .trim();
              const main = document.querySelector("main, article") || document.body;
              const titleElement = document.querySelector(
                "main h1, article h1, h1, [class*='title'], [class*='headline']"
              );
              const time = document.querySelector("main time[datetime], article time[datetime], time[datetime], time");
              return {
                url: location.href,
                title: clean(titleElement ? (titleElement.innerText || titleElement.textContent || "") : document.title),
                bodyText: clean(main ? (main.innerText || main.textContent || "") : document.body.innerText),
                datetime: time ? (time.getAttribute("datetime") || "") : "",
                timeText: time ? clean(time.innerText || time.textContent || "") : "",
              };
            }
            """;

    public List<FetchedNewsArticle> extract(PublicWebIssueSource source, Page page, int timeoutMs) {
        if (source == null || page == null) {
            return List.of();
        }
        List<DomListItem> listItems = readListItems(page, source.maxItems());
        List<FetchedNewsArticle> articles = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        for (DomListItem listItem : listItems) {
            if (listItem.url().isBlank() || !seenUrls.add(listItem.url())) {
                continue;
            }
            DomDetail detail;
            try {
                detail = readDetail(page, listItem.url(), timeoutMs);
            } catch (Exception exception) {
                LOGGER.warn("SaveTicker detail DOM extraction failed at {}: {}", listItem.url(), exception.getMessage());
                detail = DomDetail.empty(listItem.url());
            }
            FetchedNewsArticle article = toArticle(source, listItem, detail, articles.isEmpty());
            if (article != null) {
                articles.add(article);
            }
            if (articles.size() >= source.maxItems()) {
                break;
            }
        }
        return List.copyOf(articles);
    }

    private List<DomListItem> readListItems(Page page, int maxItems) {
        page.waitForTimeout(1500);
        Object result = page.evaluate(LIST_ITEMS_SCRIPT, maxItems);
        if (!(result instanceof List<?> rawItems)) {
            return List.of();
        }
        List<DomListItem> items = new ArrayList<>();
        for (Object rawItem : rawItems) {
            if (!(rawItem instanceof Map<?, ?> values)) {
                continue;
            }
            DomListItem item = new DomListItem(
                    stringValue(values.get("url")),
                    stringValue(values.get("path")),
                    stringValue(values.get("title")),
                    stringValue(values.get("listText")),
                    stringValue(values.get("datetime")),
                    stringValue(values.get("timeText"))
            );
            if (!item.url().isBlank()) {
                items.add(item);
            }
        }
        return List.copyOf(items);
    }

    private DomDetail readDetail(Page page, String url, int timeoutMs) {
        page.navigate(
                url,
                new Page.NavigateOptions()
                        .setTimeout(timeoutMs)
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
        );
        page.waitForTimeout(1000);
        if (revealFullBody(page)) {
            page.waitForTimeout(700);
        }
        Object result = page.evaluate(DETAIL_SCRIPT);
        if (!(result instanceof Map<?, ?> values)) {
            return DomDetail.empty(url);
        }
        return new DomDetail(
                stringValue(values.get("url")),
                stringValue(values.get("title")),
                stringValue(values.get("bodyText")),
                stringValue(values.get("datetime")),
                stringValue(values.get("timeText"))
        );
    }

    private boolean revealFullBody(Page page) {
        try {
            Object result = page.evaluate(REVEAL_FULL_BODY_SCRIPT);
            return result instanceof Boolean clicked && clicked;
        } catch (Exception exception) {
            LOGGER.warn("SaveTicker full-body tab reveal failed: {}", exception.getMessage());
            return false;
        }
    }

    private FetchedNewsArticle toArticle(PublicWebIssueSource source,
                                         DomListItem listItem,
                                         DomDetail detail,
                                         boolean featured) {
        String externalUrl = firstNonBlank(detail.url(), listItem.url());
        String title = titleFrom(detail, listItem);
        if (externalUrl.isBlank() || title.length() < 8) {
            return null;
        }
        String summary = firstNonBlank(
                summaryFrom(title, detail.bodyText()),
                summaryFrom(title, listItem.listText()),
                title
        );
        String searchableText = cleanLine(listItem.listText() + " " + detail.bodyText());
        return FetchedNewsArticle.builder()
                .id(articleId(externalUrl))
                .category(source.category())
                .title(title)
                .summary(summary)
                .content(tickerText(searchableText))
                .sourceName(sourceName(source, searchableText))
                .publishedAt(parseDateTime(firstNonBlank(
                        detail.datetime(),
                        detail.timeText(),
                        listItem.datetime(),
                        listItem.timeText(),
                        searchableText
                )))
                .featured(featured)
                .externalUrl(externalUrl)
                .build();
    }

    private String titleFrom(DomDetail detail, DomListItem listItem) {
        for (String candidate : List.of(
                cleanTitle(detail.title()),
                cleanTitle(listItem.title()),
                firstTitleLine(detail.bodyText()),
                firstTitleLine(listItem.listText())
        )) {
            String title = cleanTitle(candidate);
            if (!isNoiseLine(title, "") && title.length() >= 8) {
                return title;
            }
        }
        return "";
    }

    private String firstTitleLine(String value) {
        for (String line : lines(value)) {
            if (!isNoiseLine(line, "")) {
                return cleanTitle(line);
            }
        }
        return "";
    }

    private String summaryFrom(String title, String value) {
        List<String> summaryLines = new ArrayList<>();
        for (String line : lines(value)) {
            if (isNoiseLine(line, title)) {
                continue;
            }
            summaryLines.add(line);
        }
        return cleanLine(String.join(" ", summaryLines));
    }

    private boolean isNoiseLine(String line, String title) {
        String cleaned = cleanLine(line);
        if (cleaned.isBlank()) {
            return true;
        }
        if (!title.isBlank() && cleaned.equals(cleanLine(title))) {
            return true;
        }
        String normalized = cleaned.toLowerCase(Locale.ROOT);
        if (normalized.equals("saveticker")
                || normalized.equals("news")
                || normalized.equals("reuters")
                || normalized.equals("financial juice")
                || normalized.equals("financial-juice")
                || normalized.equals("login")
                || normalized.equals("sign in")
                || normalized.equals("subscribe")
                || normalized.equals("share")) {
            return true;
        }
        if (cleaned.matches("\\$[A-Za-z][A-Za-z0-9.\\-]{0,15}")) {
            return true;
        }
        if (parseDateTime(cleaned) != null && cleaned.length() <= 32) {
            return true;
        }
        return false;
    }

    private String tickerText(String value) {
        Matcher matcher = TICKER_PATTERN.matcher(value);
        Set<String> tickers = new LinkedHashSet<>();
        while (matcher.find()) {
            String ticker = matcher.group().toUpperCase(Locale.ROOT);
            tickers.add(ticker);
            tickers.add(ticker.substring(1));
        }
        return String.join(" ", tickers);
    }

    private String sourceName(PublicWebIssueSource source, String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.contains("reuters") || normalized.contains("로이터")) {
            return "Reuters";
        }
        if (normalized.contains("financial juice") || normalized.contains("financial-juice")) {
            return "Financial Juice";
        }
        return source.sourceName();
    }

    private LocalDateTime parseDateTime(String value) {
        String cleaned = cleanLine(value);
        if (cleaned.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(cleaned).atZoneSameInstant(KST_ZONE).toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(cleaned);
        } catch (Exception ignored) {
        }
        Matcher koreanDateTime = KOREAN_DATE_TIME_PATTERN.matcher(cleaned);
        if (koreanDateTime.find()) {
            return dateTimeFrom(koreanDateTime);
        }
        Matcher numericDateTime = NUMERIC_DATE_TIME_PATTERN.matcher(cleaned);
        if (numericDateTime.find()) {
            return dateTimeFrom(numericDateTime);
        }
        return null;
    }

    private LocalDateTime dateTimeFrom(Matcher matcher) {
        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int day = Integer.parseInt(matcher.group(3));
        String hour = matcher.group(4);
        String minute = matcher.group(5);
        String second = matcher.group(6);
        if (hour == null || minute == null) {
            return LocalDate.of(year, month, day).atStartOfDay();
        }
        return LocalDateTime.of(
                year,
                month,
                day,
                Integer.parseInt(hour),
                Integer.parseInt(minute),
                second == null ? 0 : Integer.parseInt(second)
        );
    }

    private String articleId(String externalUrl) {
        try {
            URI uri = URI.create(externalUrl);
            String path = uri.getPath() == null ? "" : uri.getPath();
            String[] segments = path.split("/");
            String lastSegment = segments.length == 0 ? "" : segments[segments.length - 1];
            if (!lastSegment.isBlank()) {
                return SAVETICKER_ID_PREFIX + lastSegment.replaceAll("[^A-Za-z0-9_-]", "_");
            }
        } catch (Exception ignored) {
        }
        return SAVETICKER_ID_PREFIX + hash(externalUrl);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private List<String> lines(String value) {
        String multiline = cleanMultiline(value);
        if (multiline.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String line : multiline.split("\\R+")) {
            String cleaned = cleanLine(line);
            if (!cleaned.isBlank()) {
                lines.add(cleaned);
            }
        }
        return lines;
    }

    private String cleanTitle(String value) {
        return cleanLine(value)
                .replaceAll("(?i)\\s+[|\\-]\\s+SaveTicker.*$", "")
                .trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String cleaned = cleanLine(value);
            if (!cleaned.isBlank()) {
                return cleaned;
            }
        }
        return "";
    }

    private String cleanMultiline(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ')
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n+ *", "\n")
                .replaceAll("[ ]{2,}", " ")
                .trim();
    }

    private String cleanLine(String value) {
        return cleanMultiline(value)
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String stringValue(Object value) {
        return value == null ? "" : cleanMultiline(String.valueOf(value));
    }

    private record DomListItem(
            String url,
            String path,
            String title,
            String listText,
            String datetime,
            String timeText
    ) {
    }

    private record DomDetail(
            String url,
            String title,
            String bodyText,
            String datetime,
            String timeText
    ) {

        static DomDetail empty(String url) {
            return new DomDetail(url, "", "", "", "");
        }
    }
}
