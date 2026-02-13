package com.uniport.service;

import com.uniport.dto.*;
import com.uniport.entity.Holding;
import com.uniport.entity.TeamHolding;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.HoldingRepository;
import com.uniport.repository.TeamHoldingRepository;
import com.uniport.service.kisws.KisWsSubscriptionManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class StockService {

    private static final String DEFAULT_LOGO_COLOR = "#4A90D9";

    private final KisApiService kisApiService;
    private final HoldingRepository holdingRepository;
    private final TeamHoldingRepository teamHoldingRepository;
    private final KisWsSubscriptionManager kisWsSubscriptionManager;

    public StockService(KisApiService kisApiService, HoldingRepository holdingRepository,
                        TeamHoldingRepository teamHoldingRepository,
                        @Lazy KisWsSubscriptionManager kisWsSubscriptionManager) {
        this.kisApiService = kisApiService;
        this.holdingRepository = holdingRepository;
        this.teamHoldingRepository = teamHoldingRepository;
        this.kisWsSubscriptionManager = kisWsSubscriptionManager;
    }

    private static Long parseTeamId(User user) {
        String tid = user != null ? user.getTeamId() : null;
        if (tid == null || tid.isBlank() || !tid.startsWith("team-")) return null;
        try {
            return Long.parseLong(tid.substring(5));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public StockPriceDTO getStockPrice(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new ApiException("Stock code is required", HttpStatus.BAD_REQUEST);
        }
        try {
            return kisApiService.getStockPrice(stockCode.trim());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Failed to fetch stock price: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public List<StockPriceDTO> searchStocks(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        try {
            return kisApiService.searchStocks(keyword.trim());
        } catch (Exception e) {
            throw new ApiException("Failed to search stocks: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /** 명세 §3-5: 종목 상세. id는 숫자(예: 5930) → 코드 "005930" */
    public StockDetailDTO getStockDetail(Long id, User user) {
        String code = id != null ? String.format("%06d", id) : "000000";
        try {
            kisWsSubscriptionManager.ensureSubscribed(code);
        } catch (Exception ignored) {
            /* WS 구독은 best-effort, REST 응답에는 영향 없음 */
        }
        StockPriceDTO price = getStockPrice(code);

        Long idLong = id != null ? id : 0L;
        MyHoldingDTO myHolding = null;
        if (user != null) {
            BigDecimal currentPrice = price.getCurrentPrice() != null ? price.getCurrentPrice() : BigDecimal.ZERO;
            Long teamId = parseTeamId(user);
            if (teamId != null) {
                Optional<TeamHolding> teamOpt = teamHoldingRepository.findByTeamIdAndStockCode(teamId, code);
                if (teamOpt.isPresent()) {
                    TeamHolding h = teamOpt.get();
                    BigDecimal avg = h.getAveragePurchasePrice() != null ? h.getAveragePurchasePrice() : BigDecimal.ZERO;
                    BigDecimal totalValue = currentPrice.multiply(BigDecimal.valueOf(h.getQuantity()));
                    BigDecimal totalProfit = totalValue.subtract(avg.multiply(BigDecimal.valueOf(h.getQuantity())));
                    BigDecimal profitRate = avg.compareTo(BigDecimal.ZERO) != 0
                            ? totalProfit.multiply(BigDecimal.valueOf(100)).divide(avg.multiply(BigDecimal.valueOf(h.getQuantity())), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    myHolding = MyHoldingDTO.builder()
                            .quantity(h.getQuantity())
                            .avgPrice(avg)
                            .totalValue(totalValue)
                            .totalProfit(totalProfit)
                            .profitRate(profitRate)
                            .build();
                }
            }
            if (myHolding == null) {
                Optional<Holding> opt = holdingRepository.findByUser_IdAndStockCode(user.getId(), code);
                if (opt.isPresent()) {
                    Holding h = opt.get();
                    BigDecimal avg = h.getAveragePurchasePrice() != null ? h.getAveragePurchasePrice() : BigDecimal.ZERO;
                    BigDecimal totalValue = currentPrice.multiply(BigDecimal.valueOf(h.getQuantity()));
                    BigDecimal totalProfit = totalValue.subtract(avg.multiply(BigDecimal.valueOf(h.getQuantity())));
                    BigDecimal profitRate = avg.compareTo(BigDecimal.ZERO) != 0
                            ? totalProfit.multiply(BigDecimal.valueOf(100)).divide(avg.multiply(BigDecimal.valueOf(h.getQuantity())), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    myHolding = MyHoldingDTO.builder()
                            .quantity(h.getQuantity())
                            .avgPrice(avg)
                            .totalValue(totalValue)
                            .totalProfit(totalProfit)
                            .profitRate(profitRate)
                            .build();
                }
            }
        }

        BigDecimal cp = price.getCurrentPrice() != null ? price.getCurrentPrice() : BigDecimal.ZERO;
        Long vol = price.getVolume() != null ? price.getVolume() : 0L;
        MarketDataDTO marketData = MarketDataDTO.builder()
                .openPrice(cp)
                .closePrice(cp)
                .volume(vol)
                .lowPrice(cp)
                .highPrice(cp)
                .build();

        List<FinancialDataItemDTO> financialData = new ArrayList<>();
        financialData.add(FinancialDataItemDTO.builder().quarter("2024 Q3").revenue(BigDecimal.valueOf(1000000)).grossProfit(BigDecimal.valueOf(200000)).operatingProfit(BigDecimal.valueOf(100000)).build());

        List<NewsItemDTO> news = new ArrayList<>();
        news.add(NewsItemDTO.builder().id(1L).title("종목 소식").source("뉴스").date("2025-02-01").summary("요약").build());

        String displayName = price.getStockName() != null ? price.getStockName().trim() : "";
        if (displayName.isEmpty() || displayName.equals(code) || displayName.matches("\\d{6}")) {
            displayName = "종목_" + code;
        }
        return StockDetailDTO.builder()
                .id(idLong)
                .name(displayName)
                .code(code)
                .currentPrice(cp)
                .change(price.getChangeAmount() != null ? price.getChangeAmount() : BigDecimal.ZERO)
                .changeRate(price.getChangeRate() != null ? price.getChangeRate() : BigDecimal.ZERO)
                .logoColor(DEFAULT_LOGO_COLOR)
                .myHolding(myHolding)
                .marketData(marketData)
                .financialData(financialData)
                .companyInfo("(주) 샘플 회사 소개")
                .news(news)
                .build();
    }
}
