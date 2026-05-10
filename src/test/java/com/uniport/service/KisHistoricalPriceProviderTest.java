package com.uniport.service;

import com.uniport.dto.IndexChartPriceItemDTO;
import com.uniport.exception.ApiException;
import com.uniport.service.backtest.BacktestPricePoint;
import com.uniport.service.backtest.FxRateProvider;
import com.uniport.service.backtest.KisHistoricalPriceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KisHistoricalPriceProviderTest {

    @Mock
    private KisApiService kisApiService;

    @Test
    void getSecurityPriceSeries_fetchesUsStockThroughOverseasDailyPriceAndConvertsToKrw() {
        FxRateProvider fxRateProvider = (currency, date) -> BigDecimal.valueOf(1300);
        KisHistoricalPriceProvider provider = new KisHistoricalPriceProvider(kisApiService, fxRateProvider);
        when(kisApiService.getOverseasStockDailyChartPrice(eq("NAS"), eq("AAPL"), eq("20250131"), eq("0"), eq("1")))
                .thenReturn(List.of(
                        chart("20250103", "11"),
                        chart("20250102", "10")
                ));

        List<BacktestPricePoint> result = provider.getSecurityPriceSeries("US_AAPL", LocalDate.parse("2025-01-01"), LocalDate.parse("2025-01-31"));

        assertEquals(LocalDate.parse("2025-01-02"), result.get(0).date());
        assertEquals(0, BigDecimal.valueOf(13000).compareTo(result.get(0).adjustedCloseKrw()));
        assertEquals(LocalDate.parse("2025-01-03"), result.get(1).date());
    }

    @Test
    void getSecurityPriceSeries_fetchesAdditionalUsPagesUntilStartDateCovered() {
        FxRateProvider fxRateProvider = (currency, date) -> BigDecimal.valueOf(1300);
        KisHistoricalPriceProvider provider = new KisHistoricalPriceProvider(kisApiService, fxRateProvider);
        when(kisApiService.getOverseasStockDailyChartPrice(eq("NAS"), eq("AAPL"), eq("20250131"), eq("0"), eq("1")))
                .thenReturn(List.of(
                        chart("20250131", "11"),
                        chart("20241101", "10")
                ));
        when(kisApiService.getOverseasStockDailyChartPrice(eq("NAS"), eq("AAPL"), eq("20241031"), eq("0"), eq("1")))
                .thenReturn(List.of(
                        chart("20241031", "9"),
                        chart("20240102", "8")
                ));

        List<BacktestPricePoint> result = provider.getSecurityPriceSeries("US_AAPL", LocalDate.parse("2024-01-01"), LocalDate.parse("2025-01-31"));

        assertEquals(LocalDate.parse("2024-01-02"), result.get(0).date());
        assertEquals(LocalDate.parse("2025-01-31"), result.get(result.size() - 1).date());
        assertEquals(4, result.size());
    }

    @Test
    void getBenchmarkSeries_mapsSp500AndNasdaqToTradableEtfProxies() {
        FxRateProvider fxRateProvider = (currency, date) -> BigDecimal.valueOf(1300);
        KisHistoricalPriceProvider provider = new KisHistoricalPriceProvider(kisApiService, fxRateProvider);
        when(kisApiService.getOverseasStockDailyChartPrice(eq("AMS"), eq("SPY"), eq("20250131"), eq("0"), eq("1")))
                .thenReturn(List.of(chart("20250102", "500")));
        when(kisApiService.getOverseasStockDailyChartPrice(eq("NAS"), eq("QQQ"), eq("20250131"), eq("0"), eq("1")))
                .thenReturn(List.of(chart("20250102", "400")));

        List<BacktestPricePoint> sp500 = provider.getBenchmarkSeries("SP500", LocalDate.parse("2025-01-02"), LocalDate.parse("2025-01-31"));
        List<BacktestPricePoint> nasdaq = provider.getBenchmarkSeries("NASDAQ", LocalDate.parse("2025-01-02"), LocalDate.parse("2025-01-31"));

        assertEquals(0, BigDecimal.valueOf(650000).compareTo(sp500.get(0).adjustedCloseKrw()));
        assertEquals(0, BigDecimal.valueOf(520000).compareTo(nasdaq.get(0).adjustedCloseKrw()));
    }

    @Test
    void getSecurityPriceSeries_normalizesKrxCodeAndSortsAscending() {
        FxRateProvider fxRateProvider = (currency, date) -> BigDecimal.ONE;
        KisHistoricalPriceProvider provider = new KisHistoricalPriceProvider(kisApiService, fxRateProvider);
        when(kisApiService.getStockDailyChartPrice(eq("005930"), eq("20250101"), eq("20250131"), eq("D")))
                .thenReturn(List.of(
                        chart("20250103", "110"),
                        chart("20250102", "100")
                ));

        List<BacktestPricePoint> result = provider.getSecurityPriceSeries("KRX_005930", LocalDate.parse("2025-01-01"), LocalDate.parse("2025-01-31"));

        assertEquals(LocalDate.parse("2025-01-02"), result.get(0).date());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(result.get(0).adjustedCloseKrw()));
        assertEquals(LocalDate.parse("2025-01-03"), result.get(1).date());
    }

    private IndexChartPriceItemDTO chart(String date, String close) {
        return IndexChartPriceItemDTO.builder()
                .date(date)
                .close(new BigDecimal(close))
                .build();
    }
}
