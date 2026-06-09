package com.uniport.service.backtest;

import com.uniport.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

@Service
@Primary
public class CompositeHistoricalPriceProvider implements HistoricalPriceProvider {

    private static final Logger log = LoggerFactory.getLogger(CompositeHistoricalPriceProvider.class);

    private final YahooHistoricalPriceProvider yahooHistoricalPriceProvider;
    private final KisHistoricalPriceProvider kisHistoricalPriceProvider;
    private final NasdaqHistoricalPriceProvider nasdaqHistoricalPriceProvider;
    private final CachedFallbackHistoricalPriceProvider cachedFallbackHistoricalPriceProvider;

    public CompositeHistoricalPriceProvider(YahooHistoricalPriceProvider yahooHistoricalPriceProvider,
                                            KisHistoricalPriceProvider kisHistoricalPriceProvider,
                                            NasdaqHistoricalPriceProvider nasdaqHistoricalPriceProvider,
                                            CachedFallbackHistoricalPriceProvider cachedFallbackHistoricalPriceProvider) {
        this.yahooHistoricalPriceProvider = yahooHistoricalPriceProvider;
        this.kisHistoricalPriceProvider = kisHistoricalPriceProvider;
        this.nasdaqHistoricalPriceProvider = nasdaqHistoricalPriceProvider;
        this.cachedFallbackHistoricalPriceProvider = cachedFallbackHistoricalPriceProvider;
    }

    @Override
    public List<BacktestPricePoint> getSecurityPriceSeries(String securityId, LocalDate startDate, LocalDate endDate) {
        return firstAvailable(
                "security " + safeId(securityId),
                List.of(
                        () -> yahooHistoricalPriceProvider.getSecurityPriceSeries(securityId, startDate, endDate),
                        () -> kisHistoricalPriceProvider.getSecurityPriceSeries(securityId, startDate, endDate),
                        () -> cachedFallbackHistoricalPriceProvider.getSecurityPriceSeries(securityId, startDate, endDate)
                )
        );
    }

    @Override
    public List<BacktestPricePoint> getSecurityPriceSeriesForEligibility(String securityId,
                                                                         LocalDate startDate,
                                                                         LocalDate endDate) {
        return firstAvailable(
                "eligibility " + safeId(securityId),
                List.of(
                        () -> yahooHistoricalPriceProvider.getSecurityPriceSeriesForEligibility(securityId, startDate, endDate),
                        () -> kisHistoricalPriceProvider.getSecurityPriceSeriesForEligibility(securityId, startDate, endDate),
                        () -> cachedFallbackHistoricalPriceProvider.getSecurityPriceSeriesForEligibility(securityId, startDate, endDate)
                )
        );
    }

    @Override
    public List<BacktestPricePoint> getBenchmarkSeries(String benchmarkId, LocalDate startDate, LocalDate endDate) {
        return firstAvailable(
                "benchmark " + safeId(benchmarkId),
                List.of(
                        () -> yahooHistoricalPriceProvider.getBenchmarkSeries(benchmarkId, startDate, endDate),
                        () -> kisHistoricalPriceProvider.getBenchmarkSeries(benchmarkId, startDate, endDate),
                        () -> nasdaqHistoricalPriceProvider.getBenchmarkSeries(benchmarkId, startDate, endDate),
                        () -> cachedFallbackHistoricalPriceProvider.getBenchmarkSeries(benchmarkId, startDate, endDate)
                )
        );
    }

    private List<BacktestPricePoint> firstAvailable(
            String lookupLabel,
            List<Supplier<List<BacktestPricePoint>>> providers
    ) {
        for (Supplier<List<BacktestPricePoint>> provider : providers) {
            try {
                List<BacktestPricePoint> series = provider.get();
                if (series != null && series.size() >= 2) {
                    return series;
                }
            } catch (ApiException e) {
                if (isConfigurationFailure(e)) {
                    throw e;
                }
                log.debug("[backtest-price] provider failed for {}: {}", lookupLabel, safeMessage(e));
            } catch (RuntimeException e) {
                log.debug("[backtest-price] provider failed for {}: {}", lookupLabel, safeMessage(e));
            }
        }
        return List.of();
    }

    private boolean isConfigurationFailure(ApiException e) {
        String message = e.getMessage();
        return message != null && message.contains("FX rate is required");
    }

    private String safeId(String value) {
        return value == null || value.isBlank() ? "(blank)" : value.trim();
    }

    private String safeMessage(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
