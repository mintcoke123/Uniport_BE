package com.uniport.service;

import com.uniport.entity.MatchingRoom;
import com.uniport.entity.MatchingRoomMember;
import com.uniport.entity.TeamAccount;
import com.uniport.entity.TeamHolding;
import com.uniport.entity.User;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import com.uniport.repository.TeamAccountRepository;
import com.uniport.repository.TeamHoldingRepository;
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
 * 랭킹: 시작된 매칭방(팀)별 현재 평가액·수익률 계산 후 순위.
 */
@Service
public class RankingService {

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

    /** 시작된 모든 팀의 랭킹 (평가액 내림차순). */
    public List<Map<String, Object>> getAllGroupsRanking() {
        List<MatchingRoom> started = matchingRoomRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> "started".equals(r.getStatus()))
                .collect(Collectors.toList());
        List<Map<String, Object>> list = new ArrayList<>();
        for (MatchingRoom room : started) {
            BigDecimal totalValue = computeTotalValue(room.getId());
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
        return list;
    }

    /**
     * 경쟁 팀 목록 (대회/홈/관리자용). teamId, groupName, totalValue, investmentAmount, profitLoss, profitLossPercentage, rank, isMyTeam.
     * competitionId는 현재 미사용(전체 시작된 팀 반환).
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

    /** 현재 사용자 팀의 랭킹 정보. 팀 미소속 시 null. */
    public Map<String, Object> getMyGroupRanking(User user) {
        Long teamId = parseTeamId(user);
        // User.teamId가 없어도 참가 중인 'started' 방이 있으면 그 방 기준으로 순위 반환
        if (teamId == null && user != null) {
            teamId = findStartedRoomIdByMember(user.getId());
        }
        if (teamId == null) return null;
        List<Map<String, Object>> all = getAllGroupsRanking();
        int rank = 0;
        Map<String, Object> my = null;
        for (int i = 0; i < all.size(); i++) {
            if (teamId.equals(((Number) all.get(i).get("id")).longValue())) {
                rank = i + 1;
                my = new HashMap<>(all.get(i));
                break;
            }
        }
        if (my == null) return null;
        my.put("rank", rank);
        return my;
    }

    private BigDecimal computeTotalValue(Long teamId) {
        BigDecimal cash = teamAccountRepository.findByTeamId(teamId)
                .map(TeamAccount::getCashBalance)
                .orElse(INITIAL_TEAM_BALANCE);
        BigDecimal holdingsValue = BigDecimal.ZERO;
        for (TeamHolding h : teamHoldingRepository.findByTeamId(teamId)) {
            BigDecimal price = h.getAveragePurchasePrice();
            try {
                price = kisApiService.getStockPrice(h.getStockCode()).getCurrentPrice();
            } catch (Exception ignored) {
            }
            holdingsValue = holdingsValue.add(price.multiply(BigDecimal.valueOf(h.getQuantity())));
        }
        return cash.add(holdingsValue);
    }

    /** User.teamId가 없을 때, 참가 중인 'started' 방이 있으면 그 방 ID 반환. */
    private Long findStartedRoomIdByMember(Long userId) {
        if (userId == null) return null;
        return matchingRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(userId).stream()
                .map(MatchingRoomMember::getMatchingRoom)
                .filter(r -> r != null && "started".equals(r.getStatus()))
                .map(MatchingRoom::getId)
                .findFirst()
                .orElse(null);
    }

    private static Long parseTeamId(User user) {
        String tid = user != null ? user.getTeamId() : null;
        if (tid == null || tid.isBlank() || !tid.startsWith("team-")) return null;
        try {
            return Long.parseLong(tid.substring(5));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
