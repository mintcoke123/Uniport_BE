package com.uniport.service;

import com.uniport.entity.AssetMaster;
import com.uniport.repository.AssetMasterRepository;
import com.uniport.service.backtest.BacktestPricePoint;
import com.uniport.service.backtest.HistoricalPriceProvider;
import com.uniport.service.importer.ImportResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetBacktestVerificationServiceTest {

    @Test
    void verifyActiveAssets_marksVerifiedUnavailableAndProxyAssets() {
        AssetMaster apple = asset("US_AAPL", "STOCK", "Apple Inc.", "AAPL", "NASDAQ", "USD");
        AssetMaster fake = asset("US_FAKE", "STOCK", "Fake Corp.", "FAKE", "NASDAQ", "USD");
        AssetMaster cash = asset("CASH_KRW", "CASH", "원화 현금", "KRW", "CASH", "KRW");
        AssetMasterRepository repository = mock(AssetMasterRepository.class);
        HistoricalPriceProvider priceProvider = mock(HistoricalPriceProvider.class);
        AssetBacktestVerificationService service = new AssetBacktestVerificationService(repository, priceProvider);
        when(repository.findActiveForBacktestVerification(any(Pageable.class))).thenReturn(List.of(apple, fake, cash));
        when(priceProvider.getSecurityPriceSeriesForEligibility(eq("US_AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(invocation -> List.of(
                        point(invocation.getArgument(2, LocalDate.class).minusYears(1).toString(), "100"),
                        point(invocation.getArgument(2, LocalDate.class).toString(), "101")
                ));
        when(priceProvider.getSecurityPriceSeriesForEligibility(eq("US_FAKE"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
        ArgumentCaptor<List<AssetMaster>> captor = ArgumentCaptor.forClass(List.class);

        ImportResult result = service.verifyActiveAssets(10);

        assertEquals(0, result.getInserted());
        assertEquals(2, result.getUpdated());
        assertEquals(1, result.getSkipped());
        verify(repository).saveAll(captor.capture());
        assertEquals(true, apple.getBacktestEnabled());
        assertEquals("VERIFIED", apple.getPriceSourceStatus());
        assertEquals(null, apple.getLastPriceError());
        assertEquals(false, fake.getBacktestEnabled());
        assertEquals("PRICE_UNAVAILABLE", fake.getPriceSourceStatus());
        assertEquals("Insufficient one-year price coverage from provider", fake.getLastPriceError());
        assertEquals(true, cash.getBacktestEnabled());
        assertEquals("PROXY", cash.getPriceSourceStatus());
        assertEquals(null, cash.getLastPriceError());
        assertEquals(3, captor.getValue().size());
        verify(priceProvider, never()).getSecurityPriceSeriesForEligibility(eq("CASH_KRW"), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void verifyActiveAssets_marksStockUnavailableWhenProviderReturnsOnlyShortRecentSeries() {
        AssetMaster stock = asset("US_SHORT", "STOCK", "Short History Inc.", "SHORT", "NASDAQ", "USD");
        AssetMasterRepository repository = mock(AssetMasterRepository.class);
        HistoricalPriceProvider priceProvider = mock(HistoricalPriceProvider.class);
        AssetBacktestVerificationService service = new AssetBacktestVerificationService(repository, priceProvider);
        when(repository.findActiveForBacktestVerification(any(Pageable.class))).thenReturn(List.of(stock));
        when(priceProvider.getSecurityPriceSeriesForEligibility(eq("US_SHORT"), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(invocation -> List.of(
                        point(invocation.getArgument(2, LocalDate.class).minusMonths(3).toString(), "100"),
                        point(invocation.getArgument(2, LocalDate.class).toString(), "101")
                ));

        ImportResult result = service.verifyActiveAssets(10);

        assertEquals(0, result.getUpdated());
        assertEquals(1, result.getSkipped());
        assertEquals(false, stock.getBacktestEnabled());
        assertEquals("PRICE_UNAVAILABLE", stock.getPriceSourceStatus());
        assertEquals("Insufficient one-year price coverage from provider", stock.getLastPriceError());
    }

    private AssetMaster asset(String assetId, String assetType, String name, String symbol, String market, String currency) {
        return AssetMaster.builder()
                .assetId(assetId)
                .assetType(assetType)
                .name(name)
                .symbol(symbol)
                .market(market)
                .currency(currency)
                .active(true)
                .build();
    }

    private BacktestPricePoint point(String date, String adjustedCloseKrw) {
        return new BacktestPricePoint(LocalDate.parse(date), new BigDecimal(adjustedCloseKrw));
    }
}
