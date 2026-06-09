package com.uniport.service;

import com.uniport.entity.AssetPriceDaily;
import com.uniport.repository.AssetPriceDailyRepository;
import com.uniport.service.backtest.BacktestPricePoint;
import com.uniport.service.backtest.FxRateProvider;
import com.uniport.service.backtest.NasdaqHistoricalPriceProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NasdaqHistoricalPriceProviderTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final FxRateProvider fxRateProvider = mock(FxRateProvider.class);
    private final AssetPriceDailyRepository assetPriceDailyRepository = mock(AssetPriceDailyRepository.class);
    private final NasdaqHistoricalPriceProvider provider = new NasdaqHistoricalPriceProvider(
            restTemplate,
            fxRateProvider,
            assetPriceDailyRepository,
            "https://api.nasdaq.test"
    );

    @Test
    void getBenchmarkSeriesFetchesSp500FromSpyAndStoresBenchmarkCache() {
        LocalDate startDate = LocalDate.parse("2025-05-12");
        LocalDate endDate = LocalDate.parse("2025-05-13");
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(nasdaqResponse(), HttpStatus.OK));
        when(fxRateProvider.getKrwRate(eq("USD"), eq(LocalDate.parse("2025-05-12"))))
                .thenReturn(BigDecimal.valueOf(1_300));
        when(fxRateProvider.getKrwRate(eq("USD"), eq(LocalDate.parse("2025-05-13"))))
                .thenReturn(BigDecimal.valueOf(1_310));
        when(assetPriceDailyRepository.findByAssetIdAndTradeDate(eq("BENCHMARK_SP500"), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        ArgumentCaptor<List<AssetPriceDaily>> rowsCaptor = ArgumentCaptor.forClass(List.class);

        List<BacktestPricePoint> result = provider.getBenchmarkSeries("SP500", startDate, endDate);

        assertEquals(2, result.size());
        assertEquals(LocalDate.parse("2025-05-12"), result.get(0).date());
        assertEquals(new BigDecimal("757887.000000"), result.get(0).adjustedCloseKrw());
        assertEquals(LocalDate.parse("2025-05-13"), result.get(1).date());
        assertEquals(new BigDecimal("768760.400000"), result.get(1).adjustedCloseKrw());
        verify(restTemplate).exchange(
                eq(URI.create("https://api.nasdaq.test/api/quote/SPY/historical?assetclass=etf&fromdate=2025-05-12&todate=2025-05-13&limit=9999")),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        );
        verify(assetPriceDailyRepository).saveAll(rowsCaptor.capture());
        List<AssetPriceDaily> cachedRows = rowsCaptor.getValue();
        assertEquals("BENCHMARK_SP500", cachedRows.get(0).getAssetId());
        assertEquals("NASDAQ_HISTORICAL_API", cachedRows.get(0).getSource());
    }

    @Test
    void getBenchmarkSeriesReturnsEmptyForUnsupportedBenchmark() {
        List<BacktestPricePoint> result = provider.getBenchmarkSeries(
                "KOSPI",
                LocalDate.parse("2025-05-12"),
                LocalDate.parse("2025-05-13")
        );

        assertEquals(List.of(), result);
        verify(restTemplate, never()).exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    private String nasdaqResponse() {
        return """
                {
                  "data": {
                    "symbol": "SPY",
                    "tradesTable": {
                      "rows": [
                        {"date": "05/13/2025", "close": "586.84"},
                        {"date": "05/12/2025", "close": "582.99"}
                      ]
                    }
                  },
                  "status": {"rCode": 200}
                }
                """;
    }
}
