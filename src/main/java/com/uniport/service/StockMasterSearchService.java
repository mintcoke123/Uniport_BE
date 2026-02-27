package com.uniport.service;

import com.uniport.dto.StockSearchItemDTO;
import com.uniport.entity.StockMaster;
import com.uniport.exception.ApiException;
import com.uniport.repository.StockMasterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * stock_master 기반 종목 검색 (읽기 전용). GET /api/stocks/search 전용.
 */
@Service
public class StockMasterSearchService {

    private static final Logger log = LoggerFactory.getLogger(StockMasterSearchService.class);
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final StockMasterRepository stockMasterRepository;

    public StockMasterSearchService(StockMasterRepository stockMasterRepository) {
        this.stockMasterRepository = stockMasterRepository;
    }

    /**
     * name_kr ILIKE 검색. query trim 후 길이 1 미만이면 빈 리스트.
     * limit 기본 20, 최대 50 clamp. limit 파싱 실패 시 ApiException(400).
     */
    public List<StockSearchItemDTO> search(String query, String limitParam) {
        String q = query != null ? query.trim() : "";
        if (q.length() < 1) {
            return List.of();
        }

        int limit = parseLimit(limitParam);

        Pageable pageable = PageRequest.of(0, limit);
        List<StockMaster> list = stockMasterRepository.findByNameKrIlikeOrderByNameKrAsc(q, pageable);

        List<StockSearchItemDTO> result = new ArrayList<>();
        for (StockMaster m : list) {
            StockSearchItemDTO dto = toSearchItem(m);
            if (dto != null) {
                result.add(dto);
            }
        }
        return result;
    }

    private static int parseLimit(String limitParam) {
        if (limitParam == null || limitParam.isBlank()) {
            return DEFAULT_LIMIT;
        }
        try {
            int value = Integer.parseInt(limitParam.trim());
            if (value < 1) return DEFAULT_LIMIT;
            return Math.min(value, MAX_LIMIT);
        } catch (NumberFormatException e) {
            throw new ApiException("Invalid limit", HttpStatus.BAD_REQUEST);
        }
    }

    private static StockSearchItemDTO toSearchItem(StockMaster m) {
        String code = m.getCode();
        if (code == null || code.isBlank()) return null;
        long id;
        try {
            id = Long.parseLong(code);
        } catch (NumberFormatException e) {
            log.warn("StockMaster code is not numeric, skipping: code={}", code);
            return null;
        }
        return StockSearchItemDTO.builder()
                .id(id)
                .code(code)
                .name(m.getNameKr() != null ? m.getNameKr() : "")
                .market(m.getMarket() != null ? m.getMarket() : "")
                .build();
    }
}
