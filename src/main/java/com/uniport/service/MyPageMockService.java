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
import com.uniport.dto.MyPageExpDTO;
import com.uniport.dto.MyPageResponseDTO;
import com.uniport.dto.MyPageSettingsDTO;
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
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class MyPageMockService {

    private static final Set<String> ITEM_CATEGORIES = Set.of("CAFE", "CONVENIENCE", "DINING", "BAKERY", "MOVIE", "LIFE", "ETC");
    private static final Set<String> ITEM_SORTS = Set.of("POPULAR", "LOW_POINT", "HIGH_POINT");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;

    private final Map<String, ShopItemState> shopItems = new LinkedHashMap<>();
    private final Map<String, FriendProfileState> friendProfiles = new LinkedHashMap<>();
    private final ConcurrentHashMap<Long, Integer> userPoints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, LinkedHashMap<String, FriendRequestState>> sentFriendRequestsByUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, LinkedHashMap<String, RedemptionState>> redemptionsByUser = new ConcurrentHashMap<>();
    private final AtomicInteger redemptionSequence = new AtomicInteger(401);
    private final AtomicInteger requestSequence = new AtomicInteger(151);

    public MyPageMockService() {
        seedShopItems();
        seedFriendProfiles();
    }

    public MyPageResponseDTO getMyPage(User user) {
        return MyPageResponseDTO.builder()
                .user(MyPageUserDTO.builder()
                        .nickname(defaultNickname(user))
                        .profileImageUrl(user.getProfileImageUrl())
                        .level(15)
                        .investmentMbti(user.getInvestmentProfileResult() == null ? "균형 투자형" : user.getInvestmentProfileResult())
                        .character("균형 잡힌 판다")
                        .build())
                .exp(MyPageExpDTO.builder()
                        .currentExp(640)
                        .maxExp(1000)
                        .build())
                .summary(MyPageSummaryDTO.builder()
                        .learningTimeMinutes(760)
                        .currentStreak(5)
                        .build())
                .settings(MyPageSettingsDTO.builder()
                        .pushEnabled(Boolean.TRUE)
                        .build())
                .build();
    }

    public PointBalanceResponseDTO getPointBalance(User user) {
        return PointBalanceResponseDTO.builder()
                .pointBalance(getCurrentPoint(user))
                .build();
    }

    public ShopItemsResponseDTO getShopItems(String category, String sort, Integer page, Integer size) {
        String safeCategory = category == null || category.isBlank() ? null : category.toUpperCase(Locale.ROOT);
        String safeSort = sort == null || sort.isBlank() ? "POPULAR" : sort.toUpperCase(Locale.ROOT);
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 10 : Math.min(size, 20);

        if (safeCategory != null && !ITEM_CATEGORIES.contains(safeCategory)) {
            throw new ApiException("invalid category", HttpStatus.BAD_REQUEST);
        }
        if (!ITEM_SORTS.contains(safeSort)) {
            throw new ApiException("invalid sort", HttpStatus.BAD_REQUEST);
        }

        List<ShopItemDTO> filtered = shopItems.values().stream()
                .filter(item -> safeCategory == null || item.category().equals(safeCategory))
                .sorted(resolveItemComparator(safeSort))
                .map(this::toShopItemDto)
                .toList();

        int fromIndex = Math.min(safePage * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());

        return ShopItemsResponseDTO.builder()
                .items(filtered.subList(fromIndex, toIndex))
                .page(safePage)
                .size(safeSize)
                .hasNext(toIndex < filtered.size())
                .build();
    }

    public ShopRedemptionPreviewResponseDTO getRedemptionPreview(User user, String itemId) {
        ShopItemState item = getRequiredItem(itemId);
        int currentBalance = getCurrentPoint(user);
        int remaining = currentBalance - item.requiredPoint();
        boolean canRedeem = remaining >= 0 && "AVAILABLE".equals(item.stockStatus());

        return ShopRedemptionPreviewResponseDTO.builder()
                .item(toShopItemDto(item))
                .point(ShopPreviewPointDTO.builder()
                        .currentBalance(currentBalance)
                        .requiredPoint(item.requiredPoint())
                        .remainingBalance(Math.max(remaining, 0))
                        .build())
                .canRedeem(canRedeem)
                .reason(resolveRedeemReason(item, remaining))
                .build();
    }

    public ShopRedemptionResponseDTO redeem(User user, ShopRedemptionRequestDTO request) {
        if (request == null || request.getItemId() == null || request.getItemId().isBlank()) {
            throw new ApiException("itemId is required", HttpStatus.BAD_REQUEST);
        }

        ShopItemState item = getRequiredItem(request.getItemId());
        int currentPoint = getCurrentPoint(user);
        if (!"AVAILABLE".equals(item.stockStatus())) {
            throw new ApiException("item is sold out", HttpStatus.CONFLICT);
        }
        if (currentPoint < item.requiredPoint()) {
            throw new ApiException("not enough points", HttpStatus.CONFLICT);
        }

        int remainingPoint = currentPoint - item.requiredPoint();
        userPoints.put(user.getId(), remainingPoint);

        RedemptionState state = new RedemptionState(
                "REDEEM_" + redemptionSequence.getAndIncrement(),
                item.itemId(),
                item.requiredPoint(),
                remainingPoint,
                "COMPLETED",
                Instant.now(),
                LocalDate.now().plusDays(14),
                "카카오톡 발송",
                item.usageGuide(),
                item.brand() + " 앱 또는 매장에서 사용 가능"
        );
        getUserRedemptions(user).put(state.redemptionId(), state);

        return ShopRedemptionResponseDTO.builder()
                .redemptionId(state.redemptionId())
                .itemId(item.itemId())
                .usedPoint(item.requiredPoint())
                .remainingPoint(remainingPoint)
                .status(state.status())
                .createdAt(state.createdAt().toString())
                .build();
    }

    public ShopRedemptionListResponseDTO getRedemptions(User user) {
        List<ShopRedemptionListItemDTO> items = getUserRedemptions(user).values().stream()
                .sorted(Comparator.comparing(RedemptionState::createdAt).reversed())
                .map(this::toRedemptionListItem)
                .toList();
        return ShopRedemptionListResponseDTO.builder()
                .items(items)
                .build();
    }

    public ShopRedemptionDetailResponseDTO getRedemptionDetail(User user, String redemptionId) {
        RedemptionState state = getUserRedemptions(user).get(redemptionId);
        if (state == null) {
            throw new ApiException("redemption not found", HttpStatus.NOT_FOUND);
        }
        ShopItemState item = getRequiredItem(state.itemId());
        return ShopRedemptionDetailResponseDTO.builder()
                .redemptionId(state.redemptionId())
                .itemId(state.itemId())
                .brand(item.brand())
                .name(item.name())
                .imageUrl(item.imageUrl())
                .usedPoint(state.usedPoint())
                .expiresAt(state.expiresAt().format(DATE_FORMATTER))
                .expiresInDays(Math.max(0, (int) ChronoUnit.DAYS.between(LocalDate.now(), state.expiresAt())))
                .deliveryMethod(state.deliveryMethod())
                .usageGuide(state.usageGuide())
                .status(state.status())
                .statusLabel(toRedemptionStatusLabel(state.status()))
                .notice(state.notice())
                .build();
    }

    public FriendRequestResponseDTO requestFriend(User user, FriendRequestCreateDTO request) {
        if (request == null || request.getTargetUserId() == null || request.getTargetUserId().isBlank()) {
            throw new ApiException("targetUserId is required", HttpStatus.BAD_REQUEST);
        }
        if ("USER_ME".equalsIgnoreCase(request.getTargetUserId())) {
            throw new ApiException("cannot request yourself", HttpStatus.BAD_REQUEST);
        }
        FriendProfileState profile = friendProfiles.get(request.getTargetUserId());
        if (profile == null) {
            throw new ApiException("friend user not found", HttpStatus.NOT_FOUND);
        }

        LinkedHashMap<String, FriendRequestState> requests = getUserSentRequests(user);
        if (requests.containsKey(request.getTargetUserId())) {
            throw new ApiException("friend request already exists", HttpStatus.CONFLICT);
        }

        FriendRequestState state = new FriendRequestState(
                "REQ_" + requestSequence.getAndIncrement(),
                request.getTargetUserId(),
                profile.nickname(),
                profile.profileImageUrl(),
                profile.level(),
                profile.investmentProfileLabel(),
                "REQUESTED",
                Instant.now()
        );
        requests.put(request.getTargetUserId(), state);

        return FriendRequestResponseDTO.builder()
                .requestId(state.requestId())
                .targetUserId(state.userId())
                .status(state.status())
                .createdAt(state.createdAt().toString())
                .build();
    }

    public FriendsDashboardResponseDTO getFriendsDashboard(User user) {
        List<FriendRankingItemDTO> rankingItems = List.of(
                rankItem(1, "USER_101", "송서영", "https://cdn.example.com/user1.png", 15, 50000, 1),
                rankItem(2, "USER_102", "고윤서", "https://cdn.example.com/user2.png", 15, 50000, -1),
                rankItem(3, "USER_103", "박종원", "https://cdn.example.com/user3.png", 15, 50000, 2),
                rankItem(4, "USER_104", "곽명호", "https://cdn.example.com/user4.png", 15, 50000, 0),
                rankItem(5, "USER_105", "곽건", "https://cdn.example.com/user5.png", 15, 50000, 3)
        );

        FriendRankingItemDTO myRanking = FriendRankingItemDTO.builder()
                .rank(7)
                .userId("USER_ME")
                .nickname(defaultNickname(user))
                .profileImageUrl(user.getProfileImageUrl())
                .level(15)
                .xp(50000)
                .rankChange(2)
                .build();

        return FriendsDashboardResponseDTO.builder()
                .ranking(FriendRankingSectionDTO.builder()
                        .endDay(3)
                        .items(rankingItems)
                        .build())
                .myRanking(myRanking)
                .build();
    }

    public FriendListResponseDTO getFriends(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<FriendListItemDTO> items = friendProfiles.values().stream()
                .filter(FriendProfileState::friend)
                .filter(profile -> normalized.isBlank() || profile.nickname().toLowerCase(Locale.ROOT).contains(normalized))
                .map(profile -> FriendListItemDTO.builder()
                        .userId(profile.userId())
                        .nickname(profile.nickname())
                        .profileImageUrl(profile.profileImageUrl())
                        .level(profile.level())
                        .investmentProfileLabel(profile.investmentProfileLabel())
                        .currentXp(profile.currentXp())
                        .maxXp(profile.maxXp())
                        .relationLabel("친구")
                        .description("같이 공부하면 경험치가 2배!")
                        .build())
                .toList();
        return FriendListResponseDTO.builder().items(items).build();
    }

    public FriendRequestListResponseDTO getSentFriendRequests(User user) {
        List<FriendRequestListItemDTO> items = getUserSentRequests(user).values().stream()
                .sorted(Comparator.comparing(FriendRequestState::createdAt).reversed())
                .map(this::toFriendRequestListItem)
                .toList();
        return FriendRequestListResponseDTO.builder().items(items).build();
    }

    public FriendRequestListResponseDTO getReceivedFriendRequests(User user) {
        List<FriendRequestListItemDTO> items = List.of(
                FriendRequestListItemDTO.builder()
                        .requestId("REQ_201")
                        .userId("USER_302")
                        .nickname("강동현")
                        .profileImageUrl("https://cdn.example.com/friend-6.png")
                        .level(15)
                        .investmentProfileLabel("균형잡힌 판다형")
                        .requestedAgoLabel("30분 전")
                        .status("REQUESTED")
                        .build(),
                FriendRequestListItemDTO.builder()
                        .requestId("REQ_202")
                        .userId("USER_303")
                        .nickname("곽건")
                        .profileImageUrl("https://cdn.example.com/friend-7.png")
                        .level(15)
                        .investmentProfileLabel("균형잡힌 판다형")
                        .requestedAgoLabel("1일 전")
                        .status("REQUESTED")
                        .build()
        );
        return FriendRequestListResponseDTO.builder().items(items).build();
    }

    private Comparator<ShopItemState> resolveItemComparator(String sort) {
        return switch (sort) {
            case "LOW_POINT" -> Comparator.comparingInt(ShopItemState::requiredPoint);
            case "HIGH_POINT" -> Comparator.comparingInt(ShopItemState::requiredPoint).reversed();
            default -> Comparator.comparingInt(ShopItemState::popularity).reversed();
        };
    }

    private ShopItemState getRequiredItem(String itemId) {
        ShopItemState item = shopItems.get(itemId);
        if (item == null) {
            throw new ApiException("shop item not found", HttpStatus.NOT_FOUND);
        }
        return item;
    }

    private Integer getCurrentPoint(User user) {
        return userPoints.computeIfAbsent(user.getId(), ignored -> 3000);
    }

    private String resolveRedeemReason(ShopItemState item, int remaining) {
        if (!"AVAILABLE".equals(item.stockStatus())) {
            return "재고가 없습니다";
        }
        if (remaining < 0) {
            return "보유 포인트가 부족합니다";
        }
        return null;
    }

    private ShopItemDTO toShopItemDto(ShopItemState item) {
        return ShopItemDTO.builder()
                .itemId(item.itemId())
                .brand(item.brand())
                .name(item.name())
                .imageUrl(item.imageUrl())
                .requiredPoint(item.requiredPoint())
                .badge(item.badge())
                .stockStatus(item.stockStatus())
                .build();
    }

    private ShopRedemptionListItemDTO toRedemptionListItem(RedemptionState state) {
        ShopItemState item = getRequiredItem(state.itemId());
        return ShopRedemptionListItemDTO.builder()
                .redemptionId(state.redemptionId())
                .itemId(state.itemId())
                .brand(item.brand())
                .name(item.name())
                .imageUrl(item.imageUrl())
                .usedPoint(state.usedPoint())
                .requestedAgoLabel(toAgoLabel(state.createdAt()))
                .expiresAt(state.expiresAt().format(DATE_FORMATTER))
                .expiresInDays((int) ChronoUnit.DAYS.between(LocalDate.now(), state.expiresAt()))
                .status(state.status())
                .statusLabel(toRedemptionStatusLabel(state.status()))
                .build();
    }

    private FriendRequestListItemDTO toFriendRequestListItem(FriendRequestState state) {
        return FriendRequestListItemDTO.builder()
                .requestId(state.requestId())
                .userId(state.userId())
                .nickname(state.nickname())
                .profileImageUrl(state.profileImageUrl())
                .level(state.level())
                .investmentProfileLabel(state.investmentProfileLabel())
                .requestedAgoLabel(toAgoLabel(state.createdAt()))
                .status(state.status())
                .build();
    }

    private FriendRankingItemDTO rankItem(int rank, String userId, String nickname, String profileImageUrl, int level, int xp, int rankChange) {
        return FriendRankingItemDTO.builder()
                .rank(rank)
                .userId(userId)
                .nickname(nickname)
                .profileImageUrl(profileImageUrl)
                .level(level)
                .xp(xp)
                .rankChange(rankChange)
                .build();
    }

    private LinkedHashMap<String, FriendRequestState> getUserSentRequests(User user) {
        return sentFriendRequestsByUser.computeIfAbsent(user.getId(), ignored -> seedSentRequests());
    }

    private LinkedHashMap<String, RedemptionState> getUserRedemptions(User user) {
        return redemptionsByUser.computeIfAbsent(user.getId(), ignored -> seedRedemptions());
    }

    private String defaultNickname(User user) {
        return user.getNickname() == null || user.getNickname().isBlank() ? "유니포트" : user.getNickname();
    }

    private String toAgoLabel(Instant createdAt) {
        long minutes = Math.max(1, Duration.between(createdAt, Instant.now()).toMinutes());
        if (minutes < 60) {
            return minutes + "분 전";
        }
        long hours = Math.max(1, minutes / 60);
        if (hours < 24) {
            return hours + "시간 전";
        }
        return Math.max(1, hours / 24) + "일 전";
    }

    private String toRedemptionStatusLabel(String status) {
        return switch (status) {
            case "EXPIRED" -> "기한 만료";
            case "COMPLETED" -> "사용 가능";
            default -> "처리중";
        };
    }

    private void seedShopItems() {
        shopItems.put("ITEM_101", new ShopItemState("ITEM_101", "DINING", "BHC", "후라이드 치킨 + 콜라 1.25L", "https://cdn.example.com/item-bhc.png", 3000, "BEST", "AVAILABLE", 98, "매장 직원에게 바코드를 보여주세요. 유효기간 내 1회 사용 가능합니다."));
        shopItems.put("ITEM_102", new ShopItemState("ITEM_102", "CONVENIENCE", "스타벅스", "모바일 상품권 5천원권", "https://cdn.example.com/item-starbucks.png", 5000, null, "AVAILABLE", 92, "스타벅스 앱 또는 매장에서 바코드를 제시해 사용해 주세요."));
        shopItems.put("ITEM_103", new ShopItemState("ITEM_103", "CAFE", "투썸플레이스", "아메리카노 R", "https://cdn.example.com/item-twosome.png", 4500, "HOT", "AVAILABLE", 88, "제휴 매장에서 모바일 쿠폰 번호를 제시하면 사용할 수 있습니다."));
        shopItems.put("ITEM_104", new ShopItemState("ITEM_104", "MOVIE", "CGV", "영화 관람권", "https://cdn.example.com/item-cgv.png", 6500, null, "AVAILABLE", 82, "CGV 앱 등록 후 예매 시 사용 가능합니다."));
        shopItems.put("ITEM_105", new ShopItemState("ITEM_105", "LIFE", "올리브영", "모바일 상품권 1만원권", "https://cdn.example.com/item-oliveyoung.png", 9000, null, "AVAILABLE", 79, "온라인몰 및 오프라인 매장에서 사용 가능합니다."));
        shopItems.put("ITEM_106", new ShopItemState("ITEM_106", "ETC", "배달의민족", "배민 상품권 5천원권", "https://cdn.example.com/item-baemin.png", 5000, null, "AVAILABLE", 75, "배달의민족 앱 선물함에서 등록 후 사용 가능합니다."));
    }

    private void seedFriendProfiles() {
        friendProfiles.put("USER_201", new FriendProfileState("USER_201", "고윤서", "https://cdn.example.com/friend-1.png", 15, "균형잡힌 판다형", 640, 1000, true));
        friendProfiles.put("USER_202", new FriendProfileState("USER_202", "곽명호", "https://cdn.example.com/friend-2.png", 15, "균형잡힌 판다형", 640, 1000, true));
        friendProfiles.put("USER_203", new FriendProfileState("USER_203", "박희경", "https://cdn.example.com/friend-3.png", 15, "균형잡힌 판다형", 640, 1000, true));
        friendProfiles.put("USER_204", new FriendProfileState("USER_204", "송서영", "https://cdn.example.com/friend-4.png", 15, "균형잡힌 판다형", 640, 1000, true));
        friendProfiles.put("USER_205", new FriendProfileState("USER_205", "강동현", "https://cdn.example.com/friend-5.png", 15, "균형잡힌 판다형", 640, 1000, false));
        friendProfiles.put("USER_206", new FriendProfileState("USER_206", "곽건", "https://cdn.example.com/friend-6.png", 15, "균형잡힌 판다형", 640, 1000, false));
    }

    private LinkedHashMap<String, FriendRequestState> seedSentRequests() {
        LinkedHashMap<String, FriendRequestState> requests = new LinkedHashMap<>();
        requests.put("USER_205", new FriendRequestState("REQ_111", "USER_205", "강동현", "https://cdn.example.com/friend-5.png", 15, "균형잡힌 판다형", "REQUESTED", Instant.now().minus(Duration.ofHours(1))));
        requests.put("USER_206", new FriendRequestState("REQ_112", "USER_206", "곽건", "https://cdn.example.com/friend-6.png", 15, "균형잡힌 판다형", "REQUESTED", Instant.now().minus(Duration.ofHours(3))));
        return requests;
    }

    private LinkedHashMap<String, RedemptionState> seedRedemptions() {
        LinkedHashMap<String, RedemptionState> redemptions = new LinkedHashMap<>();
        redemptions.put("REDEEM_301", new RedemptionState("REDEEM_301", "ITEM_101", 3000, 0, "COMPLETED", Instant.now().minus(Duration.ofMinutes(30)), LocalDate.now().plusDays(14), "카카오톡 발송", getRequiredItem("ITEM_101").usageGuide(), "BHC 앱 또는 매장에서 사용 가능"));
        redemptions.put("REDEEM_302", new RedemptionState("REDEEM_302", "ITEM_102", 5000, 0, "COMPLETED", Instant.now().minus(Duration.ofHours(1)), LocalDate.now().plusDays(7), "카카오톡 발송", getRequiredItem("ITEM_102").usageGuide(), "스타벅스 앱 또는 매장에서 사용 가능"));
        redemptions.put("REDEEM_303", new RedemptionState("REDEEM_303", "ITEM_101", 3000, 0, "EXPIRED", Instant.now().minus(Duration.ofDays(1)), LocalDate.now().minusDays(1), "카카오톡 발송", getRequiredItem("ITEM_101").usageGuide(), "유효기간이 지나 재발급이 필요합니다."));
        return redemptions;
    }

    private record ShopItemState(
            String itemId,
            String category,
            String brand,
            String name,
            String imageUrl,
            Integer requiredPoint,
            String badge,
            String stockStatus,
            Integer popularity,
            String usageGuide
    ) {
    }

    private record FriendProfileState(
            String userId,
            String nickname,
            String profileImageUrl,
            Integer level,
            String investmentProfileLabel,
            Integer currentXp,
            Integer maxXp,
            boolean friend
    ) {
    }

    private record FriendRequestState(
            String requestId,
            String userId,
            String nickname,
            String profileImageUrl,
            Integer level,
            String investmentProfileLabel,
            String status,
            Instant createdAt
    ) {
    }

    private record RedemptionState(
            String redemptionId,
            String itemId,
            Integer usedPoint,
            Integer remainingPoint,
            String status,
            Instant createdAt,
            LocalDate expiresAt,
            String deliveryMethod,
            String usageGuide,
            String notice
    ) {
    }
}
