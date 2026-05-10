# Yahoo On-Demand Backtest Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** ETF 백테스트 가격 데이터를 DB에 저장하지 않고 요청 시 Yahoo chart API에서 조회해 바로 계산한다.

**Architecture:** 새 `YahooHistoricalPriceProvider`를 primary `HistoricalPriceProvider`로 추가한다. 종목 메타데이터는 `AssetMasterRepository`에서 조회하고, 가격은 `RestTemplate`으로 Yahoo chart API에서 가져온 뒤 DB 저장 없이 `BacktestPricePoint`로 변환한다.

**Tech Stack:** Spring Boot 4, Java 21, RestTemplate, Jackson, JUnit 5, Mockito.

---

### Task 1: Yahoo Provider Tests And Implementation

**Files:**
- Create: `src/main/java/com/uniport/service/backtest/YahooHistoricalPriceProvider.java`
- Test: `src/test/java/com/uniport/service/YahooHistoricalPriceProviderTest.java`

- [x] Write tests for US ticker conversion, KRX suffix conversion, Yahoo JSON parsing, KRW conversion, User-Agent header, and fallback on empty response.
- [x] Run: `./gradlew --no-daemon --max-workers=1 test --tests com.uniport.service.YahooHistoricalPriceProviderTest`
- [x] Implement the minimal provider.
- [x] Re-run the same test and confirm pass.

### Task 2: Primary Bean Selection

**Files:**
- Modify: `src/main/java/com/uniport/service/backtest/CachedFallbackHistoricalPriceProvider.java`
- Modify: `src/test/java/com/uniport/service/HistoricalPriceProviderBeanTest.java`

- [x] Change the bean test to expect `YahooHistoricalPriceProvider` as the primary `HistoricalPriceProvider`.
- [x] Run: `./gradlew --no-daemon --max-workers=1 test --tests com.uniport.service.HistoricalPriceProviderBeanTest`
- [x] Remove `@Primary` from `CachedFallbackHistoricalPriceProvider` and mark Yahoo provider as primary.
- [x] Re-run the same test and confirm pass.

### Task 3: ETF Metadata

**Files:**
- Modify: `src/main/java/com/uniport/service/EtfDataService.java`
- Modify: `src/test/java/com/uniport/service/EtfDataServiceTest.java`

- [x] Change ETF analysis metadata test to expect no DB price cache policy.
- [x] Run: `./gradlew --no-daemon --max-workers=1 test --tests com.uniport.service.EtfDataServiceTest`
- [x] Update `PRICE_SOURCE` and `PRICE_CACHE_POLICY`.
- [x] Re-run ETF tests and confirm pass.

### Task 4: Verification And Commit

**Files:**
- All changed files from Tasks 1-3.

- [x] Run provider, bean, ETF service, and ETF controller search tests together.
- [x] Check `git diff` and stage only this task's files.
- [x] Commit with `fix: use yahoo on-demand ETF backtests`.
- [x] Push `main`.
