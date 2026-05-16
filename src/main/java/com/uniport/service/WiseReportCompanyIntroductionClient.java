package com.uniport.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WiseReportCompanyIntroductionClient {

    private static final Pattern OVERVIEW_ITEM_PATTERN = Pattern.compile(
            "<li[^>]*class=[\"']dot_cmp[\"'][^>]*>(.*?)</li>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final String baseUrl;

    @Autowired
    public WiseReportCompanyIntroductionClient(
            RestTemplate restTemplate,
            @Value("${wisereport.company-introduction.enabled:true}") boolean enabled,
            @Value("${wisereport.company-introduction.base-url:https://navercomp.wisereport.co.kr}") String baseUrl
    ) {
        this.restTemplate = restTemplate;
        this.enabled = enabled;
        this.baseUrl = baseUrl != null && !baseUrl.isBlank()
                ? baseUrl.trim()
                : "https://navercomp.wisereport.co.kr";
    }

    Optional<String> fetchCompanyIntroduction(String stockCode) {
        String normalizedCode = normalizeStockCode(stockCode);
        if (!enabled || normalizedCode.isBlank()) {
            return Optional.empty();
        }

        URI uri = UriComponentsBuilder
                .fromUriString(baseUrl)
                .path("/v2/company/c1010001.aspx")
                .queryParam("cmp_cd", normalizedCode)
                .build(true)
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 Uniport/1.0");
        headers.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml");

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }
            return parseCompanyIntroduction(response.getBody());
        } catch (RestClientException e) {
            return Optional.empty();
        }
    }

    static Optional<String> parseCompanyIntroduction(String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        int overviewIndex = html.indexOf("<span>기업개요</span>");
        if (overviewIndex < 0) {
            overviewIndex = html.indexOf("기업개요");
        }
        if (overviewIndex < 0) {
            return Optional.empty();
        }
        int commentIndex = html.indexOf("cmp_comment", overviewIndex);
        if (commentIndex < 0) {
            return Optional.empty();
        }
        int overviewEndIndex = html.indexOf("</ul>", commentIndex);
        String overviewSection = overviewEndIndex > commentIndex
                ? html.substring(commentIndex, overviewEndIndex)
                : html.substring(commentIndex);
        Matcher matcher = OVERVIEW_ITEM_PATTERN.matcher(overviewSection);
        List<String> paragraphs = matcher.results()
                .map(result -> cleanText(result.group(1)))
                .filter(text -> !text.isBlank())
                .toList();
        if (paragraphs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(String.join("\n", paragraphs));
    }

    private static String cleanText(String rawHtml) {
        String withoutTags = HTML_TAG_PATTERN.matcher(rawHtml).replaceAll(" ");
        return HtmlUtils.htmlUnescape(withoutTags)
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalizeStockCode(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return "";
        }
        String digits = stockCode.replaceAll("\\D", "");
        if (digits.isBlank()) {
            return "";
        }
        if (digits.length() >= 6) {
            return digits.substring(digits.length() - 6);
        }
        return "0".repeat(6 - digits.length()) + digits;
    }
}
