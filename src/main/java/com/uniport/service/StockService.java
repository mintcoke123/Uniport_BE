package com.uniport.service;

import com.uniport.dto.FinancialDataItemDTO;
import com.uniport.dto.InvestorSentimentDTO;
import com.uniport.dto.MarketDataDTO;
import com.uniport.dto.MyHoldingDTO;
import com.uniport.dto.NewsItemDTO;
import com.uniport.dto.StockDetailDTO;
import com.uniport.dto.StockPriceDTO;
import com.uniport.entity.Holding;
import com.uniport.entity.ManagedNewsArticle;
import com.uniport.entity.TeamHolding;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.HoldingRepository;
import com.uniport.repository.StockMasterRepository;
import com.uniport.repository.TeamHoldingRepository;
import com.uniport.service.kisws.KisWsSubscriptionManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
public class StockService {

    private static final String DEFAULT_LOGO_COLOR = "#4A90D9";

    private final KisApiService kisApiService;
    private final HoldingRepository holdingRepository;
    private final TeamHoldingRepository teamHoldingRepository;
    private final KisWsSubscriptionManager kisWsSubscriptionManager;
    private final StockMasterRepository stockMasterRepository;
    private final ManagedStockNewsService managedStockNewsService;
    private final CommunityService communityService;

    public StockService(KisApiService kisApiService,
                        HoldingRepository holdingRepository,
                        TeamHoldingRepository teamHoldingRepository,
                        @Lazy KisWsSubscriptionManager kisWsSubscriptionManager,
                        StockMasterRepository stockMasterRepository,
                        ManagedStockNewsService managedStockNewsService,
                        CommunityService communityService) {
        this.kisApiService = kisApiService;
        this.holdingRepository = holdingRepository;
        this.teamHoldingRepository = teamHoldingRepository;
        this.kisWsSubscriptionManager = kisWsSubscriptionManager;
        this.stockMasterRepository = stockMasterRepository;
        this.managedStockNewsService = managedStockNewsService;
        this.communityService = communityService;
    }

    private static Long parseTeamId(User user) {
        String tid = user != null ? user.getTeamId() : null;
        if (tid == null || tid.isBlank() || !tid.startsWith("team-")) {
            return null;
        }
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

    public StockDetailDTO getStockDetail(Long id, User user) {
        String code = id != null ? String.format("%06d", id) : "000000";
        try {
            kisWsSubscriptionManager.ensureSubscribed(code);
        } catch (Exception ignored) {
            // Realtime subscription failure should not block the REST response.
        }

        StockPriceDTO price = getStockPrice(code);
        String displayName = resolveDisplayName(code, price.getStockName());
        MyHoldingDTO myHolding = resolveMyHolding(user, code, price);

        BigDecimal currentPrice = price.getCurrentPrice() != null ? price.getCurrentPrice() : BigDecimal.ZERO;
        Long volume = price.getVolume() != null ? price.getVolume() : 0L;

        MarketDataDTO marketData = MarketDataDTO.builder()
                .openPrice(currentPrice)
                .closePrice(currentPrice)
                .volume(volume)
                .lowPrice(currentPrice)
                .highPrice(currentPrice)
                .build();

        List<ManagedNewsArticle> relatedArticles = managedStockNewsService.getNewsForStock(code, displayName, 3);
        List<FinancialDataItemDTO> financialData = relatedArticles.stream()
                .findFirst()
                .map(managedStockNewsService::extractFinancialData)
                .orElse(List.of());
        List<NewsItemDTO> news = relatedArticles.stream()
                .map(article -> NewsItemDTO.builder()
                        .id(article.getId())
                        .title(article.getTitle())
                        .source(article.getSourceLabel())
                        .date(article.getPublishedAt() != null ? article.getPublishedAt().toString() : null)
                        .summary(article.getSummary())
                        .build())
                .toList();

        String companyInfo = relatedArticles.stream()
                .findFirst()
                .map(managedStockNewsService::extractCompanyDescription)
                .filter(text -> text != null && !text.isBlank())
                .orElseGet(() -> stockMasterRepository.findById(code)
                        .map(master -> {
                            String market = master.getMarket() != null ? master.getMarket().trim() : "";
                            String name = master.getNameKr() != null ? master.getNameKr().trim() : displayName;
                            return market.isBlank() ? name : name + " (" + market + ")";
                        })
                        .orElse(displayName));

        InvestorSentimentDTO investorSentiment = communityService.getInvestorSentiment(code);
        int discussionCount = communityService.getDiscussionCount(code);

        return StockDetailDTO.builder()
                .id(id != null ? id : 0L)
                .name(displayName)
                .code(code)
                .currentPrice(currentPrice)
                .change(price.getChangeAmount() != null ? price.getChangeAmount() : BigDecimal.ZERO)
                .changeRate(price.getChangeRate() != null ? price.getChangeRate() : BigDecimal.ZERO)
                .logoColor(DEFAULT_LOGO_COLOR)
                .myHolding(myHolding)
                .marketData(marketData)
                .financialData(financialData)
                .companyInfo(companyInfo)
                .news(news)
                .investorSentiment(investorSentiment)
                .discussionCount(discussionCount)
                .build();
    }

    private MyHoldingDTO resolveMyHolding(User user, String code, StockPriceDTO price) {
        if (user == null) {
            return null;
        }
        BigDecimal currentPrice = price.getCurrentPrice() != null ? price.getCurrentPrice() : BigDecimal.ZERO;
        Long teamId = parseTeamId(user);
        if (teamId != null) {
            Optional<TeamHolding> teamOpt = teamHoldingRepository.findByTeamIdAndStockCode(teamId, code);
            if (teamOpt.isPresent()) {
                return toMyHolding(teamOpt.get().getQuantity(), teamOpt.get().getAveragePurchasePrice(), currentPrice);
            }
        }
        return holdingRepository.findByUser_IdAndStockCode(user.getId(), code)
                .map(holding -> toMyHolding(holding.getQuantity(), holding.getAveragePurchasePrice(), currentPrice))
                .orElse(null);
    }

    private MyHoldingDTO toMyHolding(int quantity, BigDecimal averagePrice, BigDecimal currentPrice) {
        BigDecimal safeAveragePrice = averagePrice != null ? averagePrice : BigDecimal.ZERO;
        BigDecimal totalValue = currentPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal totalProfit = totalValue.subtract(safeAveragePrice.multiply(BigDecimal.valueOf(quantity)));
        BigDecimal totalCost = safeAveragePrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal profitRate = totalCost.compareTo(BigDecimal.ZERO) != 0
                ? totalProfit.multiply(BigDecimal.valueOf(100)).divide(totalCost, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return MyHoldingDTO.builder()
                .quantity(quantity)
                .avgPrice(safeAveragePrice)
                .totalValue(totalValue)
                .totalProfit(totalProfit)
                .profitRate(profitRate)
                .build();
    }

    private String resolveDisplayName(String code, String stockNameFromPrice) {
        String displayName = stockNameFromPrice != null ? stockNameFromPrice.trim() : "";
        if (displayName.isEmpty() || displayName.equals(code) || displayName.matches("\\d{6}") || displayName.equals("종목_" + code)) {
            return stockMasterRepository.findById(code)
                    .map(master -> master.getNameKr() != null ? master.getNameKr().trim() : "")
                    .filter(name -> !name.isEmpty())
                    .orElse("종목_" + code);
        }
        return displayName;
    }
}
