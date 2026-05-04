# Mock Data To DB Migration

## 1. 변경 전: 실제로 mock 응답을 만들던 경로

### 홈 대시보드
- Controller: `ApiHomeController`
- Service: `MockInvestingHomeService`
- Endpoints:
  - `GET /api/home/mock-investing-summary`
  - `GET /api/home/group-matching-dashboard`
  - `GET /api/home/group-insights`
- 문제:
  - 상위 그룹 인사이트, 언락 문구, 매칭 대시보드 카드 일부가 서비스 내부 상수로 내려감

### ETF
- Controllers:
  - `CustomEtfController`
  - `EtfDiscoveryController`
  - `EtfAnalysisReportController`
- Service: `EtfMockService`
- Endpoints:
  - `GET/POST/PUT /api/custom-etfs/**`
  - `GET/POST/DELETE /api/etf-discovery/**`
  - `GET /api/etf-analysis-reports/{reportId}`
- 문제:
  - 커스텀 ETF 목록/상세/수정/분석/즐겨찾기/탐색이 모두 메모리와 하드코드 데이터에 의존

### 러닝
- Controller: `LearningController`
- Service: `LearningService`
- Data source: `LearningMockDataProvider`
- Endpoints:
  - `GET /api/learning/home`
  - `GET /api/learning/courses`
  - `POST /api/learning/courses/{courseId}/start`
  - `GET /api/learning/courses/{courseId}`
  - `GET /api/learning/courses/{courseId}/days/{dayId}`
  - `POST /api/learning/steps/{stepId}/submit`
  - `POST /api/learning/courses/{courseId}/days/{dayId}/complete`
- 문제:
  - 코스/Day/Step 카탈로그가 자바 코드에 박혀 있었고
  - 사용자 진행 상태는 서버 메모리(`ConcurrentHashMap`)에만 저장됨

### 마이페이지 / 포인트 / 상점 / 친구
- Controllers:
  - `MyPageController`
  - `PointsController`
  - `ShopController`
  - `FriendsController`
- Service: `MyPageMockService`
- Endpoints:
  - `GET /api/mypage`
  - `GET /api/points/balance`
  - `GET /api/shop/items`
  - `GET /api/shop/redemptions/**`
  - `POST /api/shop/redemptions`
  - `GET /api/friends/**`
  - `POST /api/friends/requests`
- 문제:
  - 포인트 잔액, 친구 목록, 교환 내역, 상품 목록이 모두 서비스 내부 seed 데이터/메모리 상태에 의존

## 2. 변경 후: DB 기반으로 바뀐 구조

### 홈 대시보드
- New service: `HomeDataService`
- Controller wiring:
  - `ApiHomeController -> HomeDataService`
- DB source:
  - `managed_group_insights`
- 실데이터 방식:
  - 상위 그룹 인사이트는 `managed_group_insights.consensus_json`, `top_group_name`, `top_pick`, `comment` 에서 조회
  - 내 투자 요약/랭킹/참가 신청 내역은 기존 운영 서비스(`MeService`, `RankingService`, `CompetitionService`)를 그대로 활용

### ETF
- New service: `EtfDataService`
- Controller wiring:
  - `CustomEtfController -> EtfDataService`
  - `EtfDiscoveryController -> EtfDataService`
  - `EtfAnalysisReportController -> EtfDataService`
- DB source:
  - `managed_etfs`
  - `managed_etf_analysis_reports`
  - `managed_etf_favorites`
  - `stock_master` (종목 이름 해석용)
- 실데이터 방식:
  - 커스텀 ETF는 `managed_etfs.owner_user_id + source_type=CUSTOM` 기준 조회
  - 탐색 ETF는 `managed_etfs.source_type=DISCOVERY` 기준 조회
  - ETF 보유 종목 비중은 `managed_etfs.holdings_json` 저장
  - 분석 리포트는 요청 시 계산 후 `managed_etf_analysis_reports.report_json` 으로 저장
  - 즐겨찾기는 `managed_etf_favorites` 로 관리

### 러닝
- Service: `LearningService` (DB persistence 로 전환)
- Transitional seed source: `LearningMockDataProvider`
- DB source:
  - `learning_courses`
  - `learning_user_states`
- 실데이터 방식:
  - 코스 마스터는 최초 1회 `LearningMockDataProvider` 내용을 `learning_courses.days_json` 으로 적재
  - 이후 API 조회는 `learning_courses` 기준
  - 사용자 진행 상태는 `learning_user_states` 에 저장
  - 서버 재시작 후에도 진행 상태 유지
- 참고:
  - 현재 `LearningMockDataProvider` 는 런타임 응답 소스가 아니라 초기 마이그레이션 seed 역할만 담당

### 마이페이지 / 포인트 / 상점 / 친구
- New service: `PointSocialDataService`
- Controller wiring:
  - `MyPageController -> PointSocialDataService`
  - `PointsController -> PointSocialDataService`
  - `ShopController -> PointSocialDataService`
  - `FriendsController -> PointSocialDataService`
- DB source:
  - `point_wallets`
  - `point_transactions`
  - `point_shop_products`
  - `gifticon_inventory`
  - `point_shop_orders`
  - `friend_relations`
  - `users`
- 실데이터 방식:
  - 포인트 잔액은 `point_wallets.balance`
  - 상점 목록은 `point_shop_products`
  - 재고 여부는 `gifticon_inventory.status=AVAILABLE`
  - 교환은 `point_shop_orders`, `point_transactions`, `gifticon_inventory` 를 함께 갱신
  - 친구 목록/요청/대시보드는 `friend_relations` 와 `users` 조합으로 계산

## 3. 현재 runtime 기준 정리

### 더 이상 mock service 를 타지 않는 API
- `/api/home/**`
- `/api/custom-etfs/**`
- `/api/etf-discovery/**`
- `/api/etf-analysis-reports/**`
- `/api/learning/**`
- `/api/mypage`
- `/api/points/**`
- `/api/shop/**`
- `/api/friends/**`

### 아직 소스에 남아 있지만 runtime 경로에서 빠진 legacy mock 클래스
- `MockInvestingHomeService`
- `EtfMockService`
- `MyPageMockService`
- `CommunityMockService`
- `StockNewsMockService`

## 4. 운영상 주의점

- ETF 탐색 목록은 이제 DB에 `source_type=DISCOVERY` 데이터가 있어야 내려간다.
- 러닝 코스는 최초 실행 시 `learning_courses` 가 비어 있으면 자동 seed 된다.
- 홈 인사이트는 `managed_group_insights` 값이 비어 있으면 기본 빈 레코드가 생성된다.
