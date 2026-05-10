package com.uniport.service;

import com.uniport.dto.AuthUserDTO;
import com.uniport.dto.CompetitionDataDTO;
import com.uniport.dto.InvestmentDataDTO;
import com.uniport.dto.MyInvestmentResponseDTO;
import com.uniport.dto.StockHoldingItemDTO;
import com.uniport.dto.StockPriceDTO;
import com.uniport.entity.Holding;
import com.uniport.entity.User;
import com.uniport.repository.HoldingRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class MeService {

    private static final String DEFAULT_LOGO_COLOR = "#4A90D9";

    private final HoldingRepository holdingRepository;
    private final StockService stockService;
    private final MatchingRoomService matchingRoomService;
    private final CompetitionService competitionService;
    private final StockVisualAssetResolver stockVisualAssetResolver;

    public MeService(HoldingRepository holdingRepository,
                     StockService stockService,
                     MatchingRoomService matchingRoomService,
                     CompetitionService competitionService,
                     StockVisualAssetResolver stockVisualAssetResolver) {
        this.holdingRepository = holdingRepository;
        this.stockService = stockService;
        this.matchingRoomService = matchingRoomService;
        this.competitionService = competitionService;
        this.stockVisualAssetResolver = stockVisualAssetResolver;
    }

    public AuthUserDTO getProfile(User user) {
        if (user == null) {
            return new AuthUserDTO();
        }
        return AuthUserDTO.builder()
                .id(user.getId() != null ? String.valueOf(user.getId()) : null)
                .studentId(user.getStudentId())
                .nickname(user.getNickname())
                .totalAssets(zeroIfNull(user.getTotalAssets()))
                .investmentAmount(zeroIfNull(user.getInvestmentAmount()))
                .profitLoss(zeroIfNull(user.getProfitLoss()))
                .profitLossRate(zeroIfNull(user.getProfitLossRate()))
                .teamId(user.getTeamId())
                .role(user.getRole() != null ? user.getRole() : "user")
                .build();
    }

    public MyInvestmentResponseDTO getMyInvestment(User user) {
        if (user == null) {
            return MyInvestmentResponseDTO.builder()
                    .investmentData(InvestmentDataDTO.builder()
                            .totalAssets(BigDecimal.ZERO)
                            .profitLoss(BigDecimal.ZERO)
                            .profitLossPercentage(BigDecimal.ZERO)
                            .investmentPrincipal(BigDecimal.ZERO)
                            .cashBalance(BigDecimal.ZERO)
                            .build())
                    .stockHoldings(List.of())
                    .competitionData(null)
                    .mockTradingStarted(false)
                    .build();
        }

        List<Holding> holdings = holdingRepository.findByUser_Id(user.getId());
        List<StockHoldingItemDTO> stockHoldings = new ArrayList<>();
        BigDecimal holdingsValue = BigDecimal.ZERO;
        for (Holding holding : holdings) {
            StockHoldingItemDTO item = toStockHoldingItem(holding);
            stockHoldings.add(item);
            holdingsValue = holdingsValue.add(zeroIfNull(item.getCurrentValue()));
        }

        BigDecimal totalAssets = deriveTotalAssets(user, holdingsValue);
        BigDecimal investmentPrincipal = deriveInvestmentPrincipal(user, totalAssets);
        BigDecimal profitLoss = deriveProfitLoss(user, totalAssets, investmentPrincipal);
        BigDecimal profitLossRate = deriveProfitLossRate(user, profitLoss, investmentPrincipal);
        BigDecimal cashBalance = totalAssets.subtract(holdingsValue).max(BigDecimal.ZERO);

        InvestmentDataDTO investmentData = InvestmentDataDTO.builder()
                .totalAssets(totalAssets)
                .profitLoss(profitLoss)
                .profitLossPercentage(profitLossRate)
                .investmentPrincipal(investmentPrincipal)
                .cashBalance(cashBalance)
                .build();

        CompetitionDataDTO competitionData = null;
        var ongoing = competitionService.findOngoing();
        if (ongoing.isPresent()) {
            var competition = ongoing.get();
            competitionData = CompetitionDataDTO.builder()
                    .name(competition.getName())
                    .endDate(competition.getEndDate())
                    .daysRemaining(Math.max(0, competitionService.daysRemaining(competition.getEndDate())))
                    .build();
        }

        boolean mockTradingStarted = matchingRoomService.hasUserStartedMockTrading(user);
        return MyInvestmentResponseDTO.builder()
                .investmentData(investmentData)
                .stockHoldings(stockHoldings)
                .competitionData(competitionData)
                .mockTradingStarted(mockTradingStarted)
                .build();
    }

    private StockHoldingItemDTO toStockHoldingItem(Holding holding) {
        BigDecimal currentPrice = BigDecimal.ZERO;
        String stockName = "Stock_" + holding.getStockCode();
        String market = "KRX";
        String logoUrl = null;
        try {
            StockPriceDTO price = stockService.getStockPrice(holding.getStockCode());
            currentPrice = price.getCurrentPrice() != null ? price.getCurrentPrice() : BigDecimal.ZERO;
            if (price.getStockName() != null && !price.getStockName().isBlank()) {
                stockName = price.getStockName();
            }
            if (price.getMarket() != null && !price.getMarket().isBlank()) {
                market = price.getMarket();
            }
        } catch (Exception ignored) {
        }

        BigDecimal averagePrice = holding.getAveragePurchasePrice() != null
                ? holding.getAveragePurchasePrice()
                : BigDecimal.ZERO;
        BigDecimal quantity = BigDecimal.valueOf(holding.getQuantity());
        BigDecimal currentValue = currentPrice.multiply(quantity);
        BigDecimal investedValue = averagePrice.multiply(quantity);
        BigDecimal profitLoss = currentValue.subtract(investedValue);
        BigDecimal profitLossPercentage = investedValue.compareTo(BigDecimal.ZERO) != 0
                ? profitLoss.multiply(BigDecimal.valueOf(100)).divide(investedValue, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return StockHoldingItemDTO.builder()
                .id(holding.getId())
                .stockCode(holding.getStockCode())
                .name(stockName)
                .market(market)
                .logoUrl(logoUrl)
                .visual(stockVisualAssetResolver.resolve(market, holding.getStockCode(), stockName, logoUrl))
                .quantity(holding.getQuantity())
                .currentValue(currentValue)
                .profitLoss(profitLoss)
                .profitLossPercentage(profitLossPercentage)
                .logoColor(DEFAULT_LOGO_COLOR)
                .build();
    }

    private BigDecimal deriveTotalAssets(User user, BigDecimal holdingsValue) {
        if (user.getTotalAssets() != null) {
            return user.getTotalAssets();
        }
        if (user.getInvestmentAmount() != null && user.getProfitLoss() != null) {
            return user.getInvestmentAmount().add(user.getProfitLoss());
        }
        return holdingsValue;
    }

    private BigDecimal deriveInvestmentPrincipal(User user, BigDecimal totalAssets) {
        if (user.getInvestmentAmount() != null) {
            return user.getInvestmentAmount();
        }
        if (user.getProfitLoss() != null) {
            return totalAssets.subtract(user.getProfitLoss()).max(BigDecimal.ZERO);
        }
        return totalAssets;
    }

    private BigDecimal deriveProfitLoss(User user, BigDecimal totalAssets, BigDecimal investmentPrincipal) {
        if (user.getProfitLoss() != null) {
            return user.getProfitLoss();
        }
        return totalAssets.subtract(investmentPrincipal);
    }

    private BigDecimal deriveProfitLossRate(User user, BigDecimal profitLoss, BigDecimal investmentPrincipal) {
        if (user.getProfitLossRate() != null) {
            return user.getProfitLossRate();
        }
        if (investmentPrincipal.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return profitLoss.multiply(BigDecimal.valueOf(100))
                .divide(investmentPrincipal, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
