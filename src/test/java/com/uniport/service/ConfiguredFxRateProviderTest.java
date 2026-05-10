package com.uniport.service;

import com.uniport.entity.FxRateDaily;
import com.uniport.repository.FxRateDailyRepository;
import com.uniport.service.backtest.ConfiguredFxRateProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfiguredFxRateProviderTest {

    @Test
    void getKrwRate_prefersDailyFxCache() {
        FxRateDailyRepository repository = mock(FxRateDailyRepository.class);
        LocalDate date = LocalDate.parse("2025-01-02");
        when(repository.findByCurrencyAndRateDate("USD", date))
                .thenReturn(Optional.of(rate("USD", date, "1325.50", "KIS_FX_DAILY")));
        ConfiguredFxRateProvider provider = new ConfiguredFxRateProvider(BigDecimal.ZERO, repository);

        BigDecimal result = provider.getKrwRate("USD", date);

        assertEquals(0, new BigDecimal("1325.50").compareTo(result));
        verify(repository, never()).save(argThat(saved -> true));
    }

    @Test
    void getKrwRate_persistsConfiguredFallbackRateByDate() {
        FxRateDailyRepository repository = mock(FxRateDailyRepository.class);
        LocalDate date = LocalDate.parse("2025-01-02");
        when(repository.findByCurrencyAndRateDate("USD", date)).thenReturn(Optional.empty());
        ConfiguredFxRateProvider provider = new ConfiguredFxRateProvider(new BigDecimal("1300"), repository);

        BigDecimal result = provider.getKrwRate("usd", date);

        assertEquals(0, new BigDecimal("1300").compareTo(result));
        verify(repository).save(argThat(saved ->
                "USD".equals(saved.getCurrency())
                        && date.equals(saved.getRateDate())
                        && new BigDecimal("1300").compareTo(saved.getKrwRate()) == 0
                        && "CONFIGURED_DEFAULT".equals(saved.getSource())
        ));
    }

    private FxRateDaily rate(String currency, LocalDate date, String krwRate, String source) {
        return FxRateDaily.builder()
                .currency(currency)
                .rateDate(date)
                .krwRate(new BigDecimal(krwRate))
                .source(source)
                .build();
    }
}
