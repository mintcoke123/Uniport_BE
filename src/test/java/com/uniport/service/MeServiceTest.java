package com.uniport.service;

import com.uniport.dto.AuthUserDTO;
import com.uniport.dto.MyInvestmentResponseDTO;
import com.uniport.dto.StockPriceDTO;
import com.uniport.entity.Holding;
import com.uniport.entity.User;
import com.uniport.repository.HoldingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeServiceTest {

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private StockService stockService;

    @Mock
    private MatchingRoomService matchingRoomService;

    @Mock
    private CompetitionService competitionService;

    @Mock
    private StockVisualAssetResolver stockVisualAssetResolver;

    @Mock
    private StockSymbolLogoUrlResolver stockSymbolLogoUrlResolver;

    @InjectMocks
    private MeService meService;

    @Test
    void getProfile_returnsEmptyDtoForAnonymousUser() {
        AuthUserDTO profile = meService.getProfile(null);

        assertNull(profile.getId());
        assertNull(profile.getStudentId());
        assertNull(profile.getNickname());
    }

    @Test
    void getMyInvestment_calculatesCashBalanceFromHoldingsValue() {
        User user = User.builder()
                .id(7L)
                .studentId("20240001")
                .nickname("tester")
                .totalAssets(new BigDecimal("1000000"))
                .investmentAmount(new BigDecimal("900000"))
                .profitLoss(new BigDecimal("100000"))
                .profitLossRate(new BigDecimal("11.11"))
                .build();

        Holding holding = Holding.builder()
                .id(3L)
                .user(user)
                .stockCode("005930")
                .quantity(10)
                .averagePurchasePrice(new BigDecimal("50000"))
                .build();

        when(holdingRepository.findByUser_Id(7L)).thenReturn(List.of(holding));
        when(stockService.getStockPrice("005930")).thenReturn(StockPriceDTO.builder()
                .stockCode("005930")
                .stockName("삼성전자")
                .currentPrice(new BigDecimal("60000"))
                .build());
        when(competitionService.findOngoing()).thenReturn(Optional.empty());
        when(matchingRoomService.hasUserStartedMockTrading(user)).thenReturn(true);

        MyInvestmentResponseDTO result = meService.getMyInvestment(user);

        assertEquals(new BigDecimal("1000000"), result.getInvestmentData().getTotalAssets());
        assertEquals(new BigDecimal("400000"), result.getInvestmentData().getCashBalance());
        assertEquals(1, result.getStockHoldings().size());
        assertEquals(new BigDecimal("600000"), result.getStockHoldings().get(0).getCurrentValue());
        assertEquals(Boolean.TRUE, result.getMockTradingStarted());
    }

    @Test
    void getMyInvestment_returnsZeroPayloadForAnonymousUser() {
        MyInvestmentResponseDTO result = meService.getMyInvestment(null);

        assertEquals(BigDecimal.ZERO, result.getInvestmentData().getTotalAssets());
        assertTrue(result.getStockHoldings().isEmpty());
        assertEquals(Boolean.FALSE, result.getMockTradingStarted());
    }
}
