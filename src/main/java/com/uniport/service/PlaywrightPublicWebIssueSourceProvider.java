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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "uniport.investment-issue.public-web", name = "enabled", havingValue = "true")
public class PlaywrightPublicWebIssueSourceProvider implements PublicIssueSourceProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaywrightPublicWebIssueSourceProvider.class);
    private static final String SOURCE_PREFIX = "uniport.investment-issue.public-web.sources";
    private static final int DEFAULT_TIMEOUT_MS = 6000;
    private static final int DEFAULT_MAX_ITEMS = 20;
    private static final int MAX_CONFIGURED_SOURCES = 50;
    private static final String SAVETICKER_API_BASE_URL = "https://api.saveticker.com/api";

    private final PublicWebIssueHtmlExtractor extractor;
    private final SaveTickerNewsJsonMapper saveTickerNewsJsonMapper;
    private final List<PublicWebIssueSource> sources;
    private final int timeoutMs;

    public PlaywrightPublicWebIssueSourceProvider(Environment environment,
                                                 PublicWebIssueHtmlExtractor extractor,
                                                 SaveTickerNewsJsonMapper saveTickerNewsJsonMapper) {
        this.extractor = extractor;
        this.saveTickerNewsJsonMapper = saveTickerNewsJsonMapper;
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
            LOGGER.warn("Public web issue source fetch failed: {}", exception.getMessage(), exception);
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
                List<FetchedNewsArticle> articles = fetchSaveTickerArticles(page, source);
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

    private List<FetchedNewsArticle> fetchSaveTickerArticles(Page page, PublicWebIssueSource source) {
        Map<String, FetchedNewsArticle> articlesById = new LinkedHashMap<>();
        for (String apiUrl : saveTickerApiUrls(source)) {
            try {
                String json = fetchJsonInPage(page, apiUrl);
                for (FetchedNewsArticle article : saveTickerNewsJsonMapper.extract(source, json)) {
                    articlesById.putIfAbsent(article.getId(), article);
                    if (articlesById.size() >= source.maxItems()) {
                        return List.copyOf(articlesById.values());
                    }
                }
            } catch (Exception exception) {
                LOGGER.warn("SaveTicker public news request failed at {}: {}", apiUrl, exception.getMessage());
            }
        }
        return List.copyOf(articlesById.values());
    }

    private List<String> saveTickerApiUrls(PublicWebIssueSource source) {
        int pageSize = Math.max(1, source.maxItems());
        return List.of(
                SAVETICKER_API_BASE_URL + "/news/top-stories",
                SAVETICKER_API_BASE_URL + "/news/list?page=1&page_size=" + pageSize + "&sort=created_at_desc"
        );
    }

    private String fetchJsonInPage(Page page, String apiUrl) {
        Object result = page.evaluate(
                """
                        async (url) => {
                          const response = await fetch(url, {
                            headers: { accept: "application/json" },
                            credentials: "omit",
                          });
                          if (!response.ok) {
                            throw new Error(`${response.status} ${response.statusText}`);
                          }
                          return await response.text();
                        }
                        """,
                apiUrl
        );
        return result instanceof String text ? text : "";
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
