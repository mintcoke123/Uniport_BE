# Yahoo On-Demand Backtest Design

## Goal

ETF 백테스트 가격 원천을 DB 가격 캐시에서 요청 시 외부 종가 조회로 바꾼다. DB는 종목 메타데이터(`asset_master`, `stock_master`)만 맡고, 가격 시계열은 분석 요청 시 Yahoo chart API에서 받아 바로 계산한다.

## Chosen Approach

Spring 서버에서 Yahoo chart API를 직접 호출한다. Python `yfinance` sidecar는 배포와 운영 복잡도가 커서 제외한다. Postgres 가격 캐시는 ETF 분석 경로에서 사용하지 않는다.

## Data Flow

1. ETF 분석 요청이 들어온다.
2. `HistoricalPriceProvider`가 `assetId`를 Yahoo ticker로 변환한다.
   - `US_AAPL` -> `AAPL`
   - `KRX_005930` + `KOSPI`/`KRX` -> `005930.KS`
   - `KRX_091990` + `KOSDAQ` -> `091990.KQ`
   - `SP500` -> `SPY`, `NASDAQ` -> `QQQ`, `KOSPI` -> `^KS11`, `KOSDAQ` -> `^KQ11`
3. Yahoo chart API에서 일봉 adjusted close 또는 close를 조회한다.
4. USD 가격은 기존 `FxRateProvider`로 KRW 환산한다.
5. 조회 결과는 DB에 저장하지 않고 `EtfBacktestEngine`에 전달한다.
6. 외부 조회가 실패하거나 데이터가 부족하면 synthetic fallback 설정에 따라 fallback 또는 데이터 부족 오류를 반환한다.
7. Yahoo 호출에는 브라우저형 `User-Agent`를 포함한다. 로컬 검증에서 User-Agent가 없으면 `Too Many Requests`가 반환될 수 있었다.

## Components

- `YahooHistoricalPriceProvider`: ETF 분석의 primary `HistoricalPriceProvider`.
- `CachedFallbackHistoricalPriceProvider`: DB 가격 캐시 구현은 남기되 primary에서 제외한다.
- `KisHistoricalPriceProvider`: KIS 검증용으로 유지하되 ETF 분석 primary로 쓰지 않는다.
- `EtfDataService`: 메타데이터 문구를 `Yahoo Finance chart API, no DB price cache`로 갱신한다.

## Error Handling

Yahoo 응답이 비어 있거나 파싱할 수 없으면 빈 시계열을 반환하고, fallback이 켜져 있으면 synthetic 시계열을 사용한다. fallback이 꺼져 있으면 기존 ETF 분석 경로가 데이터 부족 오류를 반환한다.

## Testing

테스트는 실패를 먼저 확인한 뒤 구현한다. 핵심 검증은 ticker 변환, Yahoo JSON 파싱, User-Agent 헤더, DB 가격 repository 미사용, Spring primary bean 선택, ETF 분석 메타데이터 갱신이다.
