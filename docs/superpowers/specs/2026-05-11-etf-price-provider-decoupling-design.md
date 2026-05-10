# ETF Price Provider Decoupling Design

**Goal:** ETF analysis button requests must not depend on live KIS calls.

**Recommended Approach:** Make the default `HistoricalPriceProvider` a cache-first, fallback-only provider. Keep KIS code available as a non-default external price source for future cache warmers, but remove it from the synchronous ETF analysis path.

## Context

The ETF analysis path calls `EtfDataService.buildReport`, which asks `HistoricalPriceProvider` for holding and benchmark daily prices. The previous default implementation was `KisHistoricalPriceProvider`, so cache misses could fall through to KIS REST calls. That makes a user-facing button depend on external API quota and latency.

## Alternatives Considered

1. Keep KIS in the request path and improve rate limiting.
   - Lower code churn, but still exposes users to KIS rate limits and timeouts.

2. Replace KIS with a different live vendor in the request path.
   - Vendor can change, but the same synchronous external dependency problem remains.

3. Use cache plus fallback in the request path, and move real price loading to external warmers.
   - Recommended. It makes the button reliable and keeps price source choice independent of analysis.

## Design

Create `CachedFallbackHistoricalPriceProvider` as the primary Spring bean for `HistoricalPriceProvider`.

Behavior:
- Cash and bond assets use deterministic synthetic series.
- Stock and benchmark assets read `asset_price_daily` first.
- If the cache covers the requested period, use cached prices.
- If the cache is missing and `backtest.price-fallback.enabled=true`, return deterministic fallback prices.
- If fallback is disabled and cache is missing, return an empty series so analysis reports insufficient data instead of calling KIS.

Keep `KisHistoricalPriceProvider` for future cache warmer use, but it will no longer be the primary provider injected into ETF analysis.

## Testing

Add unit tests for the new provider:
- cached prices are used without external dependencies
- fallback produces a usable series on cache miss
- fallback disabled returns empty on cache miss
- benchmark series also avoids external calls

Run targeted and full Gradle tests before committing.
