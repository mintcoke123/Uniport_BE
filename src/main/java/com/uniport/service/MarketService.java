package com.uniport.service;

import com.uniport.dto.IndexChartPriceItemDTO;
import com.uniport.dto.MarketIndexDTO;
import com.uniport.dto.MarketIndexItemDTO;
import com.uniport.dto.MarketStockItemDTO;
import com.uniport.dto.StockPriceDTO;
import com.uniport.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MarketService {

    private static final String DEFAULT_LOGO_COLOR = "#4A90D9";

    private final KisApiService kisApiService;

    public MarketService(KisApiService kisApiService) {
        this.kisApiService = kisApiService;
    }

    public List<StockPriceDTO> getVolumeRank() {
        try {
            return kisApiService.getVolumeRank();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Failed to fetch volume rank: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /** 상승률순 조회. KIS fluctuation ranking API. */
    public List<StockPriceDTO> getFluctuationRank() {
        try {
            return kisApiService.getFluctuationRank();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Failed to fetch fluctuation rank: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /** 하락율순 조회. KIS fluctuation ranking API (fid_rank_sort_cls_code=1, fid_prc_cls_code=1). */
    public List<StockPriceDTO> getFallingRank() {
        try {
            return kisApiService.getFallingRank();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Failed to fetch falling rank: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /** 일/주/월/년 지수 차트 시세. period: D=일봉, W=주봉, M=월봉, Y=년봉. 날짜: yyyyMMdd. */
    public List<IndexChartPriceItemDTO> getIndexChartPrice(String indexCode, String startDate, String endDate, String period) {
        try {
            return kisApiService.getIndexChartPrice(indexCode, startDate, endDate, period);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Failed to fetch index chart: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public MarketIndexDTO getMarketIndex(String indexCode) {
        if (indexCode == null || indexCode.isBlank()) {
            throw new ApiException("Index code is required", HttpStatus.BAD_REQUEST);
        }
        try {
            return kisApiService.getMarketIndex(indexCode.trim());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Failed to fetch market index: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /** 명세 §3-1: 시장 지수 배열 (id, name, value, change, changeRate) */
    public List<MarketIndexItemDTO> getIndicesForApi() {
        List<MarketIndexItemDTO> list = new ArrayList<>();
        try {
            MarketIndexDTO kospi = kisApiService.getMarketIndex("KOSPI");
            list.add(MarketIndexItemDTO.builder()
                    .id(1L)
                    .name(kospi.getIndexName() != null ? kospi.getIndexName() : "KOSPI")
                    .value(kospi.getValue() != null ? kospi.getValue() : BigDecimal.ZERO)
                    .change(kospi.getChangeAmount() != null ? kospi.getChangeAmount() : BigDecimal.ZERO)
                    .changeRate(kospi.getChangeRate() != null ? kospi.getChangeRate() : BigDecimal.ZERO)
                    .build());
            MarketIndexDTO kosdaq = kisApiService.getMarketIndex("KOSDAQ");
            list.add(MarketIndexItemDTO.builder()
                    .id(2L)
                    .name(kosdaq.getIndexName() != null ? kosdaq.getIndexName() : "KOSDAQ")
                    .value(kosdaq.getValue() != null ? kosdaq.getValue() : BigDecimal.ZERO)
                    .change(kosdaq.getChangeAmount() != null ? kosdaq.getChangeAmount() : BigDecimal.ZERO)
                    .changeRate(kosdaq.getChangeRate() != null ? kosdaq.getChangeRate() : BigDecimal.ZERO)
                    .build());
        } catch (Exception e) {
            list.add(MarketIndexItemDTO.builder().id(1L).name("KOSPI").value(BigDecimal.valueOf(2500)).change(BigDecimal.ZERO).changeRate(BigDecimal.ZERO).build());
            list.add(MarketIndexItemDTO.builder().id(2L).name("KOSDAQ").value(BigDecimal.valueOf(800)).change(BigDecimal.ZERO).changeRate(BigDecimal.ZERO).build());
        }
        return list;
    }

    /** 명세 §3-2~3-4: sort=volume|rising|falling. volume 실패 시 빈 배열 반환(프론트 전달 보장). */
    public List<MarketStockItemDTO> getStocksForApi(String sort) {
        List<StockPriceDTO> list;
        if ("rising".equalsIgnoreCase(sort)) {
            list = getFluctuationRank();
        } else if ("falling".equalsIgnoreCase(sort)) {
            list = getFallingRank();
        } else {
            try {
                list = getVolumeRank();
            } catch (Exception e) {
                list = List.of();
            }
        }
        if (list == null) {
            list = List.of();
        }
        return list.stream()
                .map(p -> MarketStockItemDTO.builder()
                        .id(parseStockId(p.getStockCode()))
                        .name(p.getStockName() != null ? p.getStockName() : "종목_" + p.getStockCode())
                        .code(p.getStockCode())
                        .currentPrice(p.getCurrentPrice() != null ? p.getCurrentPrice() : BigDecimal.ZERO)
                        .change(p.getChangeAmount() != null ? p.getChangeAmount() : BigDecimal.ZERO)
                        .changeRate(p.getChangeRate() != null ? p.getChangeRate() : BigDecimal.ZERO)
                        .logoColor(DEFAULT_LOGO_COLOR)
                        .build())
                .collect(Collectors.toList());
    }

    private static Long parseStockId(String code) {
        if (code == null || code.isBlank()) return 0L;
        try {
            return Long.parseLong(code.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
