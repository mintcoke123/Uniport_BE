package com.uniport.service;

import com.uniport.repository.AssetMasterRepository;
import com.uniport.repository.AssetPriceDailyRepository;
import com.uniport.service.backtest.CachedFallbackHistoricalPriceProvider;
import com.uniport.service.backtest.FxRateProvider;
import com.uniport.service.backtest.HistoricalPriceProvider;
import com.uniport.service.backtest.KisHistoricalPriceProvider;
import com.uniport.service.backtest.YahooHistoricalPriceProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

class HistoricalPriceProviderBeanTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(AssetPriceDailyRepository.class, () -> mock(AssetPriceDailyRepository.class))
            .withBean(AssetMasterRepository.class, () -> mock(AssetMasterRepository.class))
            .withBean(KisApiService.class, () -> mock(KisApiService.class))
            .withBean(RestTemplate.class, () -> mock(RestTemplate.class))
            .withBean(FxRateProvider.class, () -> (currency, date) -> BigDecimal.ONE)
            .withBean(YahooHistoricalPriceProvider.class)
            .withBean(CachedFallbackHistoricalPriceProvider.class)
            .withBean(KisHistoricalPriceProvider.class)
            .withBean(AssetBacktestVerificationService.class)
            .withPropertyValues("backtest.price-fallback.enabled=true");

    @Test
    void historicalPriceProviderBeanDefaultsToYahooOnDemandImplementation() {
        contextRunner.run(context ->
                assertInstanceOf(YahooHistoricalPriceProvider.class, context.getBean(HistoricalPriceProvider.class)));
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
}
