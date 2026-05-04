# Admin Console Backend Flow

## Scope

This backend flow supports the new admin-only data console for:

- ETF edit / analysis / discovery data
- stock news articles
- community posts and comments
- home dashboard top-group insights
- user profile management
- point shop products / inventory / wallets / orders
- friend relationship management

The admin console API is intentionally open within the current local/dev setup:

- `GET/POST/PUT/DELETE /api/admin-console/**`

Security is relaxed for this path in:

- [SecurityConfig.java](/abs/path/C:/uniport/Uniport_BE/src/main/java/com/uniport/config/SecurityConfig.java:37)

## Data Model

The admin console persists data in JPA-backed tables created by `ddl-auto: update`.

### Content domains

- `managed_etfs`
- `managed_news_articles`
- `managed_community_posts`
- `managed_community_comments`
- `managed_group_insights`

### Point shop domains

The point shop structure follows `PRD-UniPort-Point-Shop.md`.

- `point_wallets`
- `point_transactions`
- `point_shop_products`
- `gifticon_inventory`
- `point_shop_orders`

### Social domain

- `friend_relations`

### Existing users

User profile editing continues to use the existing `users` table.

## Admin API Groups

### ETF

- `GET /api/admin-console/etfs`
- `POST /api/admin-console/etfs`
- `PUT /api/admin-console/etfs/{id}`
- `DELETE /api/admin-console/etfs/{id}`

Stored fields include:

- `etfCode`
- `title`
- `theme`
- `benchmark`
- `period`
- `riskLevel`
- `returnRate`
- `popularityScore`
- `favoriteCount`
- `imageUrl`
- `shortDescription`
- `holdingsJson`
- `trendPointsJson`
- `analysisSummaryJson`

This keeps nested ETF analysis structures editable without forcing a rigid schema too early.

### News

- `GET /api/admin-console/news`
- `POST /api/admin-console/news`
- `PUT /api/admin-console/news/{id}`
- `DELETE /api/admin-console/news/{id}`

Stored fields include article body plus JSON blobs for:

- `companyInfoJson`
- `tagsJson`
- `opinionsJson`

### Community

- `GET /api/admin-console/community/posts`
- `POST /api/admin-console/community/posts`
- `PUT /api/admin-console/community/posts/{id}`
- `DELETE /api/admin-console/community/posts/{id}`
- `POST /api/admin-console/community/posts/{postId}/comments`
- `PUT /api/admin-console/community/comments/{id}`
- `DELETE /api/admin-console/community/comments/{id}`

### Home insights

- `GET /api/admin-console/group-insights/home`
- `PUT /api/admin-console/group-insights/home`

The `consensusJson` field stores the top-consensus list shown on the home dashboard.

### Users

- `GET /api/admin-console/users`
- `PATCH /api/admin-console/users/{id}`
- `DELETE /api/admin-console/users/{id}`

User editing covers both account basics and mypage-style profile fields:

- nickname
- phone
- email
- profile image
- team
- role
- investment profile result
- investment level
- interest sector
- asset summary fields

### Point shop

- `GET/POST/PUT/DELETE /api/admin-console/point-shop/products`
- `GET/POST/PUT/DELETE /api/admin-console/point-shop/inventory`
- `GET /api/admin-console/point-shop/wallets`
- `PUT /api/admin-console/point-shop/wallets/{userId}`
- `GET/POST/PUT/DELETE /api/admin-console/point-shop/orders`

Wallet adjustment writes both:

- `point_wallets`
- `point_transactions` with `type=ADJUST`

### Friends

- `GET /api/admin-console/friends`
- `POST /api/admin-console/friends`
- `PUT /api/admin-console/friends/{id}`
- `DELETE /api/admin-console/friends/{id}`

## Stock Data Handoff Alignment

The `stock-data-api-handoff.md` handoff is now split into two layers:

### Layer 1: now implemented without mock data

- Stock search uses `stock_master` through [StockMasterSearchService.java](/abs/path/C:/uniport/Uniport_BE/src/main/java/com/uniport/service/StockMasterSearchService.java:14)
- Live quote and volume come from KIS through [KisApiService.java](/abs/path/C:/uniport/Uniport_BE/src/main/java/com/uniport/service/KisApiService.java:1)
- Aggregate stock detail is assembled in [StockService.java](/abs/path/C:/uniport/Uniport_BE/src/main/java/com/uniport/service/StockService.java:27)
- Stock news now comes from persisted admin-managed records through [ManagedStockNewsService.java](/abs/path/C:/uniport/Uniport_BE/src/main/java/com/uniport/service/ManagedStockNewsService.java:1)
- Stock discussion and investor sentiment now come from persisted community records through [CommunityService.java](/abs/path/C:/uniport/Uniport_BE/src/main/java/com/uniport/service/CommunityService.java:1)

Current aggregate response on `GET /api/stocks/{id}` is:

1. resolve stock code
2. fetch current KIS quote
3. resolve latest display name from KIS or `stock_master`
4. resolve authenticated holding from `holdings` or `team_holdings`
5. attach latest managed news records for the same stock
6. derive company summary and optional financial rows from `companyInfoJson`
7. derive investor sentiment and discussion count from managed community posts
8. return one stock detail payload

This means the stock detail flow is already mock-free for:

- search
- current price
- change / volume
- holdings
- managed news
- managed discussion
- investor sentiment

### Layer 2: recommended next production upgrade

The remaining gap versus the handoff doc is external financial fundamentals.

Recommended next implementation steps:

1. Add `stock_company_map` persistence for `stockCode -> corpCode`.
2. Add an OpenDART client for company overview and quarter summaries.
3. Populate `financialData` from OpenDART first, then fall back to admin-managed `companyInfoJson`.
4. Populate `companyInfo` from OpenDART first, then fall back to managed news / `stock_master`.
5. Keep admin-managed news and community as first-party editable content rather than replacing them.

## Real Data Community Flow

Community APIs are now JPA-backed instead of in-memory mock state.

- Feed controller: [CommunityController.java](/abs/path/C:/uniport/Uniport_BE/src/main/java/com/uniport/controller/CommunityController.java:1)
- Main service: [CommunityService.java](/abs/path/C:/uniport/Uniport_BE/src/main/java/com/uniport/service/CommunityService.java:1)

Persisted tables:

- `managed_community_posts`
- `managed_community_comments`
- `managed_community_post_likes`
- `managed_community_reports`

Important post fields:

- `authorUserId`
- `authorName`
- `stockCode`
- `stockName`
- `sentiment`
- `title`
- `content`

Supported public flows:

- feed list with `sort`, `type`, `stockCode`, `sentiment`
- post detail
- create / update / delete
- like / unlike
- comment list / create / delete
- post report / comment report
- stock sentiment summary: `GET /api/community/stocks/{stockCode}/sentiment`

## Real Data News Flow

Stock news APIs are now JPA-backed instead of `StockNewsMockService`.

- Controller: [StockNewsController.java](/abs/path/C:/uniport/Uniport_BE/src/main/java/com/uniport/controller/StockNewsController.java:1)
- Main service: [ManagedStockNewsService.java](/abs/path/C:/uniport/Uniport_BE/src/main/java/com/uniport/service/ManagedStockNewsService.java:1)

Persisted table:

- `managed_news_articles`

Editable fields that power both admin and public APIs:

- `newsKey`
- `title`
- `sourceLabel`
- `stockCode`
- `stockName`
- `summary`
- `content`
- `companyInfoJson`
- `tagsJson`
- `opinionsJson`
- `publishedAt`

## Verification

Verified during this change:

- `./gradlew test`
- `npm run type-check`
- `npm run build`
