package com.uniport.service;

import com.uniport.dto.StockSearchItemDTO;
import com.uniport.dto.StockSearchResponseDTO;
import com.uniport.entity.StockMaster;
import com.uniport.exception.ApiException;
import com.uniport.repository.StockMasterRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class StockMasterSearchService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 20;

    private final StockMasterRepository stockMasterRepository;

    public StockMasterSearchService(StockMasterRepository stockMasterRepository) {
        this.stockMasterRepository = stockMasterRepository;
    }

    public StockSearchResponseDTO search(String keywordParam,
                                         Integer pageParam,
                                         Integer sizeParam,
                                         String legacyQuery,
                                         String legacyLimit) {
        String keyword = resolveKeyword(keywordParam, legacyQuery);
        int page = resolvePage(pageParam);
        int size = resolveSize(sizeParam, legacyLimit);

        if (keyword.isBlank()) {
            return StockSearchResponseDTO.builder()
                    .items(List.of())
                    .page(page)
                    .size(size)
                    .hasNext(Boolean.FALSE)
                    .build();
        }

        Pageable pageable = PageRequest.of(page, size + 1);
        List<StockMaster> list = stockMasterRepository.findByNameKrIlikeOrderByNameKrAsc(keyword, pageable);
        boolean hasNext = list.size() > size;

        List<StockSearchItemDTO> items = list.stream()
                .limit(size)
                .map(this::toSearchItem)
                .filter(item -> item != null)
                .toList();

        return StockSearchResponseDTO.builder()
                .items(items)
                .page(page)
                .size(size)
                .hasNext(hasNext)
                .build();
    }

    private String resolveKeyword(String keywordParam, String legacyQuery) {
        if (keywordParam != null && !keywordParam.isBlank()) {
            return keywordParam.trim();
        }
        return legacyQuery != null ? legacyQuery.trim() : "";
    }

    private int resolvePage(Integer pageParam) {
        if (pageParam == null || pageParam < 0) {
            return 0;
        }
        return pageParam;
    }

    private int resolveSize(Integer sizeParam, String legacyLimit) {
        if (sizeParam != null) {
            if (sizeParam < 1) {
                return DEFAULT_SIZE;
            }
            return Math.min(sizeParam, MAX_SIZE);
        }
        if (legacyLimit == null || legacyLimit.isBlank()) {
            return DEFAULT_SIZE;
        }
        try {
            int parsed = Integer.parseInt(legacyLimit.trim());
            if (parsed < 1) {
                return DEFAULT_SIZE;
            }
            return Math.min(parsed, MAX_SIZE);
        } catch (NumberFormatException ex) {
            throw new ApiException("Invalid size", HttpStatus.BAD_REQUEST);
        }
    }

    private StockSearchItemDTO toSearchItem(StockMaster stockMaster) {
        String code = stockMaster.getCode();
        if (code == null || code.isBlank()) {
            return null;
        }

        String market = stockMaster.getMarket() != null ? stockMaster.getMarket() : "";
        return StockSearchItemDTO.builder()
                .stockId(buildStockId(market, code))
                .name(stockMaster.getNameKr() != null ? stockMaster.getNameKr() : "")
                .symbol(code)
                .market(market)
                .logoUrl(null)
                .build();
    }

    private String buildStockId(String market, String code) {
        String normalizedMarket = market.toUpperCase(Locale.ROOT);
        if (normalizedMarket.contains("KOS")) {
            return "KRX_" + code;
        }
        return "US_" + code;
    }
}
