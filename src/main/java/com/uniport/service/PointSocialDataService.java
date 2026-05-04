package com.uniport.service;

import com.uniport.dto.FriendListItemDTO;
import com.uniport.dto.FriendListResponseDTO;
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
import com.uniport.dto.MyPageProfileUpdateRequestDTO;
import com.uniport.dto.MyPageResponseDTO;
import com.uniport.dto.MyPageSettingsDTO;
import com.uniport.dto.MyPageSettingsUpdateRequestDTO;
import com.uniport.dto.MyPageSummaryDTO;
import com.uniport.dto.MyPageUserDTO;
import com.uniport.dto.PointBalanceResponseDTO;
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
import com.uniport.entity.PointShopOrder;
import com.uniport.entity.PointShopProduct;
import com.uniport.entity.PointTransaction;
import com.uniport.entity.PointWallet;
import com.uniport.entity.User;
import com.uniport.entity.UserMyPagePreference;
import com.uniport.exception.ApiException;
import com.uniport.repository.FriendRelationRepository;
import com.uniport.repository.GifticonInventoryRepository;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class PointSocialDataService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;

    private final PointWalletRepository pointWalletRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final PointShopProductRepository pointShopProductRepository;
    private final GifticonInventoryRepository gifticonInventoryRepository;
    private final PointShopOrderRepository pointShopOrderRepository;
    private final FriendRelationRepository friendRelationRepository;
    private final UserRepository userRepository;
    private final UserMyPagePreferenceRepository userMyPagePreferenceRepository;

    public PointSocialDataService(PointWalletRepository pointWalletRepository,
                                  PointTransactionRepository pointTransactionRepository,
                                  PointShopProductRepository pointShopProductRepository,
                                  GifticonInventoryRepository gifticonInventoryRepository,
                                  PointShopOrderRepository pointShopOrderRepository,
                                  FriendRelationRepository friendRelationRepository,
                                  UserRepository userRepository,
                                  UserMyPagePreferenceRepository userMyPagePreferenceRepository) {
        this.pointWalletRepository = pointWalletRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.pointShopProductRepository = pointShopProductRepository;
        this.gifticonInventoryRepository = gifticonInventoryRepository;
        this.pointShopOrderRepository = pointShopOrderRepository;
        this.friendRelationRepository = friendRelationRepository;
        this.userRepository = userRepository;
        this.userMyPagePreferenceRepository = userMyPagePreferenceRepository;
    }

    public MyPageResponseDTO getMyPage(User user) {
        UserMyPagePreference preference = getOrCreatePreference(user.getId());
        int balance = getBalance(user);
        int level = Math.max(1, balance / 1000 + 1);
        int currentExp = Math.max(0, balance % 1000);
        int streak = pointTransactionRepository.findTop50ByUser_IdOrderByCreatedAtDesc(user.getId()).size();
        int friendCount = (int) friendRelationRepository.findByRequesterUser_IdOrAddresseeUser_IdOrderByUpdatedAtDesc(user.getId(), user.getId()).stream()
                .filter(relation -> "ACCEPTED".equalsIgnoreCase(relation.getStatus()))
                .count();
        int redemptionCount = pointShopOrderRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).size();
        return MyPageResponseDTO.builder()
                .user(MyPageUserDTO.builder()
                        .nickname(user.getNickname())
                        .profileImageUrl(user.getProfileImageUrl())
                        .level(level)
                        .investmentMbti(defaultString(user.getInvestmentProfileResult(), "UNKNOWN"))
                        .character(resolveSelectedCharacterName(user, preference))
                        .bio(defaultString(preference.getBio(), "나만의 투자 원칙을 기록해 보세요."))
                        .build())
                .exp(MyPageExpDTO.builder().currentExp(currentExp).maxExp(1000).build())
                .summary(MyPageSummaryDTO.builder()
                        .learningTimeMinutes(redemptionCount * 15)
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
                .characters(buildCharacters(user, preference))
                .badges(buildBadges(user, streak, redemptionCount))
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
        boolean valid = buildCharacters(user, getOrCreatePreference(user.getId())).stream()
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

    public ShopItemsResponseDTO getShopItems(String category, String sort, Integer page, Integer size) {
        String safeCategory = category == null || category.isBlank() ? null : category.trim().toUpperCase(Locale.ROOT);
        String safeSort = sort == null || sort.isBlank() ? "POPULAR" : sort.trim().toUpperCase(Locale.ROOT);
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 10 : Math.min(size, 20);

        List<PointShopProduct> filtered = pointShopProductRepository.findAllByOrderBySortOrderAscCreatedAtDesc().stream()
                .filter(product -> safeCategory == null || safeCategory.equalsIgnoreCase(product.getCategory()))
                .sorted(resolveProductComparator(safeSort))
                .toList();

        int fromIndex = Math.min(safePage * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());

        return ShopItemsResponseDTO.builder()
                .items(filtered.subList(fromIndex, toIndex).stream().map(this::toShopItemDto).toList())
                .page(safePage)
                .size(safeSize)
                .hasNext(toIndex < filtered.size())
                .build();
    }

    public ShopRedemptionPreviewResponseDTO getRedemptionPreview(User user, String itemId) {
        PointShopProduct product = getRequiredProduct(itemId);
        int currentBalance = getBalance(user);
        int remaining = currentBalance - safeInt(product.getPricePoint());
        boolean hasInventory = gifticonInventoryRepository.findFirstByProduct_IdAndStatusOrderByCreatedAtAsc(product.getId(), "AVAILABLE") != null;
        boolean canRedeem = remaining >= 0 && hasInventory && "ACTIVE".equalsIgnoreCase(product.getStatus());
        return ShopRedemptionPreviewResponseDTO.builder()
                .item(toShopItemDto(product))
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
        PointShopProduct product = getRequiredProduct(request.getItemId());
        PointWallet wallet = pointWalletRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new ApiException("point wallet not found", HttpStatus.CONFLICT));
        GifticonInventory inventory = gifticonInventoryRepository.findFirstByProduct_IdAndStatusOrderByCreatedAtAsc(product.getId(), "AVAILABLE");
        if (inventory == null) {
            throw new ApiException("item is sold out", HttpStatus.CONFLICT);
        }
        if (wallet.getBalance() < safeInt(product.getPricePoint())) {
            throw new ApiException("not enough points", HttpStatus.CONFLICT);
        }
        wallet.setBalance(wallet.getBalance() - safeInt(product.getPricePoint()));
        pointWalletRepository.save(wallet);

        PointShopOrder order = pointShopOrderRepository.save(PointShopOrder.builder()
                .user(user)
                .product(product)
                .inventory(inventory)
                .usedPoint(safeInt(product.getPricePoint()))
                .status("COMPLETED")
                .sentAt(java.time.LocalDateTime.now())
                .build());
        inventory.setStatus("ASSIGNED");
        inventory.setAssignedOrderId("REDEEM_" + order.getId());
        gifticonInventoryRepository.save(inventory);

        PointTransaction transaction = pointTransactionRepository.save(PointTransaction.builder()
                .user(user)
                .type("USE")
                .amount(-safeInt(product.getPricePoint()))
                .balanceAfter(wallet.getBalance())
                .sourceType("SHOP_REDEMPTION")
                .sourceId(String.valueOf(order.getId()))
                .description(product.getName() + " redemption")
                .build());
        order.setPointTransactionId(String.valueOf(transaction.getId()));
        pointShopOrderRepository.save(order);

        return ShopRedemptionResponseDTO.builder()
                .redemptionId("REDEEM_" + order.getId())
                .itemId("ITEM_" + product.getId())
                .usedPoint(order.getUsedPoint())
                .remainingPoint(wallet.getBalance())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt().atOffset(ZoneOffset.UTC).toString())
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

    public FriendListResponseDTO getFriends(User user, String keyword) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<FriendListItemDTO> items = friendRelationRepository.findByRequesterUser_IdOrAddresseeUser_IdOrderByUpdatedAtDesc(user.getId(), user.getId()).stream()
                .filter(relation -> "ACCEPTED".equalsIgnoreCase(relation.getStatus()))
                .map(relation -> relation.getRequesterUser().getId().equals(user.getId()) ? relation.getAddresseeUser() : relation.getRequesterUser())
                .filter(friend -> normalized.isBlank() || friend.getNickname().toLowerCase(Locale.ROOT).contains(normalized))
                .distinct()
                .map(this::toFriendListItem)
                .toList();
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
        friendRelationRepository.findBetweenUsers(user.getId(), targetUserId)
                .ifPresent(relation -> {
                    throw new ApiException("friend request already exists", HttpStatus.CONFLICT);
                });
        FriendRelation relation = friendRelationRepository.save(FriendRelation.builder()
                .requesterUser(user)
                .addresseeUser(target)
                .status("REQUESTED")
                .build());
        return FriendRequestResponseDTO.builder()
                .requestId("REQ_" + relation.getId())
                .targetUserId("USER_" + target.getId())
                .status(relation.getStatus())
                .createdAt(relation.getCreatedAt().atOffset(ZoneOffset.UTC).toString())
                .build();
    }

    public FriendRequestListResponseDTO getSentFriendRequests(User user) {
        return FriendRequestListResponseDTO.builder()
                .items(friendRelationRepository.findByRequesterUser_IdAndStatusOrderByCreatedAtDesc(user.getId(), "REQUESTED").stream()
                        .map(relation -> toFriendRequestItem("REQ_" + relation.getId(), relation.getAddresseeUser(), relation.getCreatedAt(), relation.getStatus()))
                        .toList())
                .build();
    }

    public FriendRequestListResponseDTO getReceivedFriendRequests(User user) {
        return FriendRequestListResponseDTO.builder()
                .items(friendRelationRepository.findByAddresseeUser_IdAndStatusOrderByCreatedAtDesc(user.getId(), "REQUESTED").stream()
                        .map(relation -> toFriendRequestItem("REQ_" + relation.getId(), relation.getRequesterUser(), relation.getCreatedAt(), relation.getStatus()))
                        .toList())
                .build();
    }

    public FriendsDashboardResponseDTO getFriendsDashboard(User user) {
        List<User> rankingPool = friendRelationRepository.findByRequesterUser_IdOrAddresseeUser_IdOrderByUpdatedAtDesc(user.getId(), user.getId()).stream()
                .filter(relation -> "ACCEPTED".equalsIgnoreCase(relation.getStatus()))
                .flatMap(relation -> java.util.stream.Stream.of(relation.getRequesterUser(), relation.getAddresseeUser()))
                .filter(candidate -> !candidate.getId().equals(user.getId()))
                .distinct()
                .toList();
        List<User> withMe = new java.util.ArrayList<>(rankingPool);
        withMe.add(user);
        List<User> sorted = withMe.stream()
                .sorted(Comparator.comparingInt(this::getBalance).reversed())
                .toList();
        List<FriendRankingItemDTO> items = new java.util.ArrayList<>();
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

    private List<MyPageCharacterCardDTO> buildCharacters(User user, UserMyPagePreference preference) {
        String selectedCode = defaultString(preference.getSelectedCharacterCode(), "SEED");
        return List.of(
                character("SEED", "조심스러운 거북이형", "🐢", "#d9f1c7", selectedCode),
                character("PANDA", "균형 잡힌 판다형", "🐼", "#d3ecff", selectedCode),
                character("FOX", "기민한 여우형", "🦊", "#ffe0c2", selectedCode)
        );
    }

    private MyPageCharacterCardDTO character(String code, String name, String emoji, String themeColor, String selectedCode) {
        return MyPageCharacterCardDTO.builder()
                .code(code)
                .name(name)
                .emoji(emoji)
                .themeColor(themeColor)
                .selected(code.equalsIgnoreCase(selectedCode))
                .build();
    }

    private List<MyPageBadgeDTO> buildBadges(User user, int streak, int redemptionCount) {
        return List.of(
                MyPageBadgeDTO.builder().code("FIRST_LOGIN").label("첫 로그인").description("유니포트에 첫 방문").unlocked(Boolean.TRUE).build(),
                MyPageBadgeDTO.builder().code("STREAK_3").label("3일 연속").description("연속 활동 3일 달성").unlocked(streak >= 3).build(),
                MyPageBadgeDTO.builder().code("SHOPPER").label("포인트 교환").description("포인트샵 첫 교환").unlocked(redemptionCount >= 1).build()
        );
    }

    private FriendListItemDTO toFriendListItem(User friend) {
        int balance = getBalance(friend);
        return FriendListItemDTO.builder()
                .userId("USER_" + friend.getId())
                .nickname(friend.getNickname())
                .profileImageUrl(friend.getProfileImageUrl())
                .level(Math.max(1, balance / 1000 + 1))
                .investmentProfileLabel(friend.getInvestmentProfileResult())
                .currentXp(balance % 1000)
                .maxXp(1000)
                .relationLabel("친구")
                .description(friend.getTeamId() != null ? "같은 팀에서 활동 중" : "투자 학습 친구")
                .build();
    }

    private FriendRequestListItemDTO toFriendRequestItem(String requestId, User target, java.time.LocalDateTime createdAt, String status) {
        int balance = getBalance(target);
        return FriendRequestListItemDTO.builder()
                .requestId(requestId)
                .userId("USER_" + target.getId())
                .nickname(target.getNickname())
                .profileImageUrl(target.getProfileImageUrl())
                .level(Math.max(1, balance / 1000 + 1))
                .investmentProfileLabel(target.getInvestmentProfileResult())
                .requestedAgoLabel(toAgoLabel(createdAt))
                .status(status)
                .build();
    }

    private FriendRankingItemDTO toRankingItem(int rank, User user) {
        int balance = getBalance(user);
        return FriendRankingItemDTO.builder()
                .rank(rank)
                .userId("USER_" + user.getId())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .level(Math.max(1, balance / 1000 + 1))
                .xp(balance)
                .rankChange(0)
                .build();
    }

    private ShopItemDTO toShopItemDto(PointShopProduct product) {
        boolean available = gifticonInventoryRepository.findFirstByProduct_IdAndStatusOrderByCreatedAtAsc(product.getId(), "AVAILABLE") != null;
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

    private Comparator<PointShopProduct> resolveProductComparator(String sort) {
        return switch (sort) {
            case "LOW_POINT" -> Comparator.comparingInt(product -> safeInt(product.getPricePoint()));
            case "HIGH_POINT" -> Comparator.comparingInt((PointShopProduct product) -> safeInt(product.getPricePoint())).reversed();
            default -> Comparator.comparingInt((PointShopProduct product) -> safeInt(product.getStockCount())).reversed()
                    .thenComparingInt(product -> safeInt(product.getSortOrder()));
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

    private PointShopOrder getRequiredOrder(Long userId, String redemptionId) {
        Long id = parsePrefixedId(redemptionId, "REDEEM_");
        PointShopOrder order = pointShopOrderRepository.findById(id)
                .orElseThrow(() -> new ApiException("redemption not found", HttpStatus.NOT_FOUND));
        if (!order.getUser().getId().equals(userId)) {
            throw new ApiException("redemption not found", HttpStatus.NOT_FOUND);
        }
        return order;
    }

    private String resolveSelectedCharacterName(User user, UserMyPagePreference preference) {
        return buildCharacters(user, preference).stream()
                .filter(character -> Boolean.TRUE.equals(character.getSelected()))
                .findFirst()
                .map(MyPageCharacterCardDTO::getName)
                .orElse("조심스러운 거북이형");
    }

    private boolean resolvePushEnabled(User user, UserMyPagePreference preference) {
        if (preference.getPushEnabled() != null) {
            return preference.getPushEnabled();
        }
        return (user.getEmail() != null && !user.getEmail().isBlank()) || (user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank());
    }

    private int getBalance(User user) {
        return pointWalletRepository.findByUser_Id(user.getId()).map(PointWallet::getBalance).orElse(0);
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private String resolveRedeemReason(PointShopProduct product, int remaining, boolean hasInventory) {
        if (!"ACTIVE".equalsIgnoreCase(product.getStatus()) || !hasInventory) {
            return "재고가 없습니다";
        }
        if (remaining < 0) {
            return "보유 포인트가 부족합니다";
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

    private String toAgoLabel(java.time.LocalDateTime createdAt) {
        Duration duration = Duration.between(createdAt, java.time.LocalDateTime.now());
        long minutes = Math.max(1, duration.toMinutes());
        if (minutes < 60) return minutes + "분 전";
        long hours = duration.toHours();
        if (hours < 24) return hours + "시간 전";
        return duration.toDays() + "일 전";
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
