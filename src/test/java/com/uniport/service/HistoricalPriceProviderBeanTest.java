package com.uniport.service;

import com.uniport.repository.AssetMasterRepository;
import com.uniport.repository.AssetPriceDailyRepository;
import com.uniport.service.backtest.CachedFallbackHistoricalPriceProvider;
import com.uniport.service.backtest.CompositeHistoricalPriceProvider;
import com.uniport.service.backtest.FxRateProvider;
import com.uniport.service.backtest.HistoricalPriceProvider;
import com.uniport.service.backtest.KisHistoricalPriceProvider;
import com.uniport.service.backtest.YahooHistoricalPriceProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistoricalPriceProviderBeanTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(AssetPriceDailyRepository.class, () -> mock(AssetPriceDailyRepository.class))
            .withBean(AssetMasterRepository.class, () -> mock(AssetMasterRepository.class))
            .withBean(KisApiService.class, () -> mock(KisApiService.class))
            .withBean(RestTemplate.class, () -> mock(RestTemplate.class))
            .withBean(FxRateProvider.class, () -> (currency, date) -> BigDecimal.ONE)
            .withBean(CompositeHistoricalPriceProvider.class)
            .withBean(YahooHistoricalPriceProvider.class)
            .withBean(CachedFallbackHistoricalPriceProvider.class)
            .withBean(KisHistoricalPriceProvider.class)
            .withBean(AssetBacktestVerificationService.class);

    @Test
    void historicalPriceProviderBeanDefaultsToCompositeOnDemandImplementation() {
        contextRunner.run(context ->
                assertInstanceOf(CompositeHistoricalPriceProvider.class, context.getBean(HistoricalPriceProvider.class)));
    }

    @Test
    void assetBacktestVerificationServiceUsesKisProviderForRealPriceVerification() {
        contextRunner.run(context -> {
            AssetBacktestVerificationService service = context.getBean(AssetBacktestVerificationService.class);
            Field field = AssetBacktestVerificationService.class.getDeclaredField("historicalPriceProvider");
            field.setAccessible(true);
            assertInstanceOf(KisHistoricalPriceProvider.class, field.get(service));
        });
    }

    @Test
    void defaultConfigDoesNotInventPricesWhenExternalAndCachedPricesAreMissing() {
        contextRunner.run(context -> {
            AssetPriceDailyRepository priceRepository = context.getBean(AssetPriceDailyRepository.class);
            when(priceRepository.findByAssetIdAndTradeDateBetweenOrderByTradeDateAsc(anyString(), any(), any()))
                    .thenReturn(List.of());

            HistoricalPriceProvider provider = context.getBean(HistoricalPriceProvider.class);
            LocalDate startDate = LocalDate.parse("2025-01-01");
            LocalDate endDate = LocalDate.parse("2025-01-10");

            assertEquals(List.of(), provider.getSecurityPriceSeries("US_MISSING", startDate, endDate));
            assertEquals(List.of(), provider.getBenchmarkSeries("SP500", startDate, endDate));
        });
    }

    @Test
    void syntheticFallbackIsAvailableOnlyWhenExplicitlyEnabled() {
        contextRunner
                .withPropertyValues("backtest.price-fallback.enabled=true")
                .run(context -> {
                    AssetPriceDailyRepository priceRepository = context.getBean(AssetPriceDailyRepository.class);
                    when(priceRepository.findByAssetIdAndTradeDateBetweenOrderByTradeDateAsc(anyString(), any(), any()))
                            .thenReturn(List.of());

                    HistoricalPriceProvider provider = context.getBean(HistoricalPriceProvider.class);
                    LocalDate startDate = LocalDate.parse("2025-01-01");
                    LocalDate endDate = LocalDate.parse("2025-01-10");

                    assertTrue(provider.getSecurityPriceSeries("US_MISSING", startDate, endDate).size() >= 2);
                    assertTrue(provider.getBenchmarkSeries("SP500", startDate, endDate).size() >= 2);
                });
    }
}
