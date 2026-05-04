package com.uniport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.dto.FinancialDataItemDTO;
import com.uniport.dto.StockNewsCompanyInfoDTO;
import com.uniport.dto.StockNewsDetailResponseDTO;
import com.uniport.dto.StockNewsListItemDTO;
import com.uniport.dto.StockNewsListResponseDTO;
import com.uniport.dto.StockNewsOpinionDTO;
import com.uniport.dto.StockNewsTagDTO;
import com.uniport.entity.ManagedNewsArticle;
import com.uniport.exception.ApiException;
import com.uniport.repository.ManagedNewsArticleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ManagedStockNewsService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<StockNewsTagDTO>> TAG_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<StockNewsOpinionDTO>> OPINION_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP_TYPE = new TypeReference<>() {};

    private final ManagedNewsArticleRepository managedNewsArticleRepository;

    public ManagedStockNewsService(ManagedNewsArticleRepository managedNewsArticleRepository) {
        this.managedNewsArticleRepository = managedNewsArticleRepository;
    }

    public StockNewsListResponseDTO getNewsList(String keyword, String sort, Integer page, Integer size) {
        String normalizedKeyword = keyword != null ? keyword.trim() : "";
        String normalizedSort = normalizeSort(sort);
        int safePage = page != null && page >= 0 ? page : 0;
        int safeSize = size != null && size > 0 ? Math.min(size, 20) : 10;

        List<ManagedNewsArticle> articles = managedNewsArticleRepository.findAllByOrderByPublishedAtDescIdDesc();
        List<ManagedNewsArticle> filtered = articles.stream()
                .filter(article -> matchesKeyword(article, normalizedKeyword))
                .sorted(resolveComparator(normalizedSort))
                .toList();

        int fromIndex = Math.min(safePage * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        boolean hasNext = toIndex < filtered.size();

        List<StockNewsListItemDTO> items = filtered.subList(fromIndex, toIndex).stream()
                .map(this::toListItem)
                .toList();

        return StockNewsListResponseDTO.builder()
                .items(items)
                .sort(normalizedSort)
                .keyword(normalizedKeyword.isBlank() ? null : normalizedKeyword)
                .page(safePage)
                .size(safeSize)
                .hasNext(hasNext)
                .build();
    }

    public StockNewsDetailResponseDTO getNewsDetail(String newsId) {
        ManagedNewsArticle article = resolveByNewsId(newsId);
        return toDetail(article);
    }

    public List<ManagedNewsArticle> getNewsForStock(String stockCode, String stockName, int limit) {
        List<ManagedNewsArticle> matches = managedNewsArticleRepository.searchByStock(normalizeStockCode(stockCode), stockName);
        if (matches.isEmpty()) {
            return List.of();
        }
        return matches.stream().limit(Math.max(limit, 0)).toList();
    }

    public List<FinancialDataItemDTO> extractFinancialData(ManagedNewsArticle article) {
        Map<String, Object> companyInfo = parseJson(article.getCompanyInfoJson(), MAP_TYPE, Map.of());
        Object raw = companyInfo.get("financialData");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }

        List<FinancialDataItemDTO> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            result.add(FinancialDataItemDTO.builder()
                    .quarter(stringValue(map.get("quarter")))
                    .revenue(decimalValue(map.get("revenue")))
                    .grossProfit(decimalValue(map.get("grossProfit")))
                    .operatingProfit(decimalValue(map.get("operatingProfit")))
                    .build());
        }
        return result;
    }

    public String extractCompanyDescription(ManagedNewsArticle article) {
        Map<String, Object> companyInfo = parseJson(article.getCompanyInfoJson(), MAP_TYPE, Map.of());
        String description = stringValue(companyInfo.get("description"));
        if (!description.isBlank()) {
            return description;
        }
        return "";
    }

    private ManagedNewsArticle resolveByNewsId(String newsId) {
        return managedNewsArticleRepository.findByNewsKey(newsId)
                .orElseGet(() -> {
                    try {
                        long id = Long.parseLong(newsId);
                        return managedNewsArticleRepository.findById(id)
                                .orElseThrow(() -> new ApiException("News article not found", HttpStatus.NOT_FOUND));
                    } catch (NumberFormatException ignored) {
                        throw new ApiException("News article not found", HttpStatus.NOT_FOUND);
                    }
                });
    }

    private boolean matchesKeyword(ManagedNewsArticle article, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String lower = keyword.toLowerCase(Locale.ROOT);
        return contains(article.getTitle(), lower)
                || contains(article.getSummary(), lower)
                || contains(article.getStockName(), lower)
                || contains(article.getStockCode(), lower)
                || parseJson(article.getTagsJson(), TAG_LIST_TYPE, List.<StockNewsTagDTO>of()).stream()
                .anyMatch(tag -> contains(tag.getLabel(), lower));
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private Comparator<ManagedNewsArticle> resolveComparator(String sort) {
        if ("POPULAR".equals(sort)) {
            return Comparator.comparingInt(this::popularityScore).reversed()
                    .thenComparing(ManagedStockNewsService::publishedAt, Comparator.reverseOrder())
                    .thenComparing(ManagedNewsArticle::getId, Comparator.reverseOrder());
        }
        return Comparator.comparing(ManagedStockNewsService::publishedAt, Comparator.reverseOrder())
                .thenComparing(ManagedNewsArticle::getId, Comparator.reverseOrder());
    }

    private static LocalDateTime publishedAt(ManagedNewsArticle article) {
        return article.getPublishedAt() != null ? article.getPublishedAt() : article.getCreatedAt();
    }

    private int popularityScore(ManagedNewsArticle article) {
        List<StockNewsOpinionDTO> opinions = parseJson(article.getOpinionsJson(), OPINION_LIST_TYPE, List.of());
        return opinions.isEmpty() ? 50 : 50 + opinions.size() * 10;
    }

    private String normalizeSort(String sort) {
        String value = sort != null ? sort.trim().toUpperCase(Locale.ROOT) : "LATEST";
        return "POPULAR".equals(value) ? "POPULAR" : "LATEST";
    }

    private StockNewsListItemDTO toListItem(ManagedNewsArticle article) {
        return StockNewsListItemDTO.builder()
                .newsId(article.getNewsKey() != null ? article.getNewsKey() : String.valueOf(article.getId()))
                .title(article.getTitle())
                .sourceLabel(article.getSourceLabel())
                .imageUrl(article.getImageUrl())
                .tags(parseJson(article.getTagsJson(), TAG_LIST_TYPE, List.of()))
                .publishedAt(toIso(article.getPublishedAt()))
                .popularityScore(popularityScore(article))
                .build();
    }

    private StockNewsDetailResponseDTO toDetail(ManagedNewsArticle article) {
        Map<String, Object> companyInfoMap = parseJson(article.getCompanyInfoJson(), MAP_TYPE, Map.of());
        StockNewsCompanyInfoDTO company = StockNewsCompanyInfoDTO.builder()
                .stockName(defaultIfBlank(stringValue(companyInfoMap.get("stockName")), article.getStockName()))
                .stockCode(defaultIfBlank(stringValue(companyInfoMap.get("stockCode")), article.getStockCode()))
                .description(stringValue(companyInfoMap.get("description")))
                .source(defaultIfBlank(stringValue(companyInfoMap.get("source")), article.getSourceLabel()))
                .stockPricePath(defaultIfBlank(stringValue(companyInfoMap.get("stockPricePath")),
                        article.getStockCode() != null ? "/api/stocks/search?keyword=" + article.getStockCode() : null))
                .build();

        List<String> bodyParagraphs = splitBody(article.getContent());
        List<String> keyPoints = splitKeyPoints(companyInfoMap.get("keyPoints"));
        StockNewsOpinionDTO opinion = parseJson(article.getOpinionsJson(), OPINION_LIST_TYPE, List.of())
                .stream()
                .findFirst()
                .orElse(null);

        return StockNewsDetailResponseDTO.builder()
                .newsId(article.getNewsKey() != null ? article.getNewsKey() : String.valueOf(article.getId()))
                .title(article.getTitle())
                .source(article.getSourceLabel())
                .sourceLabel(article.getSourceLabel())
                .publishedAt(toIso(article.getPublishedAt()))
                .tags(parseJson(article.getTagsJson(), TAG_LIST_TYPE, List.of()))
                .aiSummary(article.getSummary())
                .aiOpinion(opinion)
                .bodyParagraphs(bodyParagraphs)
                .imageUrl(article.getImageUrl())
                .keyPoints(keyPoints)
                .company(company)
                .disclaimer(defaultIfBlank(stringValue(companyInfoMap.get("disclaimer")), "투자 판단은 이용자 본인에게 있습니다."))
                .build();
    }

    private List<String> splitBody(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        return List.of(content.split("\\r?\\n\\r?\\n|\\r?\\n"))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private List<String> splitKeyPoints(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(s -> !s.isBlank()).toList();
        }
        if (raw instanceof String text && !text.isBlank()) {
            return List.of(text.split("\\r?\\n|,"))
                    .stream()
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }
        return List.of();
    }

    private static String normalizeStockCode(String stockCode) {
        if (stockCode == null) {
            return null;
        }
        String trimmed = stockCode.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        return trimmed.length() >= 6 ? trimmed : String.format("%06d", Integer.parseInt(trimmed));
    }

    private String toIso(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private java.math.BigDecimal decimalValue(Object value) {
        if (value == null) {
            return java.math.BigDecimal.ZERO;
        }
        if (value instanceof java.math.BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return java.math.BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new java.math.BigDecimal(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return java.math.BigDecimal.ZERO;
        }
    }

    private String defaultIfBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private <T> T parseJson(String json, TypeReference<T> typeReference, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
