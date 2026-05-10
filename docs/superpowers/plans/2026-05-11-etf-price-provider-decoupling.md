# ETF Price Provider Decoupling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make ETF analysis use `asset_price_daily` cache plus deterministic fallback without live KIS calls.

**Architecture:** Add a new primary `HistoricalPriceProvider` implementation with no `KisApiService` dependency. Leave the KIS implementation available for future cache warmer work, but make it non-default for ETF analysis injection.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, Gradle.

---

### Task 1: Add Cache/Fallback Provider Tests

**Files:**
- Create: `src/test/java/com/uniport/service/CachedFallbackHistoricalPriceProviderTest.java`

- [ ] **Step 1: Write failing tests**

Create tests that instantiate `CachedFallbackHistoricalPriceProvider` directly with mocked `AssetPriceDailyRepository`.

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew test --tests com.uniport.service.CachedFallbackHistoricalPriceProviderTest`

Expected: compilation failure because the provider does not exist yet.

### Task 2: Implement Primary Cache/Fallback Provider

**Files:**
- Create: `src/main/java/com/uniport/service/backtest/CachedFallbackHistoricalPriceProvider.java`
- Modify: `src/main/java/com/uniport/service/EtfDataService.java`

- [ ] **Step 1: Implement provider**

Create a `@Service` and `@Primary` provider that reads cache and optionally returns fallback prices.

- [ ] **Step 2: Update metadata source label**

Change `PRICE_SOURCE` to describe cache plus external warmer/fallback, not KIS request-path calls.

- [ ] **Step 3: Run targeted tests**

Run: `./gradlew test --tests com.uniport.service.CachedFallbackHistoricalPriceProviderTest --tests com.uniport.service.EtfDataServiceTest`

Expected: pass.

### Task 3: Verify Full Behavior

**Files:**
- No extra files.

- [ ] **Step 1: Run full test suite**

Run: `./gradlew test`

Expected: pass.

- [ ] **Step 2: Review git diff**

Run: `git diff --stat` and `git status --short --branch`.

Expected: only provider, tests, metadata, and docs are changed.
