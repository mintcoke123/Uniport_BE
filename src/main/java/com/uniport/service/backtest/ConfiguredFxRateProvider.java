package com.uniport.service.backtest;

import com.uniport.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

@Service
public class ConfiguredFxRateProvider implements FxRateProvider {

    private final BigDecimal usdKrwRate;

    public ConfiguredFxRateProvider(@Value("${backtest.fx.usd-krw-rate:0}") BigDecimal usdKrwRate) {
        this.usdKrwRate = usdKrwRate;
    }

    @Override
    public BigDecimal getKrwRate(String currency, LocalDate date) {
        String normalized = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if ("KRW".equals(normalized)) {
            return BigDecimal.ONE;
        }
        if ("USD".equals(normalized) && usdKrwRate != null && usdKrwRate.compareTo(BigDecimal.ZERO) > 0) {
            return usdKrwRate;
        }
        throw new ApiException("USD/KRW FX rate is required for overseas backtesting", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
