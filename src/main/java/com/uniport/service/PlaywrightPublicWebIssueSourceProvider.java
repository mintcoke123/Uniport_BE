package com.uniport.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@ConditionalOnProperty(prefix = "uniport.investment-issue.public-web", name = "enabled", havingValue = "true")
public class PlaywrightPublicWebIssueSourceProvider implements PublicIssueSourceProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaywrightPublicWebIssueSourceProvider.class);
    private static final String SOURCE_PREFIX = "uniport.investment-issue.public-web.sources";
    private static final int DEFAULT_TIMEOUT_MS = 6000;
    private static final int DEFAULT_MAX_ITEMS = 20;
    private static final int MAX_CONFIGURED_SOURCES = 50;

    private final PublicWebIssueHtmlExtractor extractor;
    private final SaveTickerNewsDomExtractor saveTickerNewsDomExtractor;
    private final HttpClient httpClient;
    private final List<PublicWebIssueSource> sources;
    private final int timeoutMs;

    public PlaywrightPublicWebIssueSourceProvider(Environment environment,
                                                 PublicWebIssueHtmlExtractor extractor,
                                                 SaveTickerNewsDomExtractor saveTickerNewsDomExtractor) {
        this.extractor = extractor;
        this.saveTickerNewsDomExtractor = saveTickerNewsDomExtractor;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofMillis(DEFAULT_TIMEOUT_MS))
                .build();
        this.sources = readSources(environment);
        this.timeoutMs = Math.max(1000, environment.getProperty(
                "uniport.investment-issue.public-web.timeout-ms",
                Integer.class,
                DEFAULT_TIMEOUT_MS
        ));
    }

    @Override
    public List<FetchedNewsArticle> fetchLatest() {
        if (sources.isEmpty()) {
            LOGGER.warn("Public web issue source is enabled but no sources are configured.");
            return List.of();
        }

        List<FetchedNewsArticle> articles = new ArrayList<>();
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            for (PublicWebIssueSource source : sources) {
                articles.addAll(fetchSource(browser, source));
            }
        } catch (Exception exception) {
            LOGGER.warn(
                    "Public web issue source browser fetch failed, falling back to HTTP fetch: {}",
                    exception.getMessage(),
                    exception
            );
            for (PublicWebIssueSource source : sources) {
                articles.addAll(fetchSourceWithoutBrowser(source));
            }
        }
        return List.copyOf(articles);
    }

    private List<FetchedNewsArticle> fetchSource(Browser browser, PublicWebIssueSource source) {
        try (Page page = browser.newPage()) {
            page.navigate(
                    source.url().toString(),
                    new Page.NavigateOptions()
                            .setTimeout(timeoutMs)
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
            );
            if (isSaveTickerNewsSource(source)) {
                List<FetchedNewsArticle> articles = saveTickerNewsDomExtractor.extract(source, page, timeoutMs);
                LOGGER.info(
                        "Public web issue source '{}' fetched {} articles from {}",
                        source.name(),
                        articles.size(),
                        source.url()
                );
                return articles;
            }
            List<FetchedNewsArticle> articles = extractor.extract(source, page.content());
            LOGGER.info(
                    "Public web issue source '{}' fetched {} articles from {}",
                    source.name(),
                    articles.size(),
                    source.url()
            );
            return articles;
        } catch (Exception exception) {
            LOGGER.warn(
                    "Public web issue source '{}' failed at {}: {}",
                    source.name(),
                    source.url(),
                    exception.getMessage(),
                    exception
            );
            return List.of();
        }
    }

    private List<FetchedNewsArticle> fetchSourceWithoutBrowser(PublicWebIssueSource source) {
        try {
            String html = fetchText(source.url().toString());
            List<FetchedNewsArticle> articles = extractor.extract(source, html);
            LOGGER.info(
                    "Public web issue source '{}' fetched {} articles from {} using HTTP fallback",
                    source.name(),
                    articles.size(),
                    source.url()
            );
            return articles;
        } catch (Exception exception) {
            LOGGER.warn(
                    "Public web issue source '{}' HTTP fallback failed at {}: {}",
                    source.name(),
                    source.url(),
                    exception.getMessage(),
                    exception
            );
            return List.of();
        }
    }

    private String fetchText(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                    .header("accept-language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("user-agent", "Mozilla/5.0 UniportInvestmentIssueCollector/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
            return response.body() == null ? "" : response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP fetch interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("HTTP fetch failed: " + exception.getMessage(), exception);
        }
    }

    private boolean isSaveTickerNewsSource(PublicWebIssueSource source) {
        String host = source.url().getHost() == null ? "" : source.url().getHost().toLowerCase(Locale.ROOT);
        String path = source.url().getPath() == null ? "" : source.url().getPath().toLowerCase(Locale.ROOT);
        return host.equals("www.saveticker.com") && (path.equals("/news") || path.startsWith("/news/"));
    }

    private List<PublicWebIssueSource> readSources(Environment environment) {
        List<PublicWebIssueSource> parsedSources = new ArrayList<>();
        for (int index = 0; index < MAX_CONFIGURED_SOURCES; index++) {
            String url = environment.getProperty(SOURCE_PREFIX + "[" + index + "].url");
            if (url == null || url.isBlank()) {
                if (index == 0) {
                    continue;
                }
                break;
            }
            PublicWebIssueSource source = parseSource(environment, index, url);
            if (source != null) {
                parsedSources.add(source);
            }
        }
        return List.copyOf(parsedSources);
    }

    private PublicWebIssueSource parseSource(Environment environment, int index, String url) {
        String prefix = SOURCE_PREFIX + "[" + index + "]";
        try {
            String name = environment.getProperty(prefix + ".name");
            NewsCategory category = parseCategory(environment.getProperty(prefix + ".category"));
            String sourceName = environment.getProperty(prefix + ".source-name", name);
            int maxItems = environment.getProperty(prefix + ".max-items", Integer.class, DEFAULT_MAX_ITEMS);
            return new PublicWebIssueSource(name, URI.create(url.trim()), category, sourceName, maxItems);
        } catch (Exception exception) {
            LOGGER.warn("Ignoring invalid public web issue source at index {}: {}", index, exception.getMessage());
            return null;
        }
    }

    private NewsCategory parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return NewsCategory.OVERSEAS_STOCK;
        }
        try {
            return NewsCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Unsupported public web issue source category '{}', using OVERSEAS_STOCK.", value);
            return NewsCategory.OVERSEAS_STOCK;
        }
    }
}
