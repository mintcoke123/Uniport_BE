package com.uniport.service;

import com.uniport.service.backtest.BacktestPricePoint;
import com.uniport.service.backtest.CachedFallbackHistoricalPriceProvider;
import com.uniport.service.backtest.CompositeHistoricalPriceProvider;
import com.uniport.service.backtest.KisHistoricalPriceProvider;
import com.uniport.service.backtest.NasdaqHistoricalPriceProvider;
import com.uniport.service.backtest.YahooHistoricalPriceProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompositeHistoricalPriceProviderTest {

    private final YahooHistoricalPriceProvider yahooProvider = mock(YahooHistoricalPriceProvider.class);
    private final KisHistoricalPriceProvider kisProvider = mock(KisHistoricalPriceProvider.class);
    private final NasdaqHistoricalPriceProvider nasdaqProvider = mock(NasdaqHistoricalPriceProvider.class);
    private final CachedFallbackHistoricalPriceProvider cachedProvider = mock(CachedFallbackHistoricalPriceProvider.class);
    private final CompositeHistoricalPriceProvider provider = new CompositeHistoricalPriceProvider(
            yahooProvider,
            kisProvider,
            nasdaqProvider,
            cachedProvider
    );

    @Test
    void getSecurityPriceSeriesFallsBackToKisWhenYahooReturnsNoUsablePrices() {
        LocalDate startDate = LocalDate.parse("2025-05-19");
        LocalDate endDate = LocalDate.parse("2026-05-19");
        List<BacktestPricePoint> kisSeries = priceSeries(startDate, endDate);
        when(yahooProvider.getSecurityPriceSeries("KRX_005930", startDate, endDate)).thenReturn(List.of());
        when(kisProvider.getSecurityPriceSeries("KRX_005930", startDate, endDate)).thenReturn(kisSeries);

        List<BacktestPricePoint> result = provider.getSecurityPriceSeries("KRX_005930", startDate, endDate);

        assertEquals(kisSeries, result);
        verify(cachedProvider, never()).getSecurityPriceSeries(eq("KRX_005930"), eq(startDate), eq(endDate));
    }

    @Test
    void getBenchmarkSeriesFallsBackToCachedProviderWhenYahooAndKisMiss() {
        LocalDate startDate = LocalDate.parse("2025-05-19");
        LocalDate endDate = LocalDate.parse("2026-05-19");
        List<BacktestPricePoint> cachedSeries = priceSeries(startDate, endDate);
        when(yahooProvider.getBenchmarkSeries("SP500", startDate, endDate)).thenReturn(List.of());
        when(kisProvider.getBenchmarkSeries("SP500", startDate, endDate)).thenReturn(List.of());
        when(nasdaqProvider.getBenchmarkSeries("SP500", startDate, endDate)).thenReturn(List.of());
        when(cachedProvider.getBenchmarkSeries("SP500", startDate, endDate)).thenReturn(cachedSeries);

        List<BacktestPricePoint> result = provider.getBenchmarkSeries("SP500", startDate, endDate);

        assertEquals(cachedSeries, result);
    }

    @Test
    void getBenchmarkSeriesFallsBackToNasdaqProviderBeforeCachedProviderWhenYahooAndKisMiss() {
        LocalDate startDate = LocalDate.parse("2025-05-19");
        LocalDate endDate = LocalDate.parse("2026-05-19");
        List<BacktestPricePoint> nasdaqSeries = priceSeries(startDate, endDate);
        when(yahooProvider.getBenchmarkSeries("SP500", startDate, endDate)).thenReturn(List.of());
        when(kisProvider.getBenchmarkSeries("SP500", startDate, endDate)).thenReturn(List.of());
        when(nasdaqProvider.getBenchmarkSeries("SP500", startDate, endDate)).thenReturn(nasdaqSeries);

        List<BacktestPricePoint> result = provider.getBenchmarkSeries("SP500", startDate, endDate);

        assertEquals(nasdaqSeries, result);
        verify(cachedProvider, never()).getBenchmarkSeries(eq("SP500"), eq(startDate), eq(endDate));
    }

    @Test
    void getSecurityPriceSeriesContinuesWhenIntermediateProviderThrows() {
        LocalDate startDate = LocalDate.parse("2025-05-19");
        LocalDate endDate = LocalDate.parse("2026-05-19");
        List<BacktestPricePoint> cachedSeries = priceSeries(startDate, endDate);
        when(yahooProvider.getSecurityPriceSeries("US_AAPL", startDate, endDate)).thenReturn(List.of());
        when(kisProvider.getSecurityPriceSeries("US_AAPL", startDate, endDate))
                .thenThrow(new IllegalStateException("KIS temporarily unavailable"));
        when(cachedProvider.getSecurityPriceSeries("US_AAPL", startDate, endDate)).thenReturn(cachedSeries);

        List<BacktestPricePoint> result = provider.getSecurityPriceSeries("US_AAPL", startDate, endDate);

        assertEquals(cachedSeries, result);
    }

    private List<BacktestPricePoint> priceSeries(LocalDate startDate, LocalDate endDate) {
        return List.of(
                new BacktestPricePoint(startDate, BigDecimal.valueOf(1000)),
                new BacktestPricePoint(endDate, BigDecimal.valueOf(1100))
        );
    }
}
