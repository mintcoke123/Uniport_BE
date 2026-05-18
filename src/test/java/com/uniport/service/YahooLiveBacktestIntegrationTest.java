package com.uniport.service;

import com.uniport.repository.AssetMasterRepository;
import com.uniport.service.backtest.BacktestHolding;
import com.uniport.service.backtest.BacktestNavPoint;
import com.uniport.service.backtest.BacktestPricePoint;
import com.uniport.service.backtest.BacktestRequest;
import com.uniport.service.backtest.BacktestResult;
import com.uniport.service.backtest.EtfBacktestEngine;
import com.uniport.service.backtest.YahooHistoricalPriceProvider;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class YahooLiveBacktestIntegrationTest {

    private static final BigDecimal PRINCIPAL = BigDecimal.valueOf(10_000_000L);
    private static final BigDecimal TRANSACTION_FEE_RATE = new BigDecimal("0.00015");
    private static final BigDecimal SLIPPAGE_RATE = new BigDecimal("0.00025");
    private static final String YAHOO_BASE_URL = "https://query1.finance.yahoo.com";

    private final EtfBacktestEngine engine = new EtfBacktestEngine();

    @Test
    void yahooLiveSearch_returnsLeveragedEtfSymbols() {
        requireLiveOptIn();
        YahooAssetSearchClient searchClient = new YahooAssetSearchClient(new RestTemplate(), YAHOO_BASE_URL);

        List<YahooAssetSearchClient.YahooAssetResult> results = searchClient.searchUsEquities("TQQQ", 10);

        assertTrue(
                results.stream().anyMatch(result -> "TQQQ".equals(result.symbol())),
                () -> "Yahoo live search did not return TQQQ. Results: " + results
        );
    }

    @Test
    void yahooLiveBacktest_coversEquitiesEtfsLeveragedInverseCommodityAndBondEtfs() {
        requireLiveOptIn();
        YahooHistoricalPriceProvider provider = yahooProvider();
        LocalDate endDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        LocalDate startDate = endDate.minusYears(3);
        List<BacktestPricePoint> benchmarkSeries = provider.getBenchmarkSeries("SP500", startDate, endDate);
        assertSufficientSeries("SP500 benchmark", benchmarkSeries);

        List<LiveAssetCase> assetCases = List.of(
                new LiveAssetCase("US_AAPL", "Apple", "Equity"),
                new LiveAssetCase("US_NVDA", "NVIDIA", "Equity"),
                new LiveAssetCase("US_SPY", "SPDR S&P 500 ETF", "Broad ETF"),
                new LiveAssetCase("US_QQQ", "Invesco QQQ ETF", "Broad ETF"),
                new LiveAssetCase("US_SOXX", "iShares Semiconductor ETF", "Sector ETF"),
                new LiveAssetCase("US_TLT", "iShares 20+ Year Treasury Bond ETF", "Bond ETF"),
                new LiveAssetCase("US_GLD", "SPDR Gold Shares", "Commodity ETF"),
                new LiveAssetCase("US_TQQQ", "ProShares UltraPro QQQ", "Leveraged ETF"),
                new LiveAssetCase("US_SOXL", "Direxion Daily Semiconductor Bull 3X", "Leveraged ETF"),
                new LiveAssetCase("US_SQQQ", "ProShares UltraPro Short QQQ", "Inverse Leveraged ETF"),
                new LiveAssetCase("US_UPRO", "ProShares UltraPro S&P500", "Leveraged ETF")
        );
        List<String> failures = new ArrayList<>();

        for (LiveAssetCase assetCase : assetCases) {
            try {
                List<BacktestPricePoint> priceSeries = provider.getSecurityPriceSeries(
                        assetCase.assetId(),
                        startDate,
                        endDate
                );
                assertSufficientSeries(assetCase.assetId(), priceSeries);

                BacktestResult result = engine.run(BacktestRequest.builder()
                        .principalAmountKrw(PRINCIPAL)
                        .transactionFeeRate(TRANSACTION_FEE_RATE)
                        .slippageRate(SLIPPAGE_RATE)
                        .rebalancePolicy("NONE")
                        .periodLabel("3Y")
                        .benchmarkName("S&P 500")
                        .holdings(List.of(new BacktestHolding(
                                assetCase.assetId(),
                                assetCase.name(),
                                BigDecimal.valueOf(100),
                                assetCase.category()
                        )))
                        .priceSeriesBySecurityId(Map.of(assetCase.assetId(), priceSeries))
                        .benchmarkSeries(benchmarkSeries)
                        .build());

                assertCompleteLiveResult(assetCase, result);
            } catch (AssertionError | RuntimeException exception) {
                failures.add(assetCase.assetId() + " (" + assetCase.category() + "): " + exception.getMessage());
            }
        }

        assertTrue(
                failures.isEmpty(),
                () -> "Yahoo live backtest failures:\n" + String.join("\n", failures)
        );
    }

    private void requireLiveOptIn() {
        Assumptions.assumeTrue(
                Boolean.getBoolean("yahooLiveBacktestTest")
                        || "true".equalsIgnoreCase(System.getenv("YAHOO_LIVE_BACKTEST_TEST")),
                "Set YAHOO_LIVE_BACKTEST_TEST=true to run Yahoo Finance live API checks."
        );
    }

    private YahooHistoricalPriceProvider yahooProvider() {
        AssetMasterRepository assetMasterRepository = mock(AssetMasterRepository.class);
        when(assetMasterRepository.findByAssetIdAndActiveTrue(anyString())).thenReturn(Optional.empty());
        return new YahooHistoricalPriceProvider(
                new RestTemplate(),
                (currency, date) -> "USD".equals(currency) ? BigDecimal.valueOf(1350) : BigDecimal.ONE,
                assetMasterRepository,
                false,
                YAHOO_BASE_URL
        );
    }

    private void assertSufficientSeries(String label, List<BacktestPricePoint> series) {
        assertNotNull(series, () -> label + " price series is null");
        assertTrue(series.size() >= 500, () -> label + " has insufficient 3Y price points: " + series.size());
        for (int index = 0; index < series.size(); index++) {
            int pointIndex = index;
            BacktestPricePoint point = series.get(index);
            assertNotNull(point.date(), () -> label + " point " + pointIndex + " date is null");
            assertNotNull(point.adjustedCloseKrw(), () -> label + " point " + pointIndex + " price is null");
            assertTrue(point.adjustedCloseKrw().compareTo(BigDecimal.ZERO) > 0,
                    () -> label + " point " + pointIndex + " price is not positive");
            if (index > 0) {
                assertFalse(
                        point.date().isBefore(series.get(index - 1).date()),
                        () -> label + " price series is not sorted at index " + pointIndex
                );
            }
        }
    }

    private void assertCompleteLiveResult(LiveAssetCase assetCase, BacktestResult result) {
        assertNotNull(result.finalNavKrw(), () -> assetCase.assetId() + " final NAV is null");
        assertNotNull(result.profitAmountKrw(), () -> assetCase.assetId() + " profit is null");
        assertNotNull(result.totalReturnPercent(), () -> assetCase.assetId() + " total return is null");
        assertNotNull(result.annualizedReturnPercent(), () -> assetCase.assetId() + " annualized return is null");
        assertNotNull(result.volatilityPercent(), () -> assetCase.assetId() + " volatility is null");
        assertNotNull(result.maxDrawdownPercent(), () -> assetCase.assetId() + " max drawdown is null");
        assertNotNull(result.benchmarkReturnPercent(), () -> assetCase.assetId() + " benchmark return is null");
        assertNotNull(result.excessReturnPercent(), () -> assetCase.assetId() + " excess return is null");
        assertNotNull(result.sharpeRatio(), () -> assetCase.assetId() + " sharpe ratio is null");
        assertNotNull(result.hhi(), () -> assetCase.assetId() + " HHI is null");
        assertTrue(result.hhi().compareTo(BigDecimal.ZERO) >= 0, () -> assetCase.assetId() + " HHI is negative");
        assertTrue(result.hhi().compareTo(BigDecimal.ONE) <= 0, () -> assetCase.assetId() + " HHI exceeds 1");
        assertEquals(assetCase.name(), result.topHoldingName(), () -> assetCase.assetId() + " top holding mismatch");
        assertEquals(assetCase.category(), result.dominantSector(), () -> assetCase.assetId() + " dominant sector mismatch");
        assertNotNull(result.top5WeightPercent(), () -> assetCase.assetId() + " top5 weight is null");
        assertNotNull(result.effectiveHoldings(), () -> assetCase.assetId() + " effective holdings is null");
        assertNotNull(result.cashWeightPercent(), () -> assetCase.assetId() + " cash weight is null");
        assertNotNull(result.benchmarkAnnualizedReturnPercent(), () -> assetCase.assetId() + " benchmark CAGR is null");
        assertNotNull(result.benchmarkVolatilityPercent(), () -> assetCase.assetId() + " benchmark volatility is null");
        assertNotNull(result.benchmarkMaxDrawdownPercent(), () -> assetCase.assetId() + " benchmark max drawdown is null");
        assertNotNull(result.beta(), () -> assetCase.assetId() + " beta is null");
        assertNotNull(result.trackingErrorPercent(), () -> assetCase.assetId() + " tracking error is null");
        assertNotNull(result.winRatePercent(), () -> assetCase.assetId() + " win rate is null");
        assertNotNull(result.riskScore(), () -> assetCase.assetId() + " risk score is null");
        assertNotNull(result.riskGrade(), () -> assetCase.assetId() + " risk grade is null");
        assertNotNull(result.riskGradeLabel(), () -> assetCase.assetId() + " risk grade label is null");
        assertTrue(result.tradingDays() >= 24, () -> assetCase.assetId() + " monthly observations too low");
        assertTrue(result.finalNavKrw().compareTo(BigDecimal.ZERO) > 0, () -> assetCase.assetId() + " final NAV is not positive");
        assertTrue(result.volatilityPercent().compareTo(BigDecimal.ZERO) >= 0, () -> assetCase.assetId() + " volatility is negative");
        assertTrue(result.maxDrawdownPercent().compareTo(BigDecimal.ZERO) <= 0, () -> assetCase.assetId() + " drawdown is positive");
        assertTrue(result.cashWeightPercent().compareTo(BigDecimal.ZERO) >= 0, () -> assetCase.assetId() + " cash weight is negative");
        assertTrue(result.cashWeightPercent().compareTo(BigDecimal.valueOf(100)) <= 0,
                () -> assetCase.assetId() + " cash weight exceeds 100");
        assertFalse(result.navSeries().isEmpty(), () -> assetCase.assetId() + " NAV series is empty");

        for (int index = 0; index < result.navSeries().size(); index++) {
            int pointIndex = index;
            BacktestNavPoint point = result.navSeries().get(index);
            assertNotNull(point.date(), () -> assetCase.assetId() + " NAV point " + pointIndex + " date is null");
            assertNotNull(point.valueKrw(), () -> assetCase.assetId() + " NAV point " + pointIndex + " value is null");
            assertTrue(point.valueKrw().compareTo(BigDecimal.ZERO) > 0,
                    () -> assetCase.assetId() + " NAV point " + pointIndex + " is not positive");
            if (index > 0) {
                assertFalse(
                        point.date().isBefore(result.navSeries().get(index - 1).date()),
                        () -> assetCase.assetId() + " NAV series is not sorted at index " + pointIndex
                );
            }
        }
    }

    private record LiveAssetCase(String assetId, String name, String category) {
    }
}
