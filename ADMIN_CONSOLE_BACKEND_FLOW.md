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

The `stock-data-api-handoff.md` document describes a future production-grade stock detail flow:

1. stock code -> corp code mapping
2. KIS quote fetch
3. OpenDART financial summary fetch
4. OpenDART company fetch
5. internal DB enrichment
6. aggregate `/api/stocks/{stockCode}/detail`

Current codebase status:

- KIS quote flow already exists in [KisApiService.java](/abs/path/C:/uniport/Uniport_BE/src/main/java/com/uniport/service/KisApiService.java:1)
- stock detail API already exists in [ApiStockController.java](/abs/path/C:/uniport/Uniport_BE/src/main/java/com/uniport/controller/ApiStockController.java:18)
- current financial/company/news sections inside [StockService.java](/abs/path/C:/uniport/Uniport_BE/src/main/java/com/uniport/service/StockService.java:64) are still placeholder-oriented

Recommended next implementation steps for the stock handoff:

1. Add `stock_company_map` persistence and corpCode sync job.
2. Add OpenDART client service for financial summary and company info.
3. Split stock detail into:
   - quote
   - financial summary
   - company
   - aggregated detail
4. Replace placeholder financial/company fields in `StockService`.
5. Connect admin-managed news records to stock detail news output.

## Verification

Verified during this change:

- `./gradlew test`
- `npm run type-check`
- `npm run build`
