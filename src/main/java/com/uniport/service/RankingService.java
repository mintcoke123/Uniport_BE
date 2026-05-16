package com.uniport.service;

import com.uniport.dto.StockPriceDTO;
import com.uniport.entity.MatchingRoom;
import com.uniport.entity.MatchingRoomMember;
import com.uniport.entity.TeamAccount;
import com.uniport.entity.TeamHolding;
import com.uniport.entity.User;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import com.uniport.repository.TeamAccountRepository;
import com.uniport.repository.TeamHoldingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 그룹 랭킹 계산 서비스.
 */
@Service
public class RankingService {

    private static final Logger log = LoggerFactory.getLogger(RankingService.class);
    private static final BigDecimal INITIAL_TEAM_BALANCE = new BigDecimal("10000000");

    private final MatchingRoomRepository matchingRoomRepository;
    private final MatchingRoomMemberRepository matchingRoomMemberRepository;
    private final TeamAccountRepository teamAccountRepository;
    private final TeamHoldingRepository teamHoldingRepository;
    private final KisApiService kisApiService;

    public RankingService(MatchingRoomRepository matchingRoomRepository,
                          MatchingRoomMemberRepository matchingRoomMemberRepository,
                          TeamAccountRepository teamAccountRepository,
                          TeamHoldingRepository teamHoldingRepository,
                          KisApiService kisApiService) {
        this.matchingRoomRepository = matchingRoomRepository;
        this.matchingRoomMemberRepository = matchingRoomMemberRepository;
        this.teamAccountRepository = teamAccountRepository;
        this.teamHoldingRepository = teamHoldingRepository;
        this.kisApiService = kisApiService;
    }

    public List<Map<String, Object>> getAllGroupsRanking() {
        return buildGroupRankings(true);
    }

    public List<Map<String, Object>> getAllGroupsRankingSnapshot() {
        return buildGroupRankings(false);
    }

    /**
     * 경쟁 중인 팀 목록.
     */
    public List<Map<String, Object>> getCompetingTeams(Long competitionId, User user) {
        List<Map<String, Object>> all = getAllGroupsRanking();
        Long myTeamId = user != null ? parseTeamId(user) : null;
        if (myTeamId == null && user != null) {
            myTeamId = findStartedRoomIdByMember(user.getId());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Map<String, Object> m = all.get(i);
            Long id = ((Number) m.get("id")).longValue();
            BigDecimal currentAssets = (BigDecimal) m.get("currentAssets");
            BigDecimal profitRate = (BigDecimal) m.get("profitRate");
            BigDecimal profitLoss = currentAssets.subtract(INITIAL_TEAM_BALANCE);
            double profitLossPercentage = profitRate.multiply(BigDecimal.valueOf(100)).doubleValue();
            boolean isMyTeam = myTeamId != null && myTeamId.equals(id);
            Map<String, Object> item = new HashMap<>();
            item.put("teamId", "team-" + id);
            item.put("groupName", m.get("groupName"));
            item.put("totalValue", currentAssets);
            item.put("investmentAmount", INITIAL_TEAM_BALANCE);
            item.put("profitLoss", profitLoss);
            item.put("profitLossPercentage", profitLossPercentage);
            item.put("rank", i + 1);
            item.put("isMyTeam", isMyTeam);
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> getMyGroupRanking(User user) {
        Long teamId = resolveUserTeamId(user);
        if (teamId == null) {
            return null;
        }
        return getMyGroupRanking(teamId, getAllGroupsRanking());
    }

    public Map<String, Object> getMyGroupRanking(User user, List<Map<String, Object>> all) {
        Long teamId = resolveUserTeamId(user);
        if (teamId == null) {
            return null;
        }
        return getMyGroupRanking(teamId, all);
    }

    private Map<String, Object> getMyGroupRanking(Long teamId, List<Map<String, Object>> all) {
        if (teamId == null) {
            return null;
        }
        if (all == null || all.isEmpty()) {
            return null;
        }

        for (int i = 0; i < all.size(); i++) {
            Map<String, Object> candidate = all.get(i);
            if (teamId.equals(((Number) candidate.get("id")).longValue())) {
                Map<String, Object> my = new HashMap<>(candidate);
                my.put("rank", i + 1);
                return my;
            }
        }
        return null;
    }

    private Long resolveUserTeamId(User user) {
        Long teamId = parseTeamId(user);
        if (teamId == null && user != null) {
            teamId = findStartedRoomIdByMember(user.getId());
        }
        return teamId;
    }

    private List<Map<String, Object>> buildGroupRankings(boolean allowNetworkPriceFetch) {
        long startedAt = System.currentTimeMillis();
        List<MatchingRoom> started = matchingRoomRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> "started".equals(r.getStatus()))
                .collect(Collectors.toList());
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, BigDecimal> resolvedPrices = new HashMap<>();

        for (MatchingRoom room : started) {
            BigDecimal totalValue = computeTotalValue(room.getId(), allowNetworkPriceFetch, resolvedPrices);
            BigDecimal profitRate = INITIAL_TEAM_BALANCE.compareTo(BigDecimal.ZERO) != 0
                    ? totalValue.subtract(INITIAL_TEAM_BALANCE).divide(INITIAL_TEAM_BALANCE, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            Map<String, Object> map = new HashMap<>();
            map.put("id", room.getId());
            map.put("groupName", room.getName() != null ? room.getName() : "팀 " + room.getId());
            map.put("currentAssets", totalValue);
            map.put("profitRate", profitRate);
            list.add(map);
        }

        list.sort(Comparator.<Map<String, Object>, BigDecimal>comparing(m -> (BigDecimal) m.get("currentAssets")).reversed());
        log.info("[ranking] completed mode={} startedRooms={} elapsedMs={}",
                allowNetworkPriceFetch ? "LIVE" : "SNAPSHOT",
                started.size(),
                System.currentTimeMillis() - startedAt);
        return list;
    }

    private BigDecimal computeTotalValue(Long teamId,
                                         boolean allowNetworkPriceFetch,
                                         Map<String, BigDecimal> resolvedPrices) {
        long startedAt = System.currentTimeMillis();
        BigDecimal cash = teamAccountRepository.findByTeamId(teamId)
                .map(TeamAccount::getCashBalance)
                .orElse(INITIAL_TEAM_BALANCE);
        BigDecimal holdingsValue = BigDecimal.ZERO;

        for (TeamHolding holding : teamHoldingRepository.findByTeamId(teamId)) {
            BigDecimal price = resolveHoldingPrice(teamId, holding, allowNetworkPriceFetch, resolvedPrices);
            holdingsValue = holdingsValue.add(price.multiply(BigDecimal.valueOf(holding.getQuantity())));
        }

        long elapsedMs = System.currentTimeMillis() - startedAt;
        if (elapsedMs >= 1000) {
            log.warn("[ranking] slow team valuation teamId={} mode={} elapsedMs={}",
                    teamId,
                    allowNetworkPriceFetch ? "LIVE" : "SNAPSHOT",
                    elapsedMs);
        }
        return cash.add(holdingsValue);
    }

    private BigDecimal resolveHoldingPrice(Long teamId,
                                           TeamHolding holding,
                                           boolean allowNetworkPriceFetch,
                                           Map<String, BigDecimal> resolvedPrices) {
        String stockCode = holding.getStockCode();
        if (stockCode != null) {
            BigDecimal cachedResolvedPrice = resolvedPrices.get(stockCode);
            if (cachedResolvedPrice != null) {
                return cachedResolvedPrice;
            }
        }

        BigDecimal fallbackPrice = holding.getAveragePurchasePrice() != null
                ? holding.getAveragePurchasePrice()
                : BigDecimal.ZERO;

        try {
            BigDecimal resolvedPrice;
            if (allowNetworkPriceFetch) {
                long startedAt = System.currentTimeMillis();
                StockPriceDTO dto = kisApiService.getStockPrice(stockCode);
                resolvedPrice = dto.getCurrentPrice() != null ? dto.getCurrentPrice() : fallbackPrice;
                long elapsedMs = System.currentTimeMillis() - startedAt;
                if (elapsedMs >= 1000) {
                    log.warn("[ranking] slow stock price lookup teamId={} stockCode={} elapsedMs={}",
                            teamId, stockCode, elapsedMs);
                }
            } else {
                resolvedPrice = kisApiService.getCachedStockPrice(stockCode)
                        .map(StockPriceDTO::getCurrentPrice)
                        .filter(price -> price != null)
                        .orElse(fallbackPrice);
            }

            if (stockCode != null) {
                resolvedPrices.put(stockCode, resolvedPrice);
            }
            return resolvedPrice;
        } catch (Exception ex) {
            log.warn("[ranking] fallback price used teamId={} stockCode={} mode={} reason={}",
                    teamId,
                    stockCode,
                    allowNetworkPriceFetch ? "LIVE" : "SNAPSHOT",
                    ex.getMessage());
            if (stockCode != null) {
                resolvedPrices.put(stockCode, fallbackPrice);
            }
            return fallbackPrice;
        }
    }

    private Long findStartedRoomIdByMember(Long userId) {
        if (userId == null) {
            return null;
        }
        return matchingRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(userId).stream()
                .map(MatchingRoomMember::getMatchingRoom)
                .filter(r -> r != null && "started".equals(r.getStatus()))
                .map(MatchingRoom::getId)
                .findFirst()
                .orElse(null);
    }

    private static Long parseTeamId(User user) {
        String tid = user != null ? user.getTeamId() : null;
        if (tid == null || tid.isBlank() || !tid.startsWith("team-")) {
            return null;
        }
        try {
            return Long.parseLong(tid.substring(5));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
