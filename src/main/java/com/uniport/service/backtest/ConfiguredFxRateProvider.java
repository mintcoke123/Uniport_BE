package com.uniport.service.backtest;

import com.uniport.exception.ApiException;
import com.uniport.entity.FxRateDaily;
import com.uniport.repository.FxRateDailyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

@Service
public class ConfiguredFxRateProvider implements FxRateProvider {

    private static final String SOURCE_CONFIGURED_DEFAULT = "CONFIGURED_DEFAULT";

    private final BigDecimal usdKrwRate;
    private final FxRateDailyRepository fxRateDailyRepository;

    public ConfiguredFxRateProvider(@Value("${backtest.fx.usd-krw-rate:0}") BigDecimal usdKrwRate,
                                    FxRateDailyRepository fxRateDailyRepository) {
        this.usdKrwRate = usdKrwRate;
        this.fxRateDailyRepository = fxRateDailyRepository;
    }

    @Override
    public BigDecimal getKrwRate(String currency, LocalDate date) {
        String normalized = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if ("KRW".equals(normalized)) {
            return BigDecimal.ONE;
        }
        if ("USD".equals(normalized) && date != null && fxRateDailyRepository != null) {
            return fxRateDailyRepository.findByCurrencyAndRateDate(normalized, date)
                    .map(FxRateDaily::getKrwRate)
                    .orElseGet(() -> fallbackUsdRate(normalized, date));
        }
        if ("USD".equals(normalized) && usdKrwRate != null && usdKrwRate.compareTo(BigDecimal.ZERO) > 0) {
            return usdKrwRate;
        }
        throw new ApiException("USD/KRW FX rate is required for overseas backtesting", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private BigDecimal fallbackUsdRate(String normalized, LocalDate date) {
        if (usdKrwRate != null && usdKrwRate.compareTo(BigDecimal.ZERO) > 0) {
            fxRateDailyRepository.save(FxRateDaily.builder()
                    .currency(normalized)
                    .rateDate(date)
                    .krwRate(usdKrwRate)
                    .source(SOURCE_CONFIGURED_DEFAULT)
                    .build());
            return usdKrwRate;
        }
        throw new ApiException("USD/KRW FX rate is required for overseas backtesting", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
