package com.uniport.service;

import com.uniport.dto.IndexChartPriceItemDTO;
import com.uniport.dto.MarketIndexDTO;
import com.uniport.dto.MarketIndexItemDTO;
import com.uniport.dto.MarketStockItemDTO;
import com.uniport.dto.StockPriceDTO;
import com.uniport.dto.StockVisualDTO;
import com.uniport.exception.ApiException;
import com.uniport.service.kisws.KisWsSubscriptionManager;
import org.springframework.context.annotation.Lazy;
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
    private final KisWsSubscriptionManager kisWsSubscriptionManager;
    private final StockVisualAssetResolver stockVisualAssetResolver;
    private final StockSymbolLogoUrlResolver stockSymbolLogoUrlResolver;
    private final YahooMarketIndexClient yahooMarketIndexClient;

    public MarketService(KisApiService kisApiService,
                         @Lazy KisWsSubscriptionManager kisWsSubscriptionManager,
                         StockVisualAssetResolver stockVisualAssetResolver,
                         StockSymbolLogoUrlResolver stockSymbolLogoUrlResolver,
                         YahooMarketIndexClient yahooMarketIndexClient) {
        this.kisApiService = kisApiService;
        this.kisWsSubscriptionManager = kisWsSubscriptionManager;
        this.stockVisualAssetResolver = stockVisualAssetResolver;
        this.stockSymbolLogoUrlResolver = stockSymbolLogoUrlResolver;
        this.yahooMarketIndexClient = yahooMarketIndexClient;
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

    /** 명세 §3-1: 시장 지수 배열 (id, name, value, change, changeRate). 목데이터 없음, 수신 실패 시 예외. */
    public List<MarketIndexItemDTO> getIndicesForApi() {
        List<MarketIndexItemDTO> list = new ArrayList<>();
        try {
            MarketIndexDTO kospi = kisApiService.getMarketIndex("KOSPI");
            list.add(toIndexItem(1L, kospi, "KOSPI"));
            MarketIndexDTO kosdaq = kisApiService.getMarketIndex("KOSDAQ");
            list.add(toIndexItem(2L, kosdaq, "KOSDAQ"));
            MarketIndexDTO nasdaq = yahooMarketIndexClient.getNasdaqCompositeIndex();
            list.add(toIndexItem(3L, nasdaq, "NASDAQ"));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("시장 지수(코스피/코스닥/나스닥)를 불러오지 못했습니다. " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
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
        for (StockPriceDTO p : list) {
            try {
                kisWsSubscriptionManager.ensureSubscribed(p.getStockCode());
            } catch (Exception ignored) {
                /* WS 구독은 best-effort */
            }
        }
        return list.stream()
                .map(p -> {
                    String stockName = p.getStockName() != null ? p.getStockName() : "종목_" + p.getStockCode();
                    String market = p.getMarket() != null && !p.getMarket().isBlank() ? p.getMarket() : "KRX";
                    StockVisualDTO visual = p.getVisual() != null
                            ? p.getVisual()
                            : stockVisualAssetResolver.resolve(market, p.getStockCode(), stockName, null);
                    String logoUrl = p.getLogoUrl() != null && !p.getLogoUrl().isBlank()
                            ? p.getLogoUrl()
                            : stockSymbolLogoUrlResolver.resolve(market, p.getStockCode(), visual);
                    return MarketStockItemDTO.builder()
                            .id(parseStockId(p.getStockCode()))
                            .name(stockName)
                            .code(p.getStockCode())
                            .market(market)
                            .logoUrl(logoUrl)
                            .visual(visual)
                            .currentPrice(p.getCurrentPrice() != null ? p.getCurrentPrice() : BigDecimal.ZERO)
                            .change(p.getChangeAmount() != null ? p.getChangeAmount() : BigDecimal.ZERO)
                            .changeRate(p.getChangeRate() != null ? p.getChangeRate() : BigDecimal.ZERO)
                            .logoColor(DEFAULT_LOGO_COLOR)
                            .build();
                })
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

    private static MarketIndexItemDTO toIndexItem(Long id, MarketIndexDTO index, String fallbackName) {
        return MarketIndexItemDTO.builder()
                .id(id)
                .name(index.getIndexName() != null ? index.getIndexName() : fallbackName)
                .value(index.getValue() != null ? index.getValue() : BigDecimal.ZERO)
                .change(index.getChangeAmount() != null ? index.getChangeAmount() : BigDecimal.ZERO)
                .changeRate(index.getChangeRate() != null ? index.getChangeRate() : BigDecimal.ZERO)
                .build();
    }
}
