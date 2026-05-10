package com.uniport.service;

import com.uniport.entity.AssetMaster;
import com.uniport.repository.AssetMasterRepository;
import com.uniport.service.backtest.BacktestPricePoint;
import com.uniport.service.backtest.YahooHistoricalPriceProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YahooHistoricalPriceProviderTest {

    @Test
    void getSecurityPriceSeries_fetchesUsAdjustedCloseAndConvertsToKrwWithoutPriceCache() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AssetMasterRepository assetMasterRepository = mock(AssetMasterRepository.class);
        YahooHistoricalPriceProvider provider = new YahooHistoricalPriceProvider(
                restTemplate,
                (currency, date) -> "USD".equals(currency) ? BigDecimal.valueOf(1300) : BigDecimal.ONE,
                assetMasterRepository,
                false,
                "https://query1.finance.yahoo.com"
        );
        when(assetMasterRepository.findByAssetIdAndActiveTrue("US_AAPL")).thenReturn(Optional.of(asset(
                "US_AAPL", "AAPL", "NASDAQ", "USD"
        )));
        stubYahoo(restTemplate, chartJson(
                List.of(1735689600L, 1735776000L),
                List.of("10.0", "11.0"),
                List.of("9.5", "10.5")
        ));

        List<BacktestPricePoint> result = provider.getSecurityPriceSeries(
                "US_AAPL",
                LocalDate.parse("2025-01-01"),
                LocalDate.parse("2025-01-02")
        );

        assertEquals(2, result.size());
        assertEquals(LocalDate.parse("2025-01-01"), result.get(0).date());
        assertEquals(0, new BigDecimal("12350.000000").compareTo(result.get(0).adjustedCloseKrw()));
        assertEquals(0, new BigDecimal("13650.000000").compareTo(result.get(1).adjustedCloseKrw()));
        URI uri = capturedUri(restTemplate);
        assertEquals(true, uri.toString().contains("/v8/finance/chart/AAPL?"));
        assertEquals(true, capturedRequest(restTemplate).getHeaders().getFirst("User-Agent").contains("Mozilla"));
    }

    @Test
    void getSecurityPriceSeries_usesKosdaqYahooSuffixFromAssetMetadata() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AssetMasterRepository assetMasterRepository = mock(AssetMasterRepository.class);
        YahooHistoricalPriceProvider provider = new YahooHistoricalPriceProvider(
                restTemplate,
                (currency, date) -> BigDecimal.ONE,
                assetMasterRepository,
                false,
                "https://query1.finance.yahoo.com"
        );
        when(assetMasterRepository.findByAssetIdAndActiveTrue("KRX_091990")).thenReturn(Optional.of(asset(
                "KRX_091990", "091990", "KOSDAQ", "KRW"
        )));
        stubYahoo(restTemplate, chartJson(
                List.of(1735689600L, 1735776000L),
                List.of("100.0", "110.0"),
                List.of()
        ));

        List<BacktestPricePoint> result = provider.getSecurityPriceSeries(
                "KRX_091990",
                LocalDate.parse("2025-01-01"),
                LocalDate.parse("2025-01-02")
        );

        assertEquals(2, result.size());
        assertEquals(0, new BigDecimal("100.000000").compareTo(result.get(0).adjustedCloseKrw()));
        URI uri = capturedUri(restTemplate);
        assertEquals(true, uri.toString().contains("/v8/finance/chart/091990.KQ?"));
    }

    @Test
    void getSecurityPriceSeries_returnsSyntheticFallbackOnYahooMissWhenEnabled() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AssetMasterRepository assetMasterRepository = mock(AssetMasterRepository.class);
        YahooHistoricalPriceProvider provider = new YahooHistoricalPriceProvider(
                restTemplate,
                (currency, date) -> BigDecimal.ONE,
                assetMasterRepository,
                true,
                "https://query1.finance.yahoo.com"
        );
        when(assetMasterRepository.findByAssetIdAndActiveTrue("US_MISSING")).thenReturn(Optional.of(asset(
                "US_MISSING", "MISSING", "NASDAQ", "USD"
        )));
        stubYahoo(restTemplate, """
                {"chart":{"result":[],"error":null}}
                """);

        List<BacktestPricePoint> result = provider.getSecurityPriceSeries(
                "US_MISSING",
                LocalDate.parse("2025-01-01"),
                LocalDate.parse("2025-01-10")
        );

        assertEquals(true, result.size() >= 2);
        assertEquals(LocalDate.parse("2025-01-01"), result.get(0).date());
    }

    @Test
    void getBenchmarkSeries_mapsSp500ToSpy() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AssetMasterRepository assetMasterRepository = mock(AssetMasterRepository.class);
        YahooHistoricalPriceProvider provider = new YahooHistoricalPriceProvider(
                restTemplate,
                (currency, date) -> "USD".equals(currency) ? BigDecimal.valueOf(1300) : BigDecimal.ONE,
                assetMasterRepository,
                false,
                "https://query1.finance.yahoo.com"
        );
        stubYahoo(restTemplate, chartJson(
                List.of(1735689600L, 1735776000L),
                List.of("500.0", "510.0"),
                List.of()
        ));

        List<BacktestPricePoint> result = provider.getBenchmarkSeries(
                "SP500",
                LocalDate.parse("2025-01-01"),
                LocalDate.parse("2025-01-02")
        );

        assertEquals(2, result.size());
        URI uri = capturedUri(restTemplate);
        assertEquals(true, uri.toString().contains("/v8/finance/chart/SPY?"));
    }

    private URI capturedUri(RestTemplate restTemplate) {
        ArgumentCaptor<URI> captor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).exchange(captor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        return captor.getValue();
    }

    private HttpEntity<?> capturedRequest(RestTemplate restTemplate) {
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(any(URI.class), eq(HttpMethod.GET), captor.capture(), eq(String.class));
        return captor.getValue();
    }

    private void stubYahoo(RestTemplate restTemplate, String body) {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body));
    }

    private AssetMaster asset(String assetId, String symbol, String market, String currency) {
        return AssetMaster.builder()
                .assetId(assetId)
                .assetType("STOCK")
                .name(symbol)
                .symbol(symbol)
                .market(market)
                .currency(currency)
                .active(true)
                .backtestEnabled(false)
                .priceSourceStatus("PENDING_VERIFICATION")
                .build();
    }

    private String chartJson(List<Long> timestamps, List<String> closes, List<String> adjustedCloses) {
        return """
                {
                  "chart": {
                    "result": [{
                      "timestamp": [%s],
                      "indicators": {
                        "quote": [{"close": [%s]}],
                        "adjclose": [{"adjclose": [%s]}]
                      }
                    }],
                    "error": null
                  }
                }
                """.formatted(
                joinLongs(timestamps),
                String.join(", ", closes),
                String.join(", ", adjustedCloses)
        );
    }

    private String joinLongs(List<Long> values) {
        return values.stream().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("");
    }
}
