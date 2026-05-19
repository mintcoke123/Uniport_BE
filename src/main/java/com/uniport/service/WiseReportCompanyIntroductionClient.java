package com.uniport.service;

import com.uniport.dto.FinancialDataItemDTO;
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

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WiseReportCompanyIntroductionClient {

    private static final int DEFAULT_LATEST_ANNUAL_COLUMN_INDEX = 3;
    private static final Pattern OVERVIEW_ITEM_PATTERN = Pattern.compile(
            "<li[^>]*class=[\"']dot_cmp[\"'][^>]*>(.*?)</li>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern FINANCIAL_ENCPARAM_PATTERN = Pattern.compile(
            "encparam\\s*:\\s*'([^']+)'",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FINANCIAL_ID_PATTERN = Pattern.compile(
            "id\\s*:\\s*'([^']+)'\\s*\\?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FINANCIAL_HEADER_PATTERN = Pattern.compile(
            "<th[^>]*>\\s*(\\d{4}/\\d{2})\\s*<br",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern FINANCIAL_ROW_PATTERN = Pattern.compile(
            "<tr>\\s*<th[^>]*>(.*?)</th>(.*?)</tr>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern FINANCIAL_CELL_PATTERN = Pattern.compile(
            "<td[^>]*>(.*?)</td>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern TITLE_ATTRIBUTE_PATTERN = Pattern.compile(
            "title=[\"']([^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

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

        try {
            return fetchCompanyPage(normalizedCode)
                    .flatMap(WiseReportCompanyIntroductionClient::parseCompanyIntroduction);
        } catch (RestClientException e) {
            return Optional.empty();
        }
    }

    List<FinancialDataItemDTO> fetchFinancialData(String stockCode) {
        String normalizedCode = normalizeStockCode(stockCode);
        if (!enabled || normalizedCode.isBlank()) {
            return List.of();
        }

        try {
            Optional<String> pageHtml = fetchCompanyPage(normalizedCode);
            if (pageHtml.isEmpty()) {
                return List.of();
            }
            Optional<FinancialRequestTokens> tokens = parseFinancialRequestTokens(pageHtml.get());
            if (tokens.isEmpty()) {
                return List.of();
            }

            URI pageUri = companyPageUri(normalizedCode);
            URI financialUri = UriComponentsBuilder
                    .fromUriString(baseUrl)
                    .path("/v2/company/ajax/cF1001.aspx")
                    .queryParam("cmp_cd", normalizedCode)
                    .queryParam("fin_typ", "0")
                    .queryParam("freq_typ", "A")
                    .queryParam("extY", "0")
                    .queryParam("extQ", "0")
                    .queryParam("encparam", tokens.get().encparam())
                    .queryParam("id", tokens.get().id())
                    .build(true)
                    .toUri();

            HttpHeaders headers = defaultHeaders();
            headers.set(HttpHeaders.REFERER, pageUri.toString());
            ResponseEntity<String> response = restTemplate.exchange(
                    financialUri,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return List.of();
            }
            return parseFinancialData(response.getBody());
        } catch (RestClientException e) {
            return List.of();
        }
    }

    private Optional<String> fetchCompanyPage(String normalizedCode) {
        URI uri = companyPageUri(normalizedCode);
        ResponseEntity<String> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(defaultHeaders()),
                String.class
        );
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return Optional.empty();
        }
        return Optional.of(response.getBody());
    }

    private URI companyPageUri(String normalizedCode) {
        return UriComponentsBuilder
                .fromUriString(baseUrl)
                .path("/v2/company/c1010001.aspx")
                .queryParam("cmp_cd", normalizedCode)
                .build(true)
                .toUri();
    }

    private HttpHeaders defaultHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 Uniport/1.0");
        headers.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml");
        return headers;
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

    static List<FinancialDataItemDTO> parseFinancialData(String html) {
        if (html == null || html.isBlank() || !html.contains("주요재무정보")) {
            return List.of();
        }

        List<String> periods = FINANCIAL_HEADER_PATTERN.matcher(html)
                .results()
                .map(result -> cleanText(result.group(1)))
                .filter(text -> !text.isBlank())
                .toList();
        int valueIndex = Math.min(DEFAULT_LATEST_ANNUAL_COLUMN_INDEX, Math.max(periods.size() - 1, 0));
        if (periods.isEmpty()) {
            return List.of();
        }

        BigDecimal revenue = null;
        BigDecimal grossProfit = null;
        BigDecimal operatingProfit = null;
        Matcher rowMatcher = FINANCIAL_ROW_PATTERN.matcher(html);
        while (rowMatcher.find()) {
            String label = cleanText(rowMatcher.group(1)).replace(" ", "");
            List<BigDecimal> values = parseFinancialRowValues(rowMatcher.group(2));
            if (values.size() <= valueIndex) {
                continue;
            }
            BigDecimal value = values.get(valueIndex);
            if ("매출액".equals(label)) {
                revenue = value;
            } else if ("매출총이익".equals(label) || "총이익".equals(label)) {
                grossProfit = value;
            } else if ("영업이익".equals(label)) {
                operatingProfit = value;
            }
        }

        if (revenue == null && grossProfit == null && operatingProfit == null) {
            return List.of();
        }

        return List.of(FinancialDataItemDTO.builder()
                .quarter(periods.get(valueIndex))
                .value(toFinancialValueLabel(revenue, grossProfit, operatingProfit))
                .revenue(revenue)
                .grossProfit(grossProfit)
                .operatingProfit(operatingProfit)
                .build());
    }

    private static Optional<FinancialRequestTokens> parseFinancialRequestTokens(String html) {
        Matcher encparamMatcher = FINANCIAL_ENCPARAM_PATTERN.matcher(html);
        Matcher idMatcher = FINANCIAL_ID_PATTERN.matcher(html);
        if (!encparamMatcher.find() || !idMatcher.find()) {
            return Optional.empty();
        }
        return Optional.of(new FinancialRequestTokens(encparamMatcher.group(1), idMatcher.group(1)));
    }

    private static List<BigDecimal> parseFinancialRowValues(String rowHtml) {
        List<BigDecimal> values = new ArrayList<>();
        Matcher cellMatcher = FINANCIAL_CELL_PATTERN.matcher(rowHtml);
        while (cellMatcher.find()) {
            Matcher titleMatcher = TITLE_ATTRIBUTE_PATTERN.matcher(cellMatcher.group());
            String title = titleMatcher.find() ? titleMatcher.group(1) : "";
            String text = cleanText(cellMatcher.group(1));
            values.add(parseDecimal(title != null && !title.isBlank() ? title : text));
        }
        return values;
    }

    private static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw
                .replace(",", "")
                .replace("억원", "")
                .replace("원", "")
                .replace("억", "")
                .replace("%", "")
                .trim();
        if (normalized.isBlank() || "N/A".equalsIgnoreCase(normalized)) {
            return null;
        }
        try {
            return new BigDecimal(normalized).setScale(0, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String toFinancialValueLabel(
            BigDecimal revenue,
            BigDecimal grossProfit,
            BigDecimal operatingProfit
    ) {
        List<String> parts = new ArrayList<>();
        if (revenue != null) {
            parts.add("매출 " + formatAmount(revenue) + "억원");
        }
        if (grossProfit != null) {
            parts.add("매출총이익 " + formatAmount(grossProfit) + "억원");
        }
        if (operatingProfit != null) {
            parts.add("영업이익 " + formatAmount(operatingProfit) + "억원");
        }
        return String.join(" · ", parts);
    }

    private static String formatAmount(BigDecimal amount) {
        return String.format("%,d", amount.setScale(0, java.math.RoundingMode.HALF_UP).longValue());
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

    private record FinancialRequestTokens(String encparam, String id) {
    }
}
