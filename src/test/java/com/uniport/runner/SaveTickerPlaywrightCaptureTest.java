package com.uniport.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

@Disabled("Manual live capture runner; run explicitly only when SaveTicker visual evidence is needed.")
class SaveTickerPlaywrightCaptureTest {

    private static final String NEWS_URL = "https://www.saveticker.com/news";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    void captureTenSaveTickerNewsItemsAndDetails() throws Exception {
        Path outputDir = Path.of("..", "..", "screenshot", "saveticker-playwright").normalize();
        Files.createDirectories(outputDir);

        List<CapturedNews> results = new ArrayList<>();
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                     .setViewportSize(1440, 1800)
                     .setLocale("ko-KR")
                     .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                             + "AppleWebKit/537.36 (KHTML, like Gecko) "
                             + "Chrome/125.0.0.0 Safari/537.36"))) {
            Page page = context.newPage();
            page.setDefaultTimeout(Duration.ofSeconds(25).toMillis());
            page.setDefaultNavigationTimeout(Duration.ofSeconds(60).toMillis());

            page.navigate(NEWS_URL, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(Duration.ofSeconds(60).toMillis()));
            page.waitForTimeout(2_500);
            waitForNewsLinks(page);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(outputDir.resolve("00-news-list.png"))
                    .setFullPage(true));

            List<NewsLink> links = collectNewsLinks(page).stream().limit(10).toList();
            assertFalse(links.isEmpty(), "SaveTicker news links should be captured");

            for (int index = 0; index < links.size(); index++) {
                NewsLink link = links.get(index);
                int number = index + 1;
                String prefix = "%02d".formatted(number);
                page.navigate(NEWS_URL, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(Duration.ofSeconds(60).toMillis()));
                page.waitForTimeout(2_500);
                waitForNewsLinks(page);

                Locator card = page.locator("a[href='" + link.path() + "'], a[href='" + link.url() + "']").first();
                String listText = extractCardText(card);
                if (listText.isBlank()) {
                    listText = link.text();
                }
                card.scrollIntoViewIfNeeded();
                card.screenshot(new Locator.ScreenshotOptions()
                        .setPath(outputDir.resolve(prefix + "-list-card.png")));

                card.click();
                page.waitForTimeout(3_000);
                if (!page.url().contains(link.path())) {
                    page.navigate(link.url(), new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(Duration.ofSeconds(60).toMillis()));
                    page.waitForTimeout(2_000);
                }
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(outputDir.resolve(prefix + "-detail.png"))
                        .setFullPage(true));
                String detailUrl = page.url();
                String detailText = cleanText(page.locator("body").innerText());

                results.add(new CapturedNews(
                        number,
                        link.url(),
                        detailUrl,
                        listText,
                        detailText,
                        "00-news-list.png",
                        prefix + "-list-card.png",
                        prefix + "-detail.png"
                ));
            }
        }

        Files.writeString(
                outputDir.resolve("capture.json"),
                OBJECT_MAPPER.writeValueAsString(results)
        );
        Files.writeString(outputDir.resolve("report.md"), markdownReport(results));
    }

    private void waitForNewsLinks(Page page) {
        page.waitForSelector("a[href^='/news/'], a[href^='https://www.saveticker.com/news/']");
    }

    private List<NewsLink> collectNewsLinks(Page page) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> rawLinks = (List<Map<String, String>>) page.evaluate("""
                () => Array.from(document.querySelectorAll("a[href^='/news/'], a[href^='https://www.saveticker.com/news/']"))
                  .map((anchor) => ({
                    href: anchor.href || "",
                    path: new URL(anchor.href, location.origin).pathname || "",
                    text: anchor.innerText || anchor.textContent || ""
                  }))
                """);
        Set<String> seenPaths = new LinkedHashSet<>();
        List<NewsLink> links = new ArrayList<>();
        for (Map<String, String> rawLink : rawLinks) {
            String url = cleanText(rawLink.getOrDefault("href", ""));
            String path = cleanText(rawLink.getOrDefault("path", ""));
            String text = cleanText(rawLink.getOrDefault("text", ""));
            if (!path.matches("/news/\\d+")) {
                continue;
            }
            if (seenPaths.add(path)) {
                links.add(new NewsLink(url, path, text));
            }
        }
        return links;
    }

    private String extractCardText(Locator card) {
        try {
            Locator container = card.locator("xpath=ancestor::*[self::article or self::li or self::div][1]").first();
            return cleanText(container.innerText());
        } catch (Exception ignored) {
            return cleanText(card.innerText());
        }
    }

    private String markdownReport(List<CapturedNews> results) {
        StringBuilder builder = new StringBuilder();
        builder.append("# SaveTicker Playwright Capture\n\n");
        builder.append("- Source: ").append(NEWS_URL).append('\n');
        builder.append("- Count: ").append(results.size()).append("\n\n");
        for (CapturedNews result : results) {
            builder.append("## ").append(result.index()).append(". ").append(firstLine(result.listText())).append("\n\n");
            builder.append("- List URL: ").append(result.listUrl()).append('\n');
            builder.append("- Detail URL: ").append(result.detailUrl()).append('\n');
            builder.append("- List screenshot: ").append(result.listScreenshot()).append('\n');
            builder.append("- Detail screenshot: ").append(result.detailScreenshot()).append("\n\n");
            builder.append("### List Text\n\n");
            builder.append("```text\n").append(result.listText()).append("\n```\n\n");
            builder.append("### Detail Text\n\n");
            builder.append("```text\n").append(result.detailText()).append("\n```\n\n");
        }
        return builder.toString();
    }

    private String firstLine(String value) {
        String text = cleanText(value);
        if (text.length() <= 120) {
            return text;
        }
        return text.substring(0, 120).trim();
    }

    private static String cleanText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00a0', ' ')
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n+ *", "\n")
                .replaceAll("[ ]{2,}", " ")
                .trim();
    }

    private record NewsLink(String url, String path, String text) {
    }

    private record CapturedNews(
            int index,
            String listUrl,
            String detailUrl,
            String listText,
            String detailText,
            String listPageScreenshot,
            String listScreenshot,
            String detailScreenshot
    ) {
        Map<String, Object> toMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("index", index);
            value.put("listUrl", listUrl);
            value.put("detailUrl", detailUrl);
            value.put("listText", listText);
            value.put("detailText", detailText);
            value.put("listPageScreenshot", listPageScreenshot);
            value.put("listScreenshot", listScreenshot);
            value.put("detailScreenshot", detailScreenshot);
            return value;
        }
    }
}
