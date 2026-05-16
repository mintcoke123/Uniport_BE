package com.uniport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.dto.FriendListItemDTO;
import com.uniport.dto.FriendListResponseDTO;
import com.uniport.dto.FriendRequestDecisionDTO;
import com.uniport.dto.FriendRankingItemDTO;
import com.uniport.dto.FriendRankingSectionDTO;
import com.uniport.dto.FriendRequestCreateDTO;
import com.uniport.dto.FriendRequestListItemDTO;
import com.uniport.dto.FriendRequestListResponseDTO;
import com.uniport.dto.FriendRequestResponseDTO;
import com.uniport.dto.FriendsDashboardResponseDTO;
import com.uniport.dto.MyPageAssetSummaryDTO;
import com.uniport.dto.MyPageBadgeDTO;
import com.uniport.dto.MyPageCharacterCardDTO;
import com.uniport.dto.MyPageCharacterSelectRequestDTO;
import com.uniport.dto.MyPageExpDTO;
import com.uniport.dto.MyPageHistoryItemDTO;
import com.uniport.dto.MyPageNoteDTO;
import com.uniport.dto.MyPageProfileUpdateRequestDTO;
import com.uniport.dto.MyPageResponseDTO;
import com.uniport.dto.MyPageSettingsDTO;
import com.uniport.dto.MyPageSettingsUpdateRequestDTO;
import com.uniport.dto.MyPageSummaryDTO;
import com.uniport.dto.MyPageTitleDTO;
import com.uniport.dto.MyPageUserDTO;
import com.uniport.dto.PointBalanceResponseDTO;
import com.uniport.dto.PointShopOrderDetailResponseDTO;
import com.uniport.dto.PointShopOrderListItemDTO;
import com.uniport.dto.PointShopOrderResponseDTO;
import com.uniport.dto.PointShopOrdersResponseDTO;
import com.uniport.dto.PointShopProductDTO;
import com.uniport.dto.PointShopProductDetailResponseDTO;
import com.uniport.dto.PointShopProductsResponseDTO;
import com.uniport.dto.ShopItemDTO;
import com.uniport.dto.ShopItemsResponseDTO;
import com.uniport.dto.ShopPreviewPointDTO;
import com.uniport.dto.ShopRedemptionDetailResponseDTO;
import com.uniport.dto.ShopRedemptionListItemDTO;
import com.uniport.dto.ShopRedemptionListResponseDTO;
import com.uniport.dto.ShopRedemptionPreviewResponseDTO;
import com.uniport.dto.ShopRedemptionRequestDTO;
import com.uniport.dto.ShopRedemptionResponseDTO;
import com.uniport.entity.FriendRelation;
import com.uniport.entity.GifticonInventory;
import com.uniport.entity.Holding;
import com.uniport.entity.LearningUserStateEntity;
import com.uniport.entity.Order;
import com.uniport.entity.PointShopOrder;
import com.uniport.entity.PointShopProduct;
import com.uniport.entity.PointTransaction;
import com.uniport.entity.PointWallet;
import com.uniport.entity.User;
import com.uniport.entity.UserMyPagePreference;
import com.uniport.exception.ApiException;
import com.uniport.repository.FriendRelationRepository;
import com.uniport.repository.GifticonInventoryRepository;
import com.uniport.repository.HoldingRepository;
import com.uniport.repository.LearningUserStateRepository;
import com.uniport.repository.OrderRepository;
import com.uniport.repository.PointShopOrderRepository;
import com.uniport.repository.PointShopProductRepository;
import com.uniport.repository.PointTransactionRepository;
import com.uniport.repository.PointWalletRepository;
import com.uniport.repository.UserMyPagePreferenceRepository;
import com.uniport.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class PointSocialDataService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;
    private static final TypeReference<Map<String, Set<Integer>>> COMPLETED_DAYS_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Integer>> EDUCATION_CURRENT_DAY_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Set<Integer>>> EDUCATION_COMPLETED_DAYS_TYPE = new TypeReference<>() {};
    private static final String POINT_SHOP_EXCHANGE_BLOCKED_MESSAGE = "포인트샵 교환은 실제 기프티콘 운영 준비 후 열릴 예정이에요.";

    private final PointWalletRepository pointWalletRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final PointShopProductRepository pointShopProductRepository;
    private final GifticonInventoryRepository gifticonInventoryRepository;
    private final PointShopOrderRepository pointShopOrderRepository;
    private final FriendRelationRepository friendRelationRepository;
    private final UserRepository userRepository;
    private final UserMyPagePreferenceRepository userMyPagePreferenceRepository;
    private final HoldingRepository holdingRepository;
    private final OrderRepository orderRepository;
    private final LearningUserStateRepository learningUserStateRepository;
    private final PushNotificationService pushNotificationService;
    private final PointLedgerService pointLedgerService;

    public PointSocialDataService(PointWalletRepository pointWalletRepository,
                                  PointTransactionRepository pointTransactionRepository,
                                  PointShopProductRepository pointShopProductRepository,
                                  GifticonInventoryRepository gifticonInventoryRepository,
                                  PointShopOrderRepository pointShopOrderRepository,
                                  FriendRelationRepository friendRelationRepository,
                                  UserRepository userRepository,
                                  UserMyPagePreferenceRepository userMyPagePreferenceRepository,
                                  HoldingRepository holdingRepository,
                                  OrderRepository orderRepository,
                                  LearningUserStateRepository learningUserStateRepository,
                                  PushNotificationService pushNotificationService,
                                  PointLedgerService pointLedgerService) {
        this.pointWalletRepository = pointWalletRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.pointShopProductRepository = pointShopProductRepository;
        this.gifticonInventoryRepository = gifticonInventoryRepository;
        this.pointShopOrderRepository = pointShopOrderRepository;
        this.friendRelationRepository = friendRelationRepository;
        this.userRepository = userRepository;
        this.userMyPagePreferenceRepository = userMyPagePreferenceRepository;
        this.holdingRepository = holdingRepository;
        this.orderRepository = orderRepository;
        this.learningUserStateRepository = learningUserStateRepository;
        this.pushNotificationService = pushNotificationService;
        this.pointLedgerService = pointLedgerService;
    }

    public MyPageResponseDTO getMyPage(User user) {
        UserMyPagePreference preference = getOrCreatePreference(user.getId());
        int balance = getBalance(user);
        LearningProgressSnapshot learningProgress = getLearningProgress(user);
        int level = learningProgress.level();
        int currentExp = learningProgress.currentExp();
        int streak = learningProgress.streakDays();
        String selectedCharacterCode = selectedCharacterCode(preference);
        int characterStage = characterStage(level);
        int friendCount = (int) friendRelationRepository.findByRequesterUser_IdOrAddresseeUser_IdOrderByUpdatedAtDesc(user.getId(), user.getId()).stream()
                .filter(relation -> "ACCEPTED".equalsIgnoreCase(relation.getStatus()))
                .count();
        int redemptionCount = pointShopOrderRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).size();
        List<Order> orders = orderRepository.findByUser_IdOrderByOrderDateDesc(user.getId());
        List<Holding> holdings = holdingRepository.findByUser_Id(user.getId());
        LearningUserStateEntity learningState = learningUserStateRepository.findById(user.getId()).orElse(null);

        return MyPageResponseDTO.builder()
                .user(MyPageUserDTO.builder()
                        .nickname(user.getNickname())
                        .profileImageUrl(user.getProfileImageUrl())
                        .level(level)
                        .investmentMbti(defaultString(user.getInvestmentProfileResult(), "균형잡힌 판다형"))
                        .character(resolveSelectedCharacterName(preference))
                        .characterCode(selectedCharacterCode)
                        .characterStage(characterStage)
                        .characterAssetKey(characterAssetKey(selectedCharacterCode, characterStage))
                        .bio(defaultString(preference.getBio(), "나만의 투자 원칙을 기록해 보세요."))
                        .build())
                .exp(MyPageExpDTO.builder()
                        .currentExp(currentExp)
                        .maxExp(learningProgress.maxExp())
                        .totalExp(learningProgress.totalXp())
                        .maxLevel(LearningProgressPolicy.MAX_LEVEL)
                        .build())
                .summary(MyPageSummaryDTO.builder()
                        .learningTimeMinutes(learningProgress.learningTimeMinutes())
                        .currentStreak(streak)
                        .friendCount(friendCount)
                        .redemptionCount(redemptionCount)
                        .build())
                .settings(MyPageSettingsDTO.builder()
                        .pushEnabled(resolvePushEnabled(user, preference))
                        .build())
                .assets(MyPageAssetSummaryDTO.builder()
                        .totalAssets(orZero(user.getTotalAssets()))
                        .investmentAmount(orZero(user.getInvestmentAmount()))
                        .profitLoss(orZero(user.getProfitLoss()))
                        .profitLossRate(orZero(user.getProfitLossRate()))
                        .pointBalance(balance)
                        .build())
                .characters(buildCharacters(preference, level))
                .badges(buildBadges(streak, redemptionCount))
                .note(buildInvestmentNote(user, holdings))
                .investmentHistory(buildInvestmentHistory(orders, holdings))
                .learningHistory(buildLearningHistory(learningState))
                .titles(buildTitles(user, streak, redemptionCount, holdings, orders))
                .build();
    }

    @Transactional
    public MyPageResponseDTO updateMyPageProfile(User user, MyPageProfileUpdateRequestDTO request) {
        User persisted = userRepository.findById(user.getId())
                .orElseThrow(() -> new ApiException("user not found", HttpStatus.NOT_FOUND));
        if (request != null && request.getNickname() != null && !request.getNickname().isBlank()) {
            persisted.setNickname(request.getNickname().trim());
        }
        if (request != null && request.getProfileImageUrl() != null) {
            persisted.setProfileImageUrl(request.getProfileImageUrl().trim());
        }
        userRepository.save(persisted);

        UserMyPagePreference preference = getOrCreatePreference(user.getId());
        if (request != null && request.getBio() != null) {
            preference.setBio(request.getBio().trim());
        }
        userMyPagePreferenceRepository.save(preference);
        return getMyPage(persisted);
    }

    @Transactional
    public MyPageResponseDTO updateMyPageSettings(User user, MyPageSettingsUpdateRequestDTO request) {
        UserMyPagePreference preference = getOrCreatePreference(user.getId());
        if (request != null && request.getPushEnabled() != null) {
            preference.setPushEnabled(request.getPushEnabled());
        }
        userMyPagePreferenceRepository.save(preference);
        return getMyPage(userRepository.findById(user.getId()).orElse(user));
    }

    @Transactional
    public MyPageResponseDTO selectCharacter(User user, MyPageCharacterSelectRequestDTO request) {
        if (request == null || request.getCharacterCode() == null || request.getCharacterCode().isBlank()) {
            throw new ApiException("characterCode is required", HttpStatus.BAD_REQUEST);
        }
        String selected = request.getCharacterCode().trim().toUpperCase(Locale.ROOT);
        boolean valid = buildCharacters(getOrCreatePreference(user.getId())).stream()
                .anyMatch(character -> character.getCode().equalsIgnoreCase(selected));
        if (!valid) {
            throw new ApiException("characterCode is invalid", HttpStatus.BAD_REQUEST);
        }
        UserMyPagePreference preference = getOrCreatePreference(user.getId());
        preference.setSelectedCharacterCode(selected);
        userMyPagePreferenceRepository.save(preference);
        return getMyPage(userRepository.findById(user.getId()).orElse(user));
    }

    public PointBalanceResponseDTO getPointBalance(User user) {
        return PointBalanceResponseDTO.builder().pointBalance(getBalance(user)).build();
    }

    public PointShopProductsResponseDTO getPointShopProducts(User user, String category) {
        String safeCategory = normalizePointShopCategory(category);
        List<PointShopProduct> products = pointShopProductRepository.findAllByOrderBySortOrderAscCreatedAtDesc().stream()
                .filter(product -> safeCategory == null || safeCategory.equals(normalizePointShopCategory(product.getCategory())))
                .toList();
        return PointShopProductsResponseDTO.builder()
                .myPoint(getBalance(user))
                .categories(pointShopCategories())
                .products(products.stream()
                        .map(this::toPointShopProductDto)
                        .toList())
                .build();
    }

    public PointShopProductDetailResponseDTO getPointShopProductDetail(User user, String productId) {
        PointShopProduct product = getRequiredPointShopProduct(productId);
        int myPoint = getBalance(user);
        int pricePoint = safeInt(product.getPricePoint());
        int stockCount = getAvailableStockCount(product.getId());
        boolean active = "ACTIVE".equalsIgnoreCase(product.getStatus());
        return PointShopProductDetailResponseDTO.builder()
                .id(toPointShopProductId(product))
                .brand(product.getBrand())
                .name(product.getName())
                .category(toPointShopCategoryLabel(product.getCategory()))
                .pricePoint(pricePoint)
                .myPoint(myPoint)
                .pointAfterExchange(Math.max(0, myPoint - pricePoint))
                .imageUrl(product.getImageUrl())
                .description(defaultString(product.getDescription(), product.getName() + " 교환권입니다."))
                .notice(toNoticeList(product.getNotice()))
                .status(active && stockCount > 0 ? "ACTIVE" : "SOLD_OUT")
                .canExchange(active && stockCount > 0 && myPoint >= pricePoint)
                .build();
    }

    @Transactional
    public PointShopOrderResponseDTO createPointShopOrder(User user, String productId) {
        if (!isPointShopExchangeEnabled()) {
            throw new ApiException(POINT_SHOP_EXCHANGE_BLOCKED_MESSAGE, HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (productId == null || productId.isBlank()) {
            throw new ApiException("productId is required", HttpStatus.BAD_REQUEST);
        }
        PointShopProduct product = getRequiredPointShopProduct(productId);
        if (!"ACTIVE".equalsIgnoreCase(product.getStatus())) {
            throw new ApiException("지금은 교환할 수 없는 상품이에요.", HttpStatus.CONFLICT);
        }
        GifticonInventory inventory = gifticonInventoryRepository.findFirstByProduct_IdAndStatusOrderByCreatedAtAsc(product.getId(), "AVAILABLE");
        if (inventory == null) {
            throw new ApiException("준비된 수량이 모두 소진되었어요.", HttpStatus.CONFLICT);
        }

        PointShopOrder order = pointShopOrderRepository.save(PointShopOrder.builder()
                .user(user)
                .product(product)
                .inventory(inventory)
                .usedPoint(safeInt(product.getPricePoint()))
                .status("REQUESTED")
                .build());
        String orderId = toPointShopOrderId(order);
        PointTransaction transaction = pointLedgerService.deduct(
                user,
                safeInt(product.getPricePoint()),
                "POINT_SHOP",
                orderId,
                "포인트샵 교환: " + product.getName()
        );

        inventory.setStatus("SENT");
        inventory.setAssignedOrderId(orderId);
        gifticonInventoryRepository.save(inventory);
        updatePersistedStockCount(product);

        order.setStatus("SENT");
        order.setSentAt(LocalDateTime.now());
        order.setPointTransactionId(transaction.getId() != null ? String.valueOf(transaction.getId()) : null);
        pointShopOrderRepository.save(order);

        return PointShopOrderResponseDTO.builder()
                .orderId(orderId)
                .status(order.getStatus())
                .usedPoint(order.getUsedPoint())
                .balanceAfter(transaction.getBalanceAfter())
                .message("교환 신청이 완료되었어요.")
                .build();
    }

    public PointShopOrdersResponseDTO getPointShopOrders(User user) {
        return PointShopOrdersResponseDTO.builder()
                .orders(pointShopOrderRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                        .map(this::toPointShopOrderListItem)
                        .toList())
                .build();
    }

    public PointShopOrderDetailResponseDTO getPointShopOrderDetail(User user, String orderId) {
        PointShopOrder order = getRequiredPointShopOrder(user.getId(), orderId);
        GifticonInventory inventory = order.getInventory();
        return PointShopOrderDetailResponseDTO.builder()
                .orderId(toPointShopOrderId(order))
                .productName(order.getProduct().getName())
                .brand(order.getProduct().getBrand())
                .usedPoint(order.getUsedPoint())
                .status(order.getStatus())
                .gifticonUrl(inventory != null ? inventory.getGifticonUrl() : null)
                .gifticonCode(inventory != null ? inventory.getGifticonCode() : null)
                .expiredAt(inventory != null && inventory.getExpiredAt() != null
                        ? inventory.getExpiredAt().atOffset(ZoneOffset.ofHours(9)).toString()
                        : null)
                .build();
    }

    public ShopItemsResponseDTO getShopItems(String category, String sort, Integer page, Integer size) {
        String safeCategory = category == null || category.isBlank() ? null : category.trim().toUpperCase(Locale.ROOT);
        String safeSort = sort == null || sort.isBlank() ? "POPULAR" : sort.trim().toUpperCase(Locale.ROOT);
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 10 : Math.min(size, 20);

        List<PointShopProduct> filtered = pointShopProductRepository.findAllByOrderBySortOrderAscCreatedAtDesc().stream()
                .filter(product -> safeCategory == null || safeCategory.equalsIgnoreCase(product.getCategory()))
                .toList();
        Map<Long, Integer> availableStockByProduct = new HashMap<>();
        filtered.forEach(product -> availableStockByProduct.put(product.getId(), getAvailableStockCount(product.getId())));
        List<PointShopProduct> sorted = filtered.stream()
                .sorted(resolveProductComparator(safeSort, availableStockByProduct))
                .toList();

        int fromIndex = Math.min(safePage * safeSize, sorted.size());
        int toIndex = Math.min(fromIndex + safeSize, sorted.size());

        return ShopItemsResponseDTO.builder()
                .items(sorted.subList(fromIndex, toIndex).stream()
                        .map(product -> toShopItemDto(product, availableStockByProduct.getOrDefault(product.getId(), 0)))
                        .toList())
                .page(safePage)
                .size(safeSize)
                .hasNext(toIndex < sorted.size())
                .build();
    }

    public ShopRedemptionPreviewResponseDTO getRedemptionPreview(User user, String itemId) {
        PointShopProduct product = getRequiredProduct(itemId);
        int currentBalance = getBalance(user);
        int remaining = currentBalance - safeInt(product.getPricePoint());
        int availableInventoryCount = getAvailableStockCount(product.getId());
        boolean hasInventory = availableInventoryCount > 0;
        boolean canRedeem = remaining >= 0 && hasInventory && "ACTIVE".equalsIgnoreCase(product.getStatus());
        return ShopRedemptionPreviewResponseDTO.builder()
                .item(toShopItemDto(product, availableInventoryCount))
                .point(ShopPreviewPointDTO.builder()
                        .currentBalance(currentBalance)
                        .requiredPoint(safeInt(product.getPricePoint()))
                        .remainingBalance(Math.max(0, remaining))
                        .build())
                .canRedeem(canRedeem)
                .reason(resolveRedeemReason(product, remaining, hasInventory))
                .build();
    }

    @Transactional
    public ShopRedemptionResponseDTO redeem(User user, ShopRedemptionRequestDTO request) {
        if (request == null || request.getItemId() == null || request.getItemId().isBlank()) {
            throw new ApiException("itemId is required", HttpStatus.BAD_REQUEST);
        }
        PointShopOrderResponseDTO order = createPointShopOrder(user, request.getItemId());
        return ShopRedemptionResponseDTO.builder()
                .redemptionId(order.getOrderId())
                .itemId(request.getItemId())
                .usedPoint(order.getUsedPoint())
                .remainingPoint(order.getBalanceAfter())
                .status(order.getStatus())
                .createdAt(LocalDateTime.now().atOffset(ZoneOffset.UTC).toString())
                .build();
    }

    public ShopRedemptionListResponseDTO getRedemptions(User user) {
        return ShopRedemptionListResponseDTO.builder()
                .items(pointShopOrderRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                        .map(this::toRedemptionListItem)
                        .toList())
                .build();
    }

    public ShopRedemptionDetailResponseDTO getRedemptionDetail(User user, String redemptionId) {
        PointShopOrder order = getRequiredOrder(user.getId(), redemptionId);
        LocalDate expiresAt = order.getInventory() != null && order.getInventory().getExpiredAt() != null
                ? order.getInventory().getExpiredAt().toLocalDate()
                : LocalDate.now().plusDays(14);
        return ShopRedemptionDetailResponseDTO.builder()
                .redemptionId("REDEEM_" + order.getId())
                .itemId("ITEM_" + order.getProduct().getId())
                .brand(order.getProduct().getBrand())
                .name(order.getProduct().getName())
                .imageUrl(order.getProduct().getImageUrl())
                .usedPoint(order.getUsedPoint())
                .expiresAt(expiresAt.format(DATE_FORMATTER))
                .expiresInDays(Math.max(0, (int) ChronoUnit.DAYS.between(LocalDate.now(), expiresAt)))
                .deliveryMethod("카카오톡 발송")
                .usageGuide(Optional.ofNullable(order.getProduct().getDescription()).orElse(""))
                .status(order.getStatus())
                .statusLabel("COMPLETED".equalsIgnoreCase(order.getStatus()) ? "사용 가능" : order.getStatus())
                .notice(Optional.ofNullable(order.getProduct().getNotice()).orElse(""))
                .build();
    }

    @Transactional(readOnly = true)
    public FriendListResponseDTO getFriends(User user, String keyword) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<FriendListItemDTO> items;
        if (normalized.isBlank()) {
            items = friendRelationRepository.findByRequesterUser_IdOrAddresseeUser_IdOrderByUpdatedAtDesc(user.getId(), user.getId()).stream()
                    .filter(relation -> "ACCEPTED".equalsIgnoreCase(relation.getStatus()))
                    .map(relation -> relation.getRequesterUser().getId().equals(user.getId()) ? relation.getAddresseeUser() : relation.getRequesterUser())
                    .distinct()
                    .map(friend -> toFriendListItem(friend, "FRIEND"))
                    .toList();
        } else {
            items = userRepository.findTop10ByNicknameContainingIgnoreCaseOrStudentIdContaining(normalized, normalized).stream()
                    .filter(candidate -> !candidate.getId().equals(user.getId()))
                    .map(candidate -> toFriendListItem(candidate, resolveFriendRelationLabel(user.getId(), candidate.getId())))
                    .toList();
        }
        return FriendListResponseDTO.builder().items(items).build();
    }

    @Transactional
    public FriendRequestResponseDTO requestFriend(User user, FriendRequestCreateDTO request) {
        if (request == null || request.getTargetUserId() == null || request.getTargetUserId().isBlank()) {
            throw new ApiException("targetUserId is required", HttpStatus.BAD_REQUEST);
        }
        Long targetUserId = parseUserRef(request.getTargetUserId());
        if (user.getId().equals(targetUserId)) {
            throw new ApiException("cannot request yourself", HttpStatus.BAD_REQUEST);
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ApiException("friend user not found", HttpStatus.NOT_FOUND));
        Optional<FriendRelation> existingRelation = friendRelationRepository.findBetweenUsers(user.getId(), targetUserId);
        FriendRelation relation;
        if (existingRelation.isPresent()) {
            relation = existingRelation.get();
            if (!"REJECTED".equalsIgnoreCase(relation.getStatus())) {
                throw new ApiException("friend request already exists", HttpStatus.CONFLICT);
            }
            LocalDateTime now = LocalDateTime.now();
            relation.setRequesterUser(user);
            relation.setAddresseeUser(target);
            relation.setStatus("REQUESTED");
            relation.setCreatedAt(now);
            relation.setUpdatedAt(now);
            relation = friendRelationRepository.save(relation);
        } else {
            relation = friendRelationRepository.save(FriendRelation.builder()
                    .requesterUser(user)
                    .addresseeUser(target)
                    .status("REQUESTED")
                    .build());
        }
        String requestId = "REQ_" + relation.getId();
        pushNotificationService.sendFriendRequestCreated(requestId, user, target);
        return FriendRequestResponseDTO.builder()
                .requestId(requestId)
                .targetUserId("USER_" + target.getId())
                .status(relation.getStatus())
                .createdAt(relation.getCreatedAt().atOffset(ZoneOffset.UTC).toString())
                .build();
    }

    @Transactional
    public FriendRequestResponseDTO decideFriendRequest(User user, String requestId, FriendRequestDecisionDTO request) {
        if (request == null || request.getAction() == null || request.getAction().isBlank()) {
            throw new ApiException("action is required", HttpStatus.BAD_REQUEST);
        }

        String action = request.getAction().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACCEPT", "REJECT").contains(action)) {
            throw new ApiException("action must be ACCEPT or REJECT", HttpStatus.BAD_REQUEST);
        }

        Long relationId = parsePrefixedId(requestId, "REQ_");
        FriendRelation relation = friendRelationRepository.findById(relationId)
                .orElseThrow(() -> new ApiException("friend request not found", HttpStatus.NOT_FOUND));

        if (!relation.getAddresseeUser().getId().equals(user.getId())) {
            throw new ApiException("friend request not found", HttpStatus.NOT_FOUND);
        }
        if (!"REQUESTED".equalsIgnoreCase(relation.getStatus())) {
            throw new ApiException("friend request is already processed", HttpStatus.BAD_REQUEST);
        }

        relation.setStatus("ACCEPT".equals(action) ? "ACCEPTED" : "REJECTED");
        FriendRelation saved = friendRelationRepository.save(relation);
        String responseRequestId = "REQ_" + saved.getId();
        pushNotificationService.sendFriendRequestDecision(
                responseRequestId,
                saved.getRequesterUser(),
                saved.getAddresseeUser(),
                saved.getStatus()
        );
        return FriendRequestResponseDTO.builder()
                .requestId(responseRequestId)
                .targetUserId("USER_" + saved.getRequesterUser().getId())
                .status(saved.getStatus())
                .createdAt(saved.getCreatedAt().atOffset(ZoneOffset.UTC).toString())
                .build();
    }

    @Transactional
    public void deleteFriend(User user, String friendUserId) {
        if (friendUserId == null || friendUserId.isBlank()) {
            throw new ApiException("friend user id is required", HttpStatus.BAD_REQUEST);
        }

        Long targetUserId = parseUserRef(friendUserId);
        if (user.getId().equals(targetUserId)) {
            throw new ApiException("friend not found", HttpStatus.NOT_FOUND);
        }

        FriendRelation relation = friendRelationRepository.findBetweenUsersByStatus(user.getId(), targetUserId, "ACCEPTED")
                .orElseThrow(() -> new ApiException("friend not found", HttpStatus.NOT_FOUND));
        friendRelationRepository.delete(relation);
    }

    @Transactional(readOnly = true)
    public FriendRequestListResponseDTO getSentFriendRequests(User user) {
        return FriendRequestListResponseDTO.builder()
                .items(friendRelationRepository.findByRequesterUser_IdAndStatusOrderByCreatedAtDesc(user.getId(), "REQUESTED").stream()
                        .map(relation -> toFriendRequestItem("REQ_" + relation.getId(), relation.getAddresseeUser(), relation.getCreatedAt(), relation.getStatus()))
                        .toList())
                .build();
    }

    @Transactional(readOnly = true)
    public FriendRequestListResponseDTO getReceivedFriendRequests(User user) {
        return FriendRequestListResponseDTO.builder()
                .items(friendRelationRepository.findByAddresseeUser_IdAndStatusOrderByCreatedAtDesc(user.getId(), "REQUESTED").stream()
                        .map(relation -> toFriendRequestItem("REQ_" + relation.getId(), relation.getRequesterUser(), relation.getCreatedAt(), relation.getStatus()))
                        .toList())
                .build();
    }

    @Transactional(readOnly = true)
    public FriendsDashboardResponseDTO getFriendsDashboard(User user) {
        List<User> rankingPool = friendRelationRepository.findByRequesterUser_IdOrAddresseeUser_IdOrderByUpdatedAtDesc(user.getId(), user.getId()).stream()
                .filter(relation -> "ACCEPTED".equalsIgnoreCase(relation.getStatus()))
                .flatMap(relation -> java.util.stream.Stream.of(relation.getRequesterUser(), relation.getAddresseeUser()))
                .filter(candidate -> !candidate.getId().equals(user.getId()))
                .distinct()
                .toList();
        List<User> withMe = new ArrayList<>(rankingPool);
        withMe.add(user);
        List<User> sorted = withMe.stream()
                .sorted(Comparator.comparingInt((User candidate) -> getLearningProgress(candidate).totalXp()).reversed())
                .toList();
        List<FriendRankingItemDTO> items = new ArrayList<>();
        for (int i = 0; i < Math.min(5, sorted.size()); i++) {
            items.add(toRankingItem(i + 1, sorted.get(i)));
        }
        int myRank = Math.max(1, sorted.indexOf(user) + 1);
        return FriendsDashboardResponseDTO.builder()
                .ranking(FriendRankingSectionDTO.builder().endDay(3).items(items).build())
                .myRanking(toRankingItem(myRank, user))
                .build();
    }

    private UserMyPagePreference getOrCreatePreference(Long userId) {
        return userMyPagePreferenceRepository.findById(userId)
                .orElseGet(() -> userMyPagePreferenceRepository.save(UserMyPagePreference.builder()
                        .userId(userId)
                        .selectedCharacterCode("SEED")
                        .pushEnabled(Boolean.TRUE)
                        .build()));
    }

    private List<MyPageCharacterCardDTO> buildCharacters(UserMyPagePreference preference) {
        return buildCharacters(preference, 1);
    }

    private List<MyPageCharacterCardDTO> buildCharacters(UserMyPagePreference preference, int level) {
        String selectedCode = defaultString(preference.getSelectedCharacterCode(), "SEED");
        int stage = characterStage(level);
        return List.of(
                character("SEED", "조심스러운 거북이형", "🐢", "#d9f1c7", stage, selectedCode),
                character("PANDA", "균형잡힌 판다형", "🐼", "#d3ecff", stage, selectedCode),
                character("FOX", "기회를 찾는 여우형", "🦊", "#ffe0c2", stage, selectedCode)
        );
    }

    private MyPageCharacterCardDTO character(String code, String name, String emoji, String themeColor, int stage, String selectedCode) {
        return MyPageCharacterCardDTO.builder()
                .code(code)
                .name(name)
                .emoji(emoji)
                .themeColor(themeColor)
                .stage(stage)
                .assetKey(characterAssetKey(code, stage))
                .selected(code.equalsIgnoreCase(selectedCode))
                .build();
    }

    private List<MyPageBadgeDTO> buildBadges(int streak, int redemptionCount) {
        return List.of(
                MyPageBadgeDTO.builder().code("FIRST_LOGIN").label("첫 방문").description("유니포트 첫 방문").unlocked(Boolean.TRUE).build(),
                MyPageBadgeDTO.builder().code("STREAK_3").label("3일 연속").description("3일 연속으로 활동했어요").unlocked(streak >= 3).build(),
                MyPageBadgeDTO.builder().code("SHOPPER").label("첫 교환").description("포인트샵에서 첫 상품을 교환했어요").unlocked(redemptionCount >= 1).build()
        );
    }

    private MyPageNoteDTO buildInvestmentNote(User user, List<Holding> holdings) {
        String profile = defaultString(user.getInvestmentProfileResult(), "균형잡힌 판다형");
        String description = switch (profile) {
            case "조심스러운 거북이형" -> "원금을 지키면서 천천히 배우고 싶은 장기형 투자자에 가깝습니다.";
            case "기회를 찾는 여우형" -> "기회를 빠르게 포착하지만 리스크 관리가 중요한 투자 성향입니다.";
            default -> "안정성과 성장의 균형을 함께 보려는 투자 성향입니다.";
        };

        List<String> principles = holdings.isEmpty()
                ? List.of("모르는 기업에는 투자하지 않기", "매수 전 3번 더 확인하기", "자동이체로 투자 습관 만들기")
                : List.of("보유 종목의 사업보고서 확인하기", "한 종목에 과도하게 몰리지 않기", "매수 이유를 투자 노트에 남기기");

        List<String> strategies = holdings.size() >= 3
                ? List.of("코어+위성 조합 유지하기", "정기 매수 비중 점검하기", "실적 발표 전후로 리스크 체크하기")
                : List.of("관심 종목 3개 먼저 추리기", "ETF로 분산 시작하기", "짧은 매매보다 기록 습관 먼저 만들기");

        return MyPageNoteDTO.builder()
                .investorTypeTitle(profile)
                .investorTypeDescription(description)
                .principles(principles)
                .recommendedStrategies(strategies)
                .build();
    }

    private List<MyPageHistoryItemDTO> buildInvestmentHistory(List<Order> orders, List<Holding> holdings) {
        List<MyPageHistoryItemDTO> orderItems = orders.stream()
                .limit(5)
                .map(order -> MyPageHistoryItemDTO.builder()
                        .title(order.getStockCode())
                        .subtitle(order.getOrderType().name() + " " + order.getQuantity() + "주")
                        .valueLabel(formatMoney(order.getPrice()))
                        .statusLabel(order.getStatus().name())
                        .happenedAtLabel(toAgoLabel(order.getOrderDate()))
                        .build())
                .toList();
        if (!orderItems.isEmpty()) {
            return orderItems;
        }
        return holdings.stream()
                .limit(5)
                .map(holding -> MyPageHistoryItemDTO.builder()
                        .title(holding.getStockCode())
                        .subtitle("보유 수량 " + holding.getQuantity() + "주")
                        .valueLabel(formatMoney(holding.getAveragePurchasePrice()))
                        .statusLabel("HOLDING")
                        .happenedAtLabel("현재 보유")
                        .build())
                .toList();
    }

    private List<MyPageHistoryItemDTO> buildLearningHistory(LearningUserStateEntity learningState) {
        if (learningState == null) {
            return List.of();
        }
        int completedCount = countCompletedLearningDays(learningState);
        return List.of(
                MyPageHistoryItemDTO.builder()
                        .title("MAIN COURSE")
                        .subtitle("완료한 Day " + completedCount + "개")
                        .valueLabel(learningState.getPoint() + "P")
                        .statusLabel("IN_PROGRESS")
                        .happenedAtLabel(learningState.getLastCompletedDate() != null ? learningState.getLastCompletedDate().format(DATE_FORMATTER) : "진행 중")
                        .build(),
                MyPageHistoryItemDTO.builder()
                        .title("LEARNING STREAK")
                        .subtitle("현재 레벨 " + safeInt(learningState.getLevel()))
                        .valueLabel("연속 " + safeInt(learningState.getStreakDays()) + "일")
                        .statusLabel("ACTIVE")
                        .happenedAtLabel("학습 상태 저장됨")
                        .build()
        );
    }

    private List<MyPageTitleDTO> buildTitles(User user, int streak, int redemptionCount, List<Holding> holdings, List<Order> orders) {
        return List.of(
                MyPageTitleDTO.builder()
                        .code("NEXT_BUFFETT")
                        .label("차세대 워렌버핏")
                        .description("수익률 100% 이상 달성")
                        .unlocked(orZero(user.getProfitLossRate()).compareTo(BigDecimal.valueOf(100)) >= 0)
                        .build(),
                MyPageTitleDTO.builder()
                        .code("TOURNAMENTER")
                        .label("거래 챌린저")
                        .description("주문 기록 10건 이상")
                        .unlocked(orders.size() >= 10)
                        .build(),
                MyPageTitleDTO.builder()
                        .code("NOTE_KEEPER")
                        .label("투자 노트 수집가")
                        .description("마이페이지 투자 원칙을 유지하는 투자자")
                        .unlocked(Boolean.TRUE)
                        .build(),
                MyPageTitleDTO.builder()
                        .code("STEADY")
                        .label("꾸준한 학습가")
                        .description("최근 활동 기록을 꾸준히 이어감")
                        .unlocked(streak >= 3)
                        .build(),
                MyPageTitleDTO.builder()
                        .code("DIVERSIFIER")
                        .label("분산 투자 입문자")
                        .description("보유 종목 3개 이상 유지")
                        .unlocked(holdings.size() >= 3)
                        .build(),
                MyPageTitleDTO.builder()
                        .code("SHOPPER")
                        .label("포인트 헌터")
                        .description("포인트샵 상품 첫 교환")
                        .unlocked(redemptionCount >= 1)
                        .build()
        );
    }

    private FriendListItemDTO toFriendListItem(User friend, String relationStatus) {
        LearningProgressSnapshot learningProgress = getLearningProgress(friend);
        return FriendListItemDTO.builder()
                .userId("USER_" + friend.getId())
                .nickname(friend.getNickname())
                .profileImageUrl(friend.getProfileImageUrl())
                .level(learningProgress.level())
                .investmentProfileLabel(friend.getInvestmentProfileResult())
                .currentXp(learningProgress.currentExp())
                .maxXp(learningProgress.maxExp())
                .relationLabel(toRelationLabel(relationStatus))
                .description(toFriendDescription(friend, relationStatus))
                .build();
    }

    private FriendRequestListItemDTO toFriendRequestItem(String requestId, User target, LocalDateTime createdAt, String status) {
        LearningProgressSnapshot learningProgress = getLearningProgress(target);
        return FriendRequestListItemDTO.builder()
                .requestId(requestId)
                .userId("USER_" + target.getId())
                .nickname(target.getNickname())
                .profileImageUrl(target.getProfileImageUrl())
                .level(learningProgress.level())
                .investmentProfileLabel(target.getInvestmentProfileResult())
                .requestedAgoLabel(toAgoLabel(createdAt))
                .status(status)
                .build();
    }

    private FriendRankingItemDTO toRankingItem(int rank, User user) {
        LearningProgressSnapshot learningProgress = getLearningProgress(user);
        return FriendRankingItemDTO.builder()
                .rank(rank)
                .userId("USER_" + user.getId())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .level(learningProgress.level())
                .xp(learningProgress.totalXp())
                .rankChange(0)
                .build();
    }

    private ShopItemDTO toShopItemDto(PointShopProduct product, int availableInventoryCount) {
        boolean available = availableInventoryCount > 0;
        return ShopItemDTO.builder()
                .itemId("ITEM_" + product.getId())
                .brand(product.getBrand())
                .name(product.getName())
                .imageUrl(product.getImageUrl())
                .requiredPoint(safeInt(product.getPricePoint()))
                .badge(product.getCategory())
                .stockStatus(available && "ACTIVE".equalsIgnoreCase(product.getStatus()) ? "AVAILABLE" : "SOLD_OUT")
                .build();
    }

    private PointShopProductDTO toPointShopProductDto(PointShopProduct product) {
        int availableStockCount = getAvailableStockCount(product.getId());
        boolean active = "ACTIVE".equalsIgnoreCase(product.getStatus()) && availableStockCount > 0;
        return PointShopProductDTO.builder()
                .id(toPointShopProductId(product))
                .brand(product.getBrand())
                .name(product.getName())
                .category(toPointShopCategoryLabel(product.getCategory()))
                .pricePoint(safeInt(product.getPricePoint()))
                .imageUrl(product.getImageUrl())
                .status(active ? "ACTIVE" : "SOLD_OUT")
                .stockCount(availableStockCount)
                .build();
    }

    private PointShopOrderListItemDTO toPointShopOrderListItem(PointShopOrder order) {
        return PointShopOrderListItemDTO.builder()
                .orderId(toPointShopOrderId(order))
                .productName(order.getProduct().getName())
                .brand(order.getProduct().getBrand())
                .usedPoint(order.getUsedPoint())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt() != null ? order.getCreatedAt().atOffset(ZoneOffset.ofHours(9)).toString() : null)
                .sentAt(order.getSentAt() != null ? order.getSentAt().atOffset(ZoneOffset.ofHours(9)).toString() : null)
                .build();
    }

    private Comparator<PointShopProduct> resolveProductComparator(String sort, Map<Long, Integer> availableStockByProduct) {
        return switch (sort) {
            case "LOW_POINT" -> Comparator.comparingInt(product -> safeInt(product.getPricePoint()));
            case "HIGH_POINT" -> Comparator.comparingInt((PointShopProduct product) -> safeInt(product.getPricePoint())).reversed();
            default -> Comparator.comparingInt((PointShopProduct product) -> availableStockByProduct.getOrDefault(product.getId(), 0)).reversed()
                    .thenComparingInt(product -> safeInt(product.getSortOrder()));
        };
    }

    private int updatePersistedStockCount(PointShopProduct product) {
        int availableInventoryCount = getAvailableStockCount(product.getId());
        if (safeInt(product.getStockCount()) != availableInventoryCount) {
            product.setStockCount(availableInventoryCount);
            pointShopProductRepository.save(product);
        }
        return availableInventoryCount;
    }

    private int getAvailableStockCount(Long productId) {
        return Math.toIntExact(gifticonInventoryRepository.countByProduct_IdAndStatus(productId, "AVAILABLE"));
    }

    private String resolveFriendRelationLabel(Long userId, Long otherUserId) {
        return friendRelationRepository.findBetweenUsers(userId, otherUserId)
                .map(relation -> {
                    if ("ACCEPTED".equalsIgnoreCase(relation.getStatus())) {
                        return "FRIEND";
                    }
                    if ("REQUESTED".equalsIgnoreCase(relation.getStatus())) {
                        return relation.getRequesterUser().getId().equals(userId) ? "REQUESTED_SENT" : "REQUESTED_RECEIVED";
                    }
                    if ("REJECTED".equalsIgnoreCase(relation.getStatus())) {
                        return "NONE";
                    }
                    return relation.getStatus();
                })
                .orElse("NONE");
    }

    private String toRelationLabel(String relationStatus) {
        return switch (relationStatus) {
            case "FRIEND" -> "친구";
            case "REQUESTED_SENT" -> "요청 보냄";
            case "REQUESTED_RECEIVED" -> "받은 요청";
            default -> "요청 가능";
        };
    }

    private String toFriendDescription(User friend, String relationStatus) {
        return switch (relationStatus) {
            case "FRIEND" -> friend.getTeamId() != null ? "같은 팀에서 활동 중" : "함께 공부하는 친구";
            case "REQUESTED_SENT" -> "상대의 수락을 기다리는 중";
            case "REQUESTED_RECEIVED" -> "수락 또는 거절이 필요한 요청";
            default -> friend.getTeamId() != null ? "같은 팀 소속 유저" : "친구 요청을 보낼 수 있는 유저";
        };
    }

    private ShopRedemptionListItemDTO toRedemptionListItem(PointShopOrder order) {
        LocalDate expiresAt = order.getInventory() != null && order.getInventory().getExpiredAt() != null
                ? order.getInventory().getExpiredAt().toLocalDate()
                : LocalDate.now().plusDays(14);
        return ShopRedemptionListItemDTO.builder()
                .redemptionId("REDEEM_" + order.getId())
                .itemId("ITEM_" + order.getProduct().getId())
                .brand(order.getProduct().getBrand())
                .name(order.getProduct().getName())
                .imageUrl(order.getProduct().getImageUrl())
                .usedPoint(order.getUsedPoint())
                .requestedAgoLabel(toAgoLabel(order.getCreatedAt()))
                .expiresAt(expiresAt.format(DATE_FORMATTER))
                .expiresInDays(Math.max(0, (int) ChronoUnit.DAYS.between(LocalDate.now(), expiresAt)))
                .status(order.getStatus())
                .statusLabel("COMPLETED".equalsIgnoreCase(order.getStatus()) ? "사용 가능" : order.getStatus())
                .build();
    }

    private PointShopProduct getRequiredProduct(String itemId) {
        Long id = parsePrefixedId(itemId, "ITEM_");
        return pointShopProductRepository.findById(id)
                .orElseThrow(() -> new ApiException("shop item not found", HttpStatus.NOT_FOUND));
    }

    private PointShopProduct getRequiredPointShopProduct(String productId) {
        Long id = parsePointShopProductId(productId);
        return pointShopProductRepository.findById(id)
                .orElseThrow(() -> new ApiException("상품 정보를 찾을 수 없어요.", HttpStatus.NOT_FOUND));
    }

    private PointShopOrder getRequiredOrder(Long userId, String redemptionId) {
        Long id = parsePrefixedId(redemptionId, "REDEEM_");
        PointShopOrder order = pointShopOrderRepository.findById(id)
                .orElseThrow(() -> new ApiException("redemption not found", HttpStatus.NOT_FOUND));
        if (!order.getUser().getId().equals(userId)) {
            throw new ApiException("redemption not found", HttpStatus.NOT_FOUND);
        }
        return order;
    }

    private PointShopOrder getRequiredPointShopOrder(Long userId, String orderId) {
        Long id = parsePointShopOrderId(orderId);
        PointShopOrder order = pointShopOrderRepository.findById(id)
                .orElseThrow(() -> new ApiException("교환 내역을 찾을 수 없어요.", HttpStatus.NOT_FOUND));
        if (!order.getUser().getId().equals(userId)) {
            throw new ApiException("교환 내역을 찾을 수 없어요.", HttpStatus.NOT_FOUND);
        }
        return order;
    }

    private String toPointShopProductId(PointShopProduct product) {
        return "product-" + product.getId();
    }

    private String toPointShopOrderId(PointShopOrder order) {
        return "order-" + order.getId();
    }

    private Long parsePointShopProductId(String value) {
        String normalized = value != null && value.startsWith("product-") ? value.substring("product-".length()) : value;
        if (normalized != null && normalized.startsWith("ITEM_")) {
            normalized = normalized.substring("ITEM_".length());
        }
        return parseLongId(normalized);
    }

    private Long parsePointShopOrderId(String value) {
        String normalized = value != null && value.startsWith("order-") ? value.substring("order-".length()) : value;
        if (normalized != null && normalized.startsWith("REDEEM_")) {
            normalized = normalized.substring("REDEEM_".length());
        }
        return parseLongId(normalized);
    }

    private Long parseLongId(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            throw new ApiException("invalid id", HttpStatus.BAD_REQUEST);
        }
    }

    private List<String> pointShopCategories() {
        return List.of("전체", "커피", "편의점");
    }

    private String normalizePointShopCategory(String value) {
        if (value == null || value.isBlank() || "전체".equals(value.trim())) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "커피", "CAFE", "COFFEE" -> "CAFE";
            case "편의점", "CONVENIENCE", "CU" -> "CONVENIENCE";
            default -> normalized;
        };
    }

    private String toPointShopCategoryLabel(String value) {
        String normalized = normalizePointShopCategory(value);
        if ("CAFE".equals(normalized)) {
            return "커피";
        }
        if ("CONVENIENCE".equals(normalized)) {
            return "편의점";
        }
        return defaultString(value, "기타");
    }

    private List<String> toNoticeList(String notice) {
        if (notice == null || notice.isBlank()) {
            return List.of(
                    "교환 신청 후 기프티콘은 마이페이지에서 확인할 수 있습니다.",
                    "유효기간이 지난 기프티콘은 재발급이 어려울 수 있습니다.",
                    "부정한 방법으로 적립한 포인트는 회수될 수 있습니다."
            );
        }
        return notice.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private String resolveSelectedCharacterName(UserMyPagePreference preference) {
        return buildCharacters(preference).stream()
                .filter(character -> Boolean.TRUE.equals(character.getSelected()))
                .findFirst()
                .map(MyPageCharacterCardDTO::getName)
                .orElse("조심스러운 거북이형");
    }

    private String selectedCharacterCode(UserMyPagePreference preference) {
        return defaultString(preference.getSelectedCharacterCode(), "SEED").trim().toUpperCase(Locale.ROOT);
    }

    private int characterStage(int level) {
        if (level >= 70) {
            return 3;
        }
        if (level >= 30) {
            return 2;
        }
        return 1;
    }

    private String characterAssetKey(String code, int stage) {
        return "character_" + selectedCodeSegment(code) + "_stage_" + Math.max(1, Math.min(stage, 3));
    }

    private String selectedCodeSegment(String code) {
        String safeCode = code == null || code.isBlank() ? "SEED" : code.trim();
        return safeCode.toLowerCase(Locale.ROOT);
    }

    private boolean resolvePushEnabled(User user, UserMyPagePreference preference) {
        if (preference.getPushEnabled() != null) {
            return preference.getPushEnabled();
        }
        return (user.getEmail() != null && !user.getEmail().isBlank())
                || (user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank());
    }

    private int getBalance(User user) {
        return pointWalletRepository.findByUser_Id(user.getId()).map(PointWallet::getBalance).orElse(0);
    }

    private boolean isPointShopExchangeEnabled() {
        return false;
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private String resolveRedeemReason(PointShopProduct product, int remaining, boolean hasInventory) {
        if (!"ACTIVE".equalsIgnoreCase(product.getStatus()) || !hasInventory) {
            return "재고가 없습니다.";
        }
        if (remaining < 0) {
            return "보유 포인트가 부족합니다.";
        }
        return null;
    }

    private Long parseUserRef(String value) {
        return parsePrefixedId(value, "USER_");
    }

    private Long parsePrefixedId(String value, String prefix) {
        String normalized = value != null && value.startsWith(prefix) ? value.substring(prefix.length()) : value;
        try {
            return Long.parseLong(normalized);
        } catch (Exception e) {
            throw new ApiException("invalid id", HttpStatus.BAD_REQUEST);
        }
    }

    private String toAgoLabel(LocalDateTime createdAt) {
        Duration duration = Duration.between(createdAt, LocalDateTime.now());
        long minutes = Math.max(1, duration.toMinutes());
        if (minutes < 60) {
            return minutes + "분 전";
        }
        long hours = duration.toHours();
        if (hours < 24) {
            return hours + "시간 전";
        }
        return duration.toDays() + "일 전";
    }

    private LearningProgressSnapshot getLearningProgress(User user) {
        return learningUserStateRepository.findById(user.getId())
                .map(this::toLearningProgressSnapshot)
                .orElseGet(() -> new LearningProgressSnapshot(1, 0, 300, 0, 0, 0));
    }

    private LearningProgressSnapshot toLearningProgressSnapshot(LearningUserStateEntity state) {
        int totalXp = state.getExp() != null ? safeInt(state.getExp()) : safeInt(state.getPoint());
        LearningProgressPolicy.Progress progress = LearningProgressPolicy.fromExp(totalXp);
        int completedDays = countCompletedLearningDays(state);
        int learningTimeMinutes = completedDays * 15;
        return new LearningProgressSnapshot(
                progress.level(),
                progress.currentExp(),
                progress.maxExp(),
                progress.totalExp(),
                safeInt(state.getStreakDays()),
                learningTimeMinutes
        );
    }

    private int countCompletedLearningDays(LearningUserStateEntity learningState) {
        int total = 0;
        Map<String, Set<Integer>> courseDays = readValue(learningState.getCompletedDaysByCourseJson(), COMPLETED_DAYS_TYPE);
        for (Set<Integer> days : courseDays.values()) {
            total += days == null ? 0 : days.size();
        }
        Map<String, Set<Integer>> educationDays = readValue(learningState.getEducationCompletedDaysJson(), EDUCATION_COMPLETED_DAYS_TYPE);
        for (Set<Integer> days : educationDays.values()) {
            total += days == null ? 0 : days.size();
        }
        Map<String, Integer> educationCurrentDays = readValue(learningState.getEducationCurrentDayJson(), EDUCATION_CURRENT_DAY_TYPE);
        if (total == 0 && !educationCurrentDays.isEmpty()) {
            total = Math.max(0, educationCurrentDays.values().stream().mapToInt(value -> value == null ? 0 : value - 1).sum());
        }
        return total;
    }

    private <T> T readValue(String value, TypeReference<T> typeReference) {
        try {
            String safeJson = value == null || value.isBlank() ? "{}" : value;
            return OBJECT_MAPPER.readValue(safeJson, typeReference);
        } catch (Exception e) {
            throw new ApiException("failed to read learning progress", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String formatMoney(BigDecimal value) {
        BigDecimal safe = value != null ? value : BigDecimal.ZERO;
        return safe.setScale(0, RoundingMode.HALF_UP).toPlainString() + "원";
    }

    private record LearningProgressSnapshot(
            int level,
            int currentExp,
            int maxExp,
            int totalXp,
            int streakDays,
            int learningTimeMinutes
    ) {
    }
}
