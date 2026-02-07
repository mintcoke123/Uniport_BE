package com.uniport.service;

import com.uniport.dto.CompetitionDataDTO;
import com.uniport.dto.InvestmentDataDTO;
import com.uniport.dto.MyInvestmentResponseDTO;
import com.uniport.dto.StockHoldingItemDTO;
import com.uniport.dto.StockPriceDTO;
import com.uniport.entity.Holding;
import com.uniport.entity.User;
import com.uniport.repository.HoldingRepository;
import com.uniport.service.CompetitionService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 명세 §2-1: 내 투자 요약 (자산 + 보유 종목 + 대회 요약).
 */
@Service
public class MeService {

    private static final String DEFAULT_LOGO_COLOR = "#4A90D9";

    private final HoldingRepository holdingRepository;
    private final StockService stockService;
    private final MatchingRoomService matchingRoomService;
    private final CompetitionService competitionService;

    public MeService(HoldingRepository holdingRepository, StockService stockService, MatchingRoomService matchingRoomService, CompetitionService competitionService) {
        this.holdingRepository = holdingRepository;
        this.stockService = stockService;
        this.matchingRoomService = matchingRoomService;
        this.competitionService = competitionService;
    }

    /** user가 null이면 빈/0 데이터 반환 (미로그인 시 홈에서 401 대신 사용). */
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
        BigDecimal totalAssets = user.getTotalAssets() != null ? user.getTotalAssets() : BigDecimal.ZERO;
        BigDecimal investmentPrincipal = user.getInvestmentAmount() != null ? user.getInvestmentAmount() : totalAssets;
        BigDecimal profitLoss = user.getProfitLoss() != null ? user.getProfitLoss() : BigDecimal.ZERO;
        BigDecimal profitLossRate = user.getProfitLossRate() != null ? user.getProfitLossRate() : BigDecimal.ZERO;
        BigDecimal cashBalance = totalAssets; // stub: 전부 현금으로 간주 가능, 실제로는 totalAssets - 주식 평가액

        InvestmentDataDTO investmentData = InvestmentDataDTO.builder()
                .totalAssets(totalAssets)
                .profitLoss(profitLoss)
                .profitLossPercentage(profitLossRate)
                .investmentPrincipal(investmentPrincipal)
                .cashBalance(cashBalance)
                .build();

        List<Holding> holdings = holdingRepository.findByUser_Id(user.getId());
        List<StockHoldingItemDTO> stockHoldings = holdings.stream()
                .map(h -> toStockHoldingItem(h))
                .collect(Collectors.toList());

        CompetitionDataDTO competitionData = null;
        var ongoing = competitionService.findOngoing();
        if (ongoing.isPresent()) {
            var c = ongoing.get();
            competitionData = CompetitionDataDTO.builder()
                    .name(c.getName())
                    .endDate(c.getEndDate())
                    .daysRemaining(Math.max(0, competitionService.daysRemaining(c.getEndDate())))
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

    private StockHoldingItemDTO toStockHoldingItem(Holding h) {
        BigDecimal currentPrice = BigDecimal.ZERO;
        String stockName = "종목_" + h.getStockCode();
        try {
            StockPriceDTO price = stockService.getStockPrice(h.getStockCode());
            currentPrice = price.getCurrentPrice() != null ? price.getCurrentPrice() : BigDecimal.ZERO;
            if (price.getStockName() != null && !price.getStockName().isBlank()) stockName = price.getStockName();
        } catch (Exception ignored) {
        }
        BigDecimal avg = h.getAveragePurchasePrice() != null ? h.getAveragePurchasePrice() : BigDecimal.ZERO;
        BigDecimal currentValue = currentPrice.multiply(BigDecimal.valueOf(h.getQuantity()));
        BigDecimal profitLoss = currentValue.subtract(avg.multiply(BigDecimal.valueOf(h.getQuantity())));
        BigDecimal profitLossPct = avg.compareTo(BigDecimal.ZERO) != 0
                ? profitLoss.multiply(BigDecimal.valueOf(100)).divide(avg.multiply(BigDecimal.valueOf(h.getQuantity())), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return StockHoldingItemDTO.builder()
                .id(h.getId())
                .name(stockName)
                .quantity(h.getQuantity())
                .currentValue(currentValue)
                .profitLoss(profitLoss)
                .profitLossPercentage(profitLossPct)
                .logoColor(DEFAULT_LOGO_COLOR)
                .build();
    }
}
