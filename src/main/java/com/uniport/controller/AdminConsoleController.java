package com.uniport.controller;

import com.uniport.entity.Competition;
import com.uniport.entity.FriendRelation;
import com.uniport.entity.GifticonInventory;
import com.uniport.entity.ManagedCommunityComment;
import com.uniport.entity.ManagedCommunityPost;
import com.uniport.entity.ManagedEtf;
import com.uniport.entity.ManagedGroupInsight;
import com.uniport.entity.ManagedNewsArticle;
import com.uniport.entity.PointShopOrder;
import com.uniport.entity.PointShopProduct;
import com.uniport.entity.PointTransaction;
import com.uniport.entity.PointWallet;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.dto.EducationCatalogResponseDTO;
import com.uniport.dto.EducationDayContentResponseDTO;
import com.uniport.repository.FriendRelationRepository;
import com.uniport.repository.GifticonInventoryRepository;
import com.uniport.repository.ManagedCommunityCommentRepository;
import com.uniport.repository.ManagedCommunityPostRepository;
import com.uniport.repository.ManagedEtfRepository;
import com.uniport.repository.ManagedGroupInsightRepository;
import com.uniport.repository.ManagedNewsArticleRepository;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.PointShopOrderRepository;
import com.uniport.repository.PointShopProductRepository;
import com.uniport.repository.PointTransactionRepository;
import com.uniport.repository.PointWalletRepository;
import com.uniport.repository.UserRepository;
import com.uniport.service.CompetitionService;
import com.uniport.service.EducationContentService;
import com.uniport.service.MatchingRoomService;
import com.uniport.service.UserAccountDeletionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin-console")
public class AdminConsoleController {

    private static final String GROUP_INSIGHT_KEY = "HOME_TOP";

    private final ManagedEtfRepository managedEtfRepository;
    private final ManagedNewsArticleRepository managedNewsArticleRepository;
    private final ManagedCommunityPostRepository managedCommunityPostRepository;
    private final ManagedCommunityCommentRepository managedCommunityCommentRepository;
    private final ManagedGroupInsightRepository managedGroupInsightRepository;
    private final PointWalletRepository pointWalletRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final PointShopProductRepository pointShopProductRepository;
    private final GifticonInventoryRepository gifticonInventoryRepository;
    private final PointShopOrderRepository pointShopOrderRepository;
    private final MatchingRoomMemberRepository matchingRoomMemberRepository;
    private final FriendRelationRepository friendRelationRepository;
    private final UserAccountDeletionService userAccountDeletionService;
    private final UserRepository userRepository;
    private final CompetitionService competitionService;
    private final EducationContentService educationContentService;
    private final MatchingRoomService matchingRoomService;

    public AdminConsoleController(
            ManagedEtfRepository managedEtfRepository,
            ManagedNewsArticleRepository managedNewsArticleRepository,
            ManagedCommunityPostRepository managedCommunityPostRepository,
            ManagedCommunityCommentRepository managedCommunityCommentRepository,
            ManagedGroupInsightRepository managedGroupInsightRepository,
            PointWalletRepository pointWalletRepository,
            PointTransactionRepository pointTransactionRepository,
            PointShopProductRepository pointShopProductRepository,
            GifticonInventoryRepository gifticonInventoryRepository,
            PointShopOrderRepository pointShopOrderRepository,
            MatchingRoomMemberRepository matchingRoomMemberRepository,
            FriendRelationRepository friendRelationRepository,
            UserAccountDeletionService userAccountDeletionService,
            UserRepository userRepository,
            CompetitionService competitionService,
            EducationContentService educationContentService,
            MatchingRoomService matchingRoomService
    ) {
        this.managedEtfRepository = managedEtfRepository;
        this.managedNewsArticleRepository = managedNewsArticleRepository;
        this.managedCommunityPostRepository = managedCommunityPostRepository;
        this.managedCommunityCommentRepository = managedCommunityCommentRepository;
        this.managedGroupInsightRepository = managedGroupInsightRepository;
        this.pointWalletRepository = pointWalletRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.pointShopProductRepository = pointShopProductRepository;
        this.gifticonInventoryRepository = gifticonInventoryRepository;
        this.pointShopOrderRepository = pointShopOrderRepository;
        this.matchingRoomMemberRepository = matchingRoomMemberRepository;
        this.friendRelationRepository = friendRelationRepository;
        this.userAccountDeletionService = userAccountDeletionService;
        this.userRepository = userRepository;
        this.competitionService = competitionService;
        this.educationContentService = educationContentService;
        this.matchingRoomService = matchingRoomService;
    }

    @GetMapping("/bootstrap")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getBootstrap() {
        return ResponseEntity.ok(Map.of(
                "counts", Map.of(
                        "etfs", managedEtfRepository.count(),
                        "news", managedNewsArticleRepository.count(),
                        "communityPosts", managedCommunityPostRepository.count(),
                        "products", pointShopProductRepository.count(),
                        "users", userRepository.count()
                )
        ));
    }

    @GetMapping("/competitions")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getCompetitions() {
        return ResponseEntity.ok(competitionService.findAll().stream().map(competitionService::toMap).toList());
    }

    @PostMapping("/competitions")
    public ResponseEntity<Map<String, Object>> createCompetition(@RequestBody Map<String, String> body) {
        String name = body != null && body.containsKey("name") ? body.get("name") : "새 토너먼트";
        String startDate = body != null && body.containsKey("startDate") ? body.get("startDate") : "2025-03-01T00:00:00";
        String endDate = body != null && body.containsKey("endDate") ? body.get("endDate") : "2025-03-31T23:59:59";
        Competition created = competitionService.create(name, startDate, endDate);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Created",
                "competition", competitionService.toMap(created)
        ));
    }

    @GetMapping("/etfs")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getEtfs() {
        return ResponseEntity.ok(managedEtfRepository.findAll().stream().map(this::toEtfMap).toList());
    }

    @PostMapping("/etfs")
    public ResponseEntity<Map<String, Object>> createEtf(@RequestBody Map<String, Object> body) {
        ManagedEtf etf = ManagedEtf.builder().build();
        applyEtf(etf, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(toEtfMap(managedEtfRepository.save(etf)));
    }

    @PutMapping("/etfs/{id}")
    public ResponseEntity<Map<String, Object>> updateEtf(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ManagedEtf etf = managedEtfRepository.findById(id)
                .orElseThrow(() -> new ApiException("ETF not found", HttpStatus.NOT_FOUND));
        applyEtf(etf, body);
        return ResponseEntity.ok(toEtfMap(managedEtfRepository.save(etf)));
    }

    @DeleteMapping("/etfs/{id}")
    public ResponseEntity<Map<String, Object>> deleteEtf(@PathVariable Long id) {
        managedEtfRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/news")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getNews() {
        return ResponseEntity.ok(managedNewsArticleRepository.findAll().stream().map(this::toNewsMap).toList());
    }

    @PostMapping("/news")
    public ResponseEntity<Map<String, Object>> createNews(@RequestBody Map<String, Object> body) {
        ManagedNewsArticle article = ManagedNewsArticle.builder().build();
        applyNews(article, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(toNewsMap(managedNewsArticleRepository.save(article)));
    }

    @PutMapping("/news/{id}")
    public ResponseEntity<Map<String, Object>> updateNews(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ManagedNewsArticle article = managedNewsArticleRepository.findById(id)
                .orElseThrow(() -> new ApiException("News article not found", HttpStatus.NOT_FOUND));
        applyNews(article, body);
        return ResponseEntity.ok(toNewsMap(managedNewsArticleRepository.save(article)));
    }

    @DeleteMapping("/news/{id}")
    public ResponseEntity<Map<String, Object>> deleteNews(@PathVariable Long id) {
        managedNewsArticleRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/community/posts")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getCommunityPosts() {
        return ResponseEntity.ok(managedCommunityPostRepository.findAll().stream().map(this::toCommunityPostMap).toList());
    }

    @PostMapping("/community/posts")
    public ResponseEntity<Map<String, Object>> createCommunityPost(@RequestBody Map<String, Object> body) {
        ManagedCommunityPost post = ManagedCommunityPost.builder().likeCount(0).build();
        applyCommunityPost(post, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(toCommunityPostMap(managedCommunityPostRepository.save(post)));
    }

    @PutMapping("/community/posts/{id}")
    public ResponseEntity<Map<String, Object>> updateCommunityPost(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ManagedCommunityPost post = managedCommunityPostRepository.findById(id)
                .orElseThrow(() -> new ApiException("Community post not found", HttpStatus.NOT_FOUND));
        applyCommunityPost(post, body);
        return ResponseEntity.ok(toCommunityPostMap(managedCommunityPostRepository.save(post)));
    }

    @DeleteMapping("/community/posts/{id}")
    public ResponseEntity<Map<String, Object>> deleteCommunityPost(@PathVariable Long id) {
        managedCommunityPostRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/community/posts/{postId}/comments")
    public ResponseEntity<Map<String, Object>> createCommunityComment(@PathVariable Long postId, @RequestBody Map<String, Object> body) {
        ManagedCommunityPost post = managedCommunityPostRepository.findById(postId)
                .orElseThrow(() -> new ApiException("Community post not found", HttpStatus.NOT_FOUND));
        ManagedCommunityComment comment = ManagedCommunityComment.builder().post(post).build();
        applyCommunityComment(comment, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(toCommunityCommentMap(managedCommunityCommentRepository.save(comment)));
    }

    @PutMapping("/community/comments/{id}")
    public ResponseEntity<Map<String, Object>> updateCommunityComment(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ManagedCommunityComment comment = managedCommunityCommentRepository.findById(id)
                .orElseThrow(() -> new ApiException("Community comment not found", HttpStatus.NOT_FOUND));
        applyCommunityComment(comment, body);
        return ResponseEntity.ok(toCommunityCommentMap(managedCommunityCommentRepository.save(comment)));
    }

    @DeleteMapping("/community/comments/{id}")
    public ResponseEntity<Map<String, Object>> deleteCommunityComment(@PathVariable Long id) {
        managedCommunityCommentRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/group-insights/home")
    @Transactional
    public ResponseEntity<Map<String, Object>> getHomeGroupInsight() {
        return ResponseEntity.ok(toGroupInsightMap(getOrCreateGroupInsight()));
    }

    @GetMapping("/matching-rooms")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getMatchingRooms() {
        return ResponseEntity.ok(matchingRoomService.list(null));
    }

    @DeleteMapping("/matching-rooms/{roomId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteMatchingRoom(@PathVariable String roomId) {
        return ResponseEntity.ok(matchingRoomService.deleteRoomByAdmin(roomId));
    }

    @DeleteMapping("/matching-rooms/{roomId}/members/{userId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> removeMatchingRoomMember(
            @PathVariable String roomId,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(matchingRoomService.removeMemberByAdmin(roomId, userId));
    }

    @PutMapping("/group-insights/home")
    public ResponseEntity<Map<String, Object>> updateHomeGroupInsight(@RequestBody Map<String, Object> body) {
        ManagedGroupInsight insight = getOrCreateGroupInsight();
        insight.setTopGroupId(getLong(body, "topGroupId"));
        insight.setTopGroupName(getString(body, "topGroupName", insight.getTopGroupName()));
        insight.setDailyReturnRate(getBigDecimal(body, "dailyReturnRate"));
        insight.setTopPick(getString(body, "topPick", insight.getTopPick()));
        insight.setComment(getString(body, "comment", insight.getComment()));
        insight.setConsensusJson(getString(body, "consensusJson", insight.getConsensusJson()));
        return ResponseEntity.ok(toGroupInsightMap(managedGroupInsightRepository.save(insight)));
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getUsers() {
        return ResponseEntity.ok(userRepository.findAll().stream().map(this::toUserMap).toList());
    }

    @PatchMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        User user = userRepository.findById(id).orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
        user.setStudentId(getString(body, "studentId", user.getStudentId()));
        user.setNickname(getString(body, "nickname", user.getNickname()));
        user.setEmail(getString(body, "email", user.getEmail()));
        user.setPhoneNumber(getString(body, "phoneNumber", user.getPhoneNumber()));
        user.setProfileImageUrl(getString(body, "profileImageUrl", user.getProfileImageUrl()));
        user.setTeamId(getString(body, "teamId", user.getTeamId()));
        user.setRole(getString(body, "role", user.getRole()));
        user.setInvestmentProfileResult(getString(body, "investmentProfileResult", user.getInvestmentProfileResult()));
        setOptionalUserStringField(user, "InvestmentLevel", getString(body, "investmentLevel", getOptionalUserStringField(user, "InvestmentLevel")));
        setOptionalUserStringField(user, "InterestSector", getString(body, "interestSector", getOptionalUserStringField(user, "InterestSector")));
        user.setTotalAssets(getBigDecimal(body, "totalAssets"));
        user.setInvestmentAmount(getBigDecimal(body, "investmentAmount"));
        user.setProfitLoss(getBigDecimal(body, "profitLoss"));
        user.setProfitLossRate(getBigDecimal(body, "profitLossRate"));
        return ResponseEntity.ok(toUserMap(userRepository.save(user)));
    }

    @Transactional
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> deleteUserByAdminConsole(@PathVariable Long id) {
        userAccountDeletionService.deleteUserById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/point-shop/products")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getPointShopProducts() {
        return ResponseEntity.ok(pointShopProductRepository.findAll().stream().map(this::toProductMap).toList());
    }

    @PostMapping("/point-shop/products")
    public ResponseEntity<Map<String, Object>> createPointShopProduct(@RequestBody Map<String, Object> body) {
        PointShopProduct product = PointShopProduct.builder().build();
        applyProduct(product, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(toProductMap(pointShopProductRepository.save(product)));
    }

    @PutMapping("/point-shop/products/{id}")
    public ResponseEntity<Map<String, Object>> updatePointShopProduct(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        PointShopProduct product = pointShopProductRepository.findById(id)
                .orElseThrow(() -> new ApiException("Point shop product not found", HttpStatus.NOT_FOUND));
        applyProduct(product, body);
        return ResponseEntity.ok(toProductMap(pointShopProductRepository.save(product)));
    }

    @DeleteMapping("/point-shop/products/{id}")
    public ResponseEntity<Map<String, Object>> deletePointShopProduct(@PathVariable Long id) {
        pointShopProductRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/point-shop/inventory")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getGifticonInventory() {
        return ResponseEntity.ok(gifticonInventoryRepository.findAll().stream().map(this::toInventoryMap).toList());
    }

    @PostMapping("/point-shop/inventory")
    public ResponseEntity<Map<String, Object>> createGifticonInventory(@RequestBody Map<String, Object> body) {
        GifticonInventory inventory = GifticonInventory.builder().build();
        applyInventory(inventory, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(toInventoryMap(gifticonInventoryRepository.save(inventory)));
    }

    @PutMapping("/point-shop/inventory/{id}")
    public ResponseEntity<Map<String, Object>> updateGifticonInventory(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        GifticonInventory inventory = gifticonInventoryRepository.findById(id)
                .orElseThrow(() -> new ApiException("Gifticon inventory not found", HttpStatus.NOT_FOUND));
        applyInventory(inventory, body);
        return ResponseEntity.ok(toInventoryMap(gifticonInventoryRepository.save(inventory)));
    }

    @DeleteMapping("/point-shop/inventory/{id}")
    public ResponseEntity<Map<String, Object>> deleteGifticonInventory(@PathVariable Long id) {
        gifticonInventoryRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/point-shop/wallets")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getPointWallets() {
        return ResponseEntity.ok(pointWalletRepository.findAllByOrderByUpdatedAtDesc().stream().map(this::toWalletMap).toList());
    }

    @PutMapping("/point-shop/wallets/{userId}")
    public ResponseEntity<Map<String, Object>> updatePointWallet(@PathVariable Long userId, @RequestBody Map<String, Object> body) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
        PointWallet wallet = pointWalletRepository.findByUser_Id(userId)
                .orElse(PointWallet.builder().user(user).balance(0).build());
        Integer newBalance = getInteger(body, "balance", wallet.getBalance());
        wallet.setBalance(newBalance);
        PointWallet saved = pointWalletRepository.save(wallet);
        PointTransaction transaction = PointTransaction.builder()
                .user(user)
                .type("ADJUST")
                .amount(newBalance)
                .balanceAfter(newBalance)
                .sourceType("ADMIN")
                .sourceId(String.valueOf(userId))
                .description(getString(body, "description", "Admin wallet adjustment"))
                .build();
        pointTransactionRepository.save(transaction);
        return ResponseEntity.ok(toWalletMap(saved));
    }

    @GetMapping("/point-shop/orders")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getPointShopOrders() {
        return ResponseEntity.ok(pointShopOrderRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toOrderMap).toList());
    }

    @PostMapping("/point-shop/orders")
    public ResponseEntity<Map<String, Object>> createPointShopOrder(@RequestBody Map<String, Object> body) {
        PointShopOrder order = PointShopOrder.builder().build();
        applyOrder(order, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(toOrderMap(pointShopOrderRepository.save(order)));
    }

    @PutMapping("/point-shop/orders/{id}")
    public ResponseEntity<Map<String, Object>> updatePointShopOrder(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        PointShopOrder order = pointShopOrderRepository.findById(id)
                .orElseThrow(() -> new ApiException("Point shop order not found", HttpStatus.NOT_FOUND));
        applyOrder(order, body);
        return ResponseEntity.ok(toOrderMap(pointShopOrderRepository.save(order)));
    }

    @DeleteMapping("/point-shop/orders/{id}")
    public ResponseEntity<Map<String, Object>> deletePointShopOrder(@PathVariable Long id) {
        pointShopOrderRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/friends")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getFriendRelations() {
        return ResponseEntity.ok(friendRelationRepository.findAllByOrderByUpdatedAtDesc().stream().map(this::toFriendMap).toList());
    }

    @GetMapping("/education/catalog")
    @Transactional(readOnly = true)
    public ResponseEntity<EducationCatalogResponseDTO> getEducationCatalog() {
        return ResponseEntity.ok(educationContentService.getCatalog());
    }

    @GetMapping("/education/days/{track}/{day}")
    @Transactional(readOnly = true)
    public ResponseEntity<EducationDayContentResponseDTO> getEducationDayContent(
            @PathVariable String track,
            @PathVariable Integer day,
            @RequestParam(value = "sector", required = false) String sector
    ) {
        return ResponseEntity.ok(educationContentService.getDayContent(track, day, sector));
    }

    @PostMapping("/friends")
    public ResponseEntity<Map<String, Object>> createFriendRelation(@RequestBody Map<String, Object> body) {
        FriendRelation relation = FriendRelation.builder().build();
        applyFriendRelation(relation, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(toFriendMap(friendRelationRepository.save(relation)));
    }

    @PutMapping("/friends/{id}")
    public ResponseEntity<Map<String, Object>> updateFriendRelation(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        FriendRelation relation = friendRelationRepository.findById(id)
                .orElseThrow(() -> new ApiException("Friend relation not found", HttpStatus.NOT_FOUND));
        applyFriendRelation(relation, body);
        return ResponseEntity.ok(toFriendMap(friendRelationRepository.save(relation)));
    }

    @DeleteMapping("/friends/{id}")
    public ResponseEntity<Map<String, Object>> deleteFriendRelation(@PathVariable Long id) {
        friendRelationRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private void applyEtf(ManagedEtf etf, Map<String, Object> body) {
        etf.setEtfCode(requiredString(body, "etfCode"));
        etf.setTitle(requiredString(body, "title"));
        etf.setTheme(getString(body, "theme", etf.getTheme()));
        etf.setBenchmark(getString(body, "benchmark", etf.getBenchmark()));
        etf.setPeriod(getString(body, "period", etf.getPeriod()));
        etf.setRiskLevel(getString(body, "riskLevel", etf.getRiskLevel()));
        etf.setReturnRate(getBigDecimal(body, "returnRate"));
        etf.setPopularityScore(getInteger(body, "popularityScore", etf.getPopularityScore()));
        etf.setFavoriteCount(getInteger(body, "favoriteCount", etf.getFavoriteCount()));
        etf.setImageUrl(getString(body, "imageUrl", etf.getImageUrl()));
        etf.setShortDescription(getString(body, "shortDescription", etf.getShortDescription()));
        etf.setHoldingsJson(getString(body, "holdingsJson", etf.getHoldingsJson()));
        etf.setTrendPointsJson(getString(body, "trendPointsJson", etf.getTrendPointsJson()));
        etf.setAnalysisSummaryJson(getString(body, "analysisSummaryJson", etf.getAnalysisSummaryJson()));
        etf.setPublishedAt(getLocalDateTime(body, "publishedAt"));
    }

    private void applyNews(ManagedNewsArticle article, Map<String, Object> body) {
        article.setNewsKey(requiredString(body, "newsKey"));
        article.setTitle(requiredString(body, "title"));
        article.setSourceLabel(getString(body, "sourceLabel", article.getSourceLabel()));
        article.setImageUrl(getString(body, "imageUrl", article.getImageUrl()));
        article.setStockCode(getString(body, "stockCode", article.getStockCode()));
        article.setStockName(getString(body, "stockName", article.getStockName()));
        article.setSummary(getString(body, "summary", article.getSummary()));
        article.setContent(getString(body, "content", article.getContent()));
        article.setCompanyInfoJson(getString(body, "companyInfoJson", article.getCompanyInfoJson()));
        article.setTagsJson(getString(body, "tagsJson", article.getTagsJson()));
        article.setOpinionsJson(getString(body, "opinionsJson", article.getOpinionsJson()));
        article.setPublishedAt(getLocalDateTime(body, "publishedAt"));
    }

    private void applyCommunityPost(ManagedCommunityPost post, Map<String, Object> body) {
        post.setType(requiredString(body, "type"));
        post.setAuthorName(requiredString(body, "authorName"));
        post.setAuthorUserId(getLong(body, "authorUserId"));
        post.setAuthorProfileImageUrl(getString(body, "authorProfileImageUrl", post.getAuthorProfileImageUrl()));
        post.setStockCode(getString(body, "stockCode", post.getStockCode()));
        post.setStockName(getString(body, "stockName", post.getStockName()));
        post.setSentiment(getString(body, "sentiment", post.getSentiment()));
        post.setTitle(requiredString(body, "title"));
        post.setContent(requiredString(body, "content"));
        post.setAnalysisReportId(getString(body, "analysisReportId", post.getAnalysisReportId()));
        post.setLikeCount(getInteger(body, "likeCount", post.getLikeCount() == null ? 0 : post.getLikeCount()));
    }

    private void applyCommunityComment(ManagedCommunityComment comment, Map<String, Object> body) {
        comment.setAuthorName(requiredString(body, "authorName"));
        comment.setAuthorUserId(getLong(body, "authorUserId"));
        comment.setAuthorProfileImageUrl(getString(body, "authorProfileImageUrl", comment.getAuthorProfileImageUrl()));
        comment.setContent(requiredString(body, "content"));
    }

    private void applyProduct(PointShopProduct product, Map<String, Object> body) {
        product.setName(requiredString(body, "name"));
        product.setBrand(getString(body, "brand", product.getBrand()));
        product.setCategory(getString(body, "category", product.getCategory()));
        product.setPricePoint(getInteger(body, "pricePoint", product.getPricePoint()));
        product.setImageUrl(getString(body, "imageUrl", product.getImageUrl()));
        product.setDescription(getString(body, "description", product.getDescription()));
        product.setNotice(getString(body, "notice", product.getNotice()));
        product.setStatus(getString(body, "status", product.getStatus()));
        product.setStockCount(getInteger(body, "stockCount", product.getStockCount()));
        product.setSortOrder(getInteger(body, "sortOrder", product.getSortOrder()));
    }

    private void applyInventory(GifticonInventory inventory, Map<String, Object> body) {
        Long productId = getLong(body, "productId");
        PointShopProduct product = pointShopProductRepository.findById(productId)
                .orElseThrow(() -> new ApiException("Point shop product not found", HttpStatus.NOT_FOUND));
        inventory.setProduct(product);
        inventory.setGifticonCode(requiredString(body, "gifticonCode"));
        inventory.setGifticonUrl(getString(body, "gifticonUrl", inventory.getGifticonUrl()));
        inventory.setExpiredAt(getLocalDateTime(body, "expiredAt"));
        inventory.setStatus(getString(body, "status", inventory.getStatus()));
        inventory.setAssignedOrderId(getString(body, "assignedOrderId", inventory.getAssignedOrderId()));
    }

    private void applyOrder(PointShopOrder order, Map<String, Object> body) {
        Long userId = getLong(body, "userId");
        Long productId = getLong(body, "productId");
        User user = userRepository.findById(userId).orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
        PointShopProduct product = pointShopProductRepository.findById(productId)
                .orElseThrow(() -> new ApiException("Point shop product not found", HttpStatus.NOT_FOUND));
        order.setUser(user);
        order.setProduct(product);
        Long inventoryId = getLong(body, "inventoryId");
        if (inventoryId != null) {
            GifticonInventory inventory = gifticonInventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new ApiException("Gifticon inventory not found", HttpStatus.NOT_FOUND));
            order.setInventory(inventory);
        } else {
            order.setInventory(null);
        }
        order.setUsedPoint(getInteger(body, "usedPoint", order.getUsedPoint()));
        order.setStatus(getString(body, "status", order.getStatus()));
        order.setPointTransactionId(getString(body, "pointTransactionId", order.getPointTransactionId()));
        order.setFailureReason(getString(body, "failureReason", order.getFailureReason()));
        order.setSentAt(getLocalDateTime(body, "sentAt"));
    }

    private void applyFriendRelation(FriendRelation relation, Map<String, Object> body) {
        Long requesterUserId = getLong(body, "requesterUserId");
        Long addresseeUserId = getLong(body, "addresseeUserId");
        User requester = userRepository.findById(requesterUserId)
                .orElseThrow(() -> new ApiException("Requester user not found", HttpStatus.NOT_FOUND));
        User addressee = userRepository.findById(addresseeUserId)
                .orElseThrow(() -> new ApiException("Addressee user not found", HttpStatus.NOT_FOUND));
        relation.setRequesterUser(requester);
        relation.setAddresseeUser(addressee);
        relation.setStatus(getString(body, "status", relation.getStatus()));
    }

    private ManagedGroupInsight getOrCreateGroupInsight() {
        return managedGroupInsightRepository.findByInsightKey(GROUP_INSIGHT_KEY)
                .orElseGet(() -> managedGroupInsightRepository.save(
                        ManagedGroupInsight.builder()
                                .insightKey(GROUP_INSIGHT_KEY)
                                .topGroupName("Top Group")
                                .topPick("NVDA")
                                .comment("")
                                .consensusJson("[]")
                                .build()
                ));
    }

    private Map<String, Object> toEtfMap(ManagedEtf etf) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", etf.getId());
        map.put("etfCode", etf.getEtfCode());
        map.put("title", etf.getTitle());
        map.put("theme", etf.getTheme());
        map.put("benchmark", etf.getBenchmark());
        map.put("period", etf.getPeriod());
        map.put("riskLevel", etf.getRiskLevel());
        map.put("returnRate", etf.getReturnRate());
        map.put("popularityScore", etf.getPopularityScore());
        map.put("favoriteCount", etf.getFavoriteCount());
        map.put("imageUrl", etf.getImageUrl());
        map.put("shortDescription", etf.getShortDescription());
        map.put("holdingsJson", etf.getHoldingsJson());
        map.put("trendPointsJson", etf.getTrendPointsJson());
        map.put("analysisSummaryJson", etf.getAnalysisSummaryJson());
        map.put("publishedAt", etf.getPublishedAt());
        map.put("createdAt", etf.getCreatedAt());
        map.put("updatedAt", etf.getUpdatedAt());
        return map;
    }

    private Map<String, Object> toNewsMap(ManagedNewsArticle article) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", article.getId());
        map.put("newsKey", article.getNewsKey());
        map.put("title", article.getTitle());
        map.put("sourceLabel", article.getSourceLabel());
        map.put("imageUrl", article.getImageUrl());
        map.put("stockCode", article.getStockCode());
        map.put("stockName", article.getStockName());
        map.put("summary", article.getSummary());
        map.put("content", article.getContent());
        map.put("companyInfoJson", article.getCompanyInfoJson());
        map.put("tagsJson", article.getTagsJson());
        map.put("opinionsJson", article.getOpinionsJson());
        map.put("publishedAt", article.getPublishedAt());
        map.put("createdAt", article.getCreatedAt());
        map.put("updatedAt", article.getUpdatedAt());
        return map;
    }

    private Map<String, Object> toCommunityPostMap(ManagedCommunityPost post) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", post.getId());
        map.put("type", post.getType());
        map.put("authorName", post.getAuthorName());
        map.put("authorUserId", post.getAuthorUserId());
        map.put("authorProfileImageUrl", post.getAuthorProfileImageUrl());
        map.put("stockCode", post.getStockCode());
        map.put("stockName", post.getStockName());
        map.put("sentiment", post.getSentiment());
        map.put("title", post.getTitle());
        map.put("content", post.getContent());
        map.put("analysisReportId", post.getAnalysisReportId());
        map.put("likeCount", post.getLikeCount());
        map.put("comments", managedCommunityCommentRepository.findByPost_IdOrderByCreatedAtAsc(post.getId()).stream().map(this::toCommunityCommentMap).toList());
        map.put("createdAt", post.getCreatedAt());
        map.put("updatedAt", post.getUpdatedAt());
        return map;
    }

    private Map<String, Object> toCommunityCommentMap(ManagedCommunityComment comment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", comment.getId());
        map.put("postId", comment.getPost().getId());
        map.put("authorName", comment.getAuthorName());
        map.put("authorUserId", comment.getAuthorUserId());
        map.put("authorProfileImageUrl", comment.getAuthorProfileImageUrl());
        map.put("content", comment.getContent());
        map.put("createdAt", comment.getCreatedAt());
        map.put("updatedAt", comment.getUpdatedAt());
        return map;
    }

    private Map<String, Object> toGroupInsightMap(ManagedGroupInsight insight) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", insight.getId());
        map.put("insightKey", insight.getInsightKey());
        map.put("topGroupId", insight.getTopGroupId());
        map.put("topGroupName", insight.getTopGroupName());
        map.put("dailyReturnRate", insight.getDailyReturnRate());
        map.put("topPick", insight.getTopPick());
        map.put("comment", insight.getComment());
        map.put("consensusJson", insight.getConsensusJson());
        map.put("createdAt", insight.getCreatedAt());
        map.put("updatedAt", insight.getUpdatedAt());
        return map;
    }

    private Map<String, Object> toUserMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        PointWallet wallet = user.getId() != null ? pointWalletRepository.findByUser_Id(user.getId()).orElse(null) : null;
        map.put("id", user.getId());
        map.put("studentId", user.getStudentId());
        map.put("nickname", user.getNickname());
        map.put("email", user.getEmail());
        map.put("phoneNumber", user.getPhoneNumber());
        map.put("profileImageUrl", user.getProfileImageUrl());
        map.put("teamId", user.getTeamId());
        map.put("role", user.getRole());
        map.put("investmentProfileResult", user.getInvestmentProfileResult());
        map.put("investmentLevel", getOptionalUserStringField(user, "InvestmentLevel"));
        map.put("interestSector", getOptionalUserStringField(user, "InterestSector"));
        map.put("totalAssets", user.getTotalAssets());
        map.put("investmentAmount", user.getInvestmentAmount());
        map.put("profitLoss", user.getProfitLoss());
        map.put("profitLossRate", user.getProfitLossRate());
        map.put("pointBalance", wallet != null ? wallet.getBalance() : 0);
        return map;
    }

    private Map<String, Object> toProductMap(PointShopProduct product) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", product.getId());
        map.put("name", product.getName());
        map.put("brand", product.getBrand());
        map.put("category", product.getCategory());
        map.put("pricePoint", product.getPricePoint());
        map.put("imageUrl", product.getImageUrl());
        map.put("description", product.getDescription());
        map.put("notice", product.getNotice());
        map.put("status", product.getStatus());
        map.put("stockCount", product.getStockCount());
        map.put("sortOrder", product.getSortOrder());
        map.put("createdAt", product.getCreatedAt());
        map.put("updatedAt", product.getUpdatedAt());
        return map;
    }

    private Map<String, Object> toInventoryMap(GifticonInventory inventory) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", inventory.getId());
        map.put("productId", inventory.getProduct() != null ? inventory.getProduct().getId() : null);
        map.put("productName", inventory.getProduct() != null ? inventory.getProduct().getName() : null);
        map.put("gifticonCode", inventory.getGifticonCode());
        map.put("gifticonUrl", inventory.getGifticonUrl());
        map.put("expiredAt", inventory.getExpiredAt());
        map.put("status", inventory.getStatus());
        map.put("assignedOrderId", inventory.getAssignedOrderId());
        map.put("createdAt", inventory.getCreatedAt());
        map.put("updatedAt", inventory.getUpdatedAt());
        return map;
    }

    private Map<String, Object> toWalletMap(PointWallet wallet) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", wallet.getId());
        map.put("userId", wallet.getUser() != null ? wallet.getUser().getId() : null);
        map.put("nickname", wallet.getUser() != null ? wallet.getUser().getNickname() : null);
        map.put("studentId", wallet.getUser() != null ? wallet.getUser().getStudentId() : null);
        map.put("balance", wallet.getBalance());
        map.put("createdAt", wallet.getCreatedAt());
        map.put("updatedAt", wallet.getUpdatedAt());
        return map;
    }

    private Map<String, Object> toOrderMap(PointShopOrder order) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", order.getId());
        map.put("userId", order.getUser() != null ? order.getUser().getId() : null);
        map.put("nickname", order.getUser() != null ? order.getUser().getNickname() : null);
        map.put("productId", order.getProduct() != null ? order.getProduct().getId() : null);
        map.put("productName", order.getProduct() != null ? order.getProduct().getName() : null);
        map.put("inventoryId", order.getInventory() != null ? order.getInventory().getId() : null);
        map.put("usedPoint", order.getUsedPoint());
        map.put("status", order.getStatus());
        map.put("pointTransactionId", order.getPointTransactionId());
        map.put("failureReason", order.getFailureReason());
        map.put("sentAt", order.getSentAt());
        map.put("createdAt", order.getCreatedAt());
        map.put("updatedAt", order.getUpdatedAt());
        return map;
    }

    private Map<String, Object> toFriendMap(FriendRelation relation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", relation.getId());
        map.put("requesterUserId", relation.getRequesterUser() != null ? relation.getRequesterUser().getId() : null);
        map.put("requesterNickname", relation.getRequesterUser() != null ? relation.getRequesterUser().getNickname() : null);
        map.put("addresseeUserId", relation.getAddresseeUser() != null ? relation.getAddresseeUser().getId() : null);
        map.put("addresseeNickname", relation.getAddresseeUser() != null ? relation.getAddresseeUser().getNickname() : null);
        map.put("status", relation.getStatus());
        map.put("createdAt", relation.getCreatedAt());
        map.put("updatedAt", relation.getUpdatedAt());
        return map;
    }

    private static String requiredString(Map<String, Object> body, String key) {
        String value = getString(body, key, null);
        if (value == null || value.isBlank()) {
            throw new ApiException(key + " is required", HttpStatus.BAD_REQUEST);
        }
        return value.trim();
    }

    private static String getString(Map<String, Object> body, String key, String defaultValue) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(body.get(key));
    }

    private static Integer getInteger(Map<String, Object> body, String key, Integer defaultValue) {
        if (body == null || !body.containsKey(key) || body.get(key) == null || String.valueOf(body.get(key)).isBlank()) {
            return defaultValue;
        }
        Object value = body.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new ApiException(key + " must be a number", HttpStatus.BAD_REQUEST);
        }
    }

    private static Long getLong(Map<String, Object> body, String key) {
        if (body == null || !body.containsKey(key) || body.get(key) == null || String.valueOf(body.get(key)).isBlank()) {
            return null;
        }
        Object value = body.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new ApiException(key + " must be a number", HttpStatus.BAD_REQUEST);
        }
    }

    private static BigDecimal getBigDecimal(Map<String, Object> body, String key) {
        if (body == null || !body.containsKey(key) || body.get(key) == null || String.valueOf(body.get(key)).isBlank()) {
            return null;
        }
        Object value = body.get(key);
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new ApiException(key + " must be a decimal number", HttpStatus.BAD_REQUEST);
        }
    }

    private static String getOptionalUserStringField(User user, String suffix) {
        try {
            Method getter = User.class.getMethod("get" + suffix);
            Object value = getter.invoke(user);
            return value != null ? String.valueOf(value) : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static void setOptionalUserStringField(User user, String suffix, String value) {
        try {
            Method setter = User.class.getMethod("set" + suffix, String.class);
            setter.invoke(user, value);
        } catch (ReflectiveOperationException ignored) {
            // Older deployments may not have this optional field yet.
        }
    }

    private static LocalDateTime getLocalDateTime(Map<String, Object> body, String key) {
        if (body == null || !body.containsKey(key) || body.get(key) == null || String.valueOf(body.get(key)).isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(String.valueOf(body.get(key)).trim());
        } catch (DateTimeParseException ex) {
            throw new ApiException(key + " must be an ISO local datetime string", HttpStatus.BAD_REQUEST);
        }
    }
}
