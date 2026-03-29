package com.uniport.service;

import com.uniport.dto.FriendRankingItemDTO;
import com.uniport.dto.FriendRankingSectionDTO;
import com.uniport.dto.FriendRequestCreateDTO;
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
import com.uniport.dto.ShopRedemptionPreviewResponseDTO;
import com.uniport.dto.ShopRedemptionRequestDTO;
import com.uniport.dto.ShopRedemptionResponseDTO;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MyPageMockService {

    private static final Set<String> ITEM_CATEGORIES = Set.of("CAFE", "CONVENIENCE", "BAKERY");
    private static final Set<String> ITEM_SORTS = Set.of("POPULAR", "LOW_POINT", "HIGH_POINT");

    private final Map<String, ShopItemState> shopItems = new LinkedHashMap<>();
    private final ConcurrentHashMap<Long, Integer> userPoints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> friendRequestsByUser = new ConcurrentHashMap<>();
    private final AtomicInteger redemptionSequence = new AtomicInteger(201);
    private final AtomicInteger requestSequence = new AtomicInteger(101);

    public MyPageMockService() {
        seedShopItems();
    }

    public MyPageResponseDTO getMyPage(User user) {
        return MyPageResponseDTO.builder()
                .user(MyPageUserDTO.builder()
                        .nickname(defaultNickname(user))
                        .profileImageUrl(user.getProfileImageUrl())
                        .level(12)
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
                .filter(item -> safeCategory == null || item.category.equals(safeCategory))
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
        int remaining = currentBalance - item.requiredPoint;
        boolean canRedeem = remaining >= 0 && "AVAILABLE".equals(item.stockStatus);

        return ShopRedemptionPreviewResponseDTO.builder()
                .item(toShopItemDto(item))
                .point(ShopPreviewPointDTO.builder()
                        .currentBalance(currentBalance)
                        .requiredPoint(item.requiredPoint)
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
        if (!"AVAILABLE".equals(item.stockStatus)) {
            throw new ApiException("item is sold out", HttpStatus.CONFLICT);
        }
        if (currentPoint < item.requiredPoint) {
            throw new ApiException("not enough points", HttpStatus.CONFLICT);
        }

        int remainingPoint = currentPoint - item.requiredPoint;
        userPoints.put(user.getId(), remainingPoint);

        return ShopRedemptionResponseDTO.builder()
                .redemptionId("REDEEM_" + redemptionSequence.getAndIncrement())
                .itemId(item.itemId)
                .usedPoint(item.requiredPoint)
                .remainingPoint(remainingPoint)
                .status("COMPLETED")
                .createdAt(Instant.now().toString())
                .build();
    }

    public FriendRequestResponseDTO requestFriend(User user, FriendRequestCreateDTO request) {
        if (request == null || request.getTargetUserId() == null || request.getTargetUserId().isBlank()) {
            throw new ApiException("targetUserId is required", HttpStatus.BAD_REQUEST);
        }
        if ("USER_ME".equalsIgnoreCase(request.getTargetUserId())) {
            throw new ApiException("cannot request yourself", HttpStatus.BAD_REQUEST);
        }

        Set<String> requested = friendRequestsByUser.computeIfAbsent(user.getId(), ignored -> ConcurrentHashMap.newKeySet());
        if (!requested.add(request.getTargetUserId())) {
            throw new ApiException("friend request already exists", HttpStatus.CONFLICT);
        }

        return FriendRequestResponseDTO.builder()
                .requestId("REQ_" + requestSequence.getAndIncrement())
                .targetUserId(request.getTargetUserId())
                .status("REQUESTED")
                .createdAt(Instant.now().toString())
                .build();
    }

    public FriendsDashboardResponseDTO getFriendsDashboard(User user) {
        List<FriendRankingItemDTO> rankingItems = List.of(
                rankItem(1, "USER_101", "김지수", "https://cdn.example.com/user1.png", 42, 12450, 1),
                rankItem(2, "USER_102", "최민호", "https://cdn.example.com/user2.png", 38, 11200, -1),
                rankItem(3, "USER_103", "이민지", "https://cdn.example.com/user3.png", 35, 9800, 2)
        );

        FriendRankingItemDTO myRanking = FriendRankingItemDTO.builder()
                .rank(12)
                .userId("USER_ME")
                .nickname(defaultNickname(user))
                .profileImageUrl(user.getProfileImageUrl())
                .level(29)
                .xp(5400)
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
        return userPoints.computeIfAbsent(user.getId(), ignored -> 5400);
    }

    private String resolveRedeemReason(ShopItemState item, int remaining) {
        if (!"AVAILABLE".equals(item.stockStatus)) {
            return "재고가 없습니다";
        }
        if (remaining < 0) {
            return "보유 포인트가 부족합니다";
        }
        return null;
    }

    private ShopItemDTO toShopItemDto(ShopItemState item) {
        return ShopItemDTO.builder()
                .itemId(item.itemId)
                .brand(item.brand)
                .name(item.name)
                .imageUrl(item.imageUrl)
                .requiredPoint(item.requiredPoint)
                .badge(item.badge)
                .stockStatus(item.stockStatus)
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

    private String defaultNickname(User user) {
        return user.getNickname() == null || user.getNickname().isBlank() ? "유니포터" : user.getNickname();
    }

    private void seedShopItems() {
        shopItems.put("ITEM_101", new ShopItemState("ITEM_101", "CAFE", "스타벅스", "아이스 카페 아메리카노 T", "https://cdn.example.com/item1.png", 4500, "BEST", "AVAILABLE", 95));
        shopItems.put("ITEM_102", new ShopItemState("ITEM_102", "CONVENIENCE", "GS25", "모바일 상품권 5천원권", "https://cdn.example.com/item2.png", 5000, null, "AVAILABLE", 88));
        shopItems.put("ITEM_103", new ShopItemState("ITEM_103", "BAKERY", "파리바게뜨", "초코 조각 케이크", "https://cdn.example.com/item3.png", 6200, "HOT", "AVAILABLE", 84));
        shopItems.put("ITEM_104", new ShopItemState("ITEM_104", "CAFE", "투썸플레이스", "카페라떼 R", "https://cdn.example.com/item4.png", 4800, null, "SOLD_OUT", 70));
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
            Integer popularity
    ) {
    }
}
