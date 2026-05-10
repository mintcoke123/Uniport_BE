package com.uniport.service;

import com.uniport.entity.AssetPriceDaily;
import com.uniport.repository.AssetPriceDailyRepository;
import com.uniport.service.backtest.BacktestPricePoint;
import com.uniport.service.backtest.CachedFallbackHistoricalPriceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CachedFallbackHistoricalPriceProviderTest {

    @Mock
    private AssetPriceDailyRepository assetPriceDailyRepository;

    @Test
    void getSecurityPriceSeries_returnsCachedPricesWhenCacheCoversPeriod() {
        LocalDate startDate = LocalDate.parse("2025-01-01");
        LocalDate endDate = LocalDate.parse("2025-01-31");
        CachedFallbackHistoricalPriceProvider provider = new CachedFallbackHistoricalPriceProvider(
                assetPriceDailyRepository,
                true
        );
        when(assetPriceDailyRepository.findByAssetIdAndTradeDateBetweenOrderByTradeDateAsc("US_AAPL", startDate, endDate))
                .thenReturn(List.of(
                        cachedPrice("US_AAPL", "2025-01-01", "13000", "10", "USD", "EXTERNAL_TEST"),
                        cachedPrice("US_AAPL", "2025-01-31", "14300", "11", "USD", "EXTERNAL_TEST")
                ));

        List<BacktestPricePoint> result = provider.getSecurityPriceSeries("US_AAPL", startDate, endDate);

        assertEquals(2, result.size());
        assertEquals(LocalDate.parse("2025-01-01"), result.get(0).date());
        assertEquals(0, BigDecimal.valueOf(13000).compareTo(result.get(0).adjustedCloseKrw()));
        assertEquals(LocalDate.parse("2025-01-31"), result.get(1).date());
    }

    @Test
    void getSecurityPriceSeries_returnsSyntheticFallbackOnCacheMissWhenEnabled() {
        LocalDate startDate = LocalDate.parse("2025-01-01");
        LocalDate endDate = LocalDate.parse("2025-01-10");
        CachedFallbackHistoricalPriceProvider provider = new CachedFallbackHistoricalPriceProvider(
                assetPriceDailyRepository,
                true
        );
        when(assetPriceDailyRepository.findByAssetIdAndTradeDateBetweenOrderByTradeDateAsc("US_NVDA", startDate, endDate))
                .thenReturn(List.of());

        List<BacktestPricePoint> result = provider.getSecurityPriceSeries("US_NVDA", startDate, endDate);

        assertEquals(true, result.size() >= 2);
        assertEquals(startDate, result.get(0).date());
        assertEquals(true, result.get(result.size() - 1).adjustedCloseKrw()
                .compareTo(result.get(0).adjustedCloseKrw()) != 0);
    }

    @Test
    void getSecurityPriceSeries_returnsEmptyOnCacheMissWhenFallbackDisabled() {
        LocalDate startDate = LocalDate.parse("2025-01-01");
        LocalDate endDate = LocalDate.parse("2025-01-10");
        CachedFallbackHistoricalPriceProvider provider = new CachedFallbackHistoricalPriceProvider(
                assetPriceDailyRepository,
                false
        );
        when(assetPriceDailyRepository.findByAssetIdAndTradeDateBetweenOrderByTradeDateAsc("US_NVDA", startDate, endDate))
                .thenReturn(List.of());

        List<BacktestPricePoint> result = provider.getSecurityPriceSeries("US_NVDA", startDate, endDate);

        assertEquals(List.of(), result);
    }

    @Test
    void getBenchmarkSeries_usesBenchmarkCacheKeyAndFallbackWithoutExternalCalls() {
        LocalDate startDate = LocalDate.parse("2025-01-01");
        LocalDate endDate = LocalDate.parse("2025-01-10");
        CachedFallbackHistoricalPriceProvider provider = new CachedFallbackHistoricalPriceProvider(
                assetPriceDailyRepository,
                true
        );
        when(assetPriceDailyRepository.findByAssetIdAndTradeDateBetweenOrderByTradeDateAsc("BENCHMARK_SP500", startDate, endDate))
                .thenReturn(List.of());

        List<BacktestPricePoint> result = provider.getBenchmarkSeries("SP500", startDate, endDate);

        assertEquals(true, result.size() >= 2);
        assertEquals(startDate, result.get(0).date());
    }

    private AssetPriceDaily cachedPrice(String assetId,
                                        String date,
                                        String closeKrw,
                                        String closeNative,
                                        String currency,
                                        String source) {
        return AssetPriceDaily.builder()
                .assetId(assetId)
                .tradeDate(LocalDate.parse(date))
                .closeKrw(new BigDecimal(closeKrw))
                .closeNative(new BigDecimal(closeNative))
                .currency(currency)
                .source(source)
                .build();
    }
}
