package com.uniport.service;

import com.uniport.dto.CompetitionDataDTO;
import com.uniport.dto.GroupInsightConsensusDTO;
import com.uniport.dto.GroupInsightsResponseDTO;
import com.uniport.dto.HomeActiveMatchDTO;
import com.uniport.dto.HomeCompetitionSummaryDTO;
import com.uniport.dto.HomeInvestmentSummaryDTO;
import com.uniport.dto.HomeMyGroupRankingDTO;
import com.uniport.dto.MockInvestingSummaryResponseDTO;
import com.uniport.dto.MyInvestmentResponseDTO;
import com.uniport.dto.StockVisualDTO;
import com.uniport.dto.TopGroupInsightDTO;
import com.uniport.entity.Vote;
import com.uniport.entity.User;
import com.uniport.repository.VoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HomeDataService {

    private static final Logger log = LoggerFactory.getLogger(HomeDataService.class);
    private static final int MAX_TOP_CONSENSUS_ITEMS = 3;
    private static final int MAX_INSIGHT_VOTES = 20;

    private final MatchingRoomService matchingRoomService;
    private final MeService meService;
    private final RankingService rankingService;
    private final CompetitionService competitionService;
    private final CompetitionParticipationService competitionParticipationService;
    private final VoteRepository voteRepository;
    private final StockVisualAssetResolver stockVisualAssetResolver;
    private final StockSymbolLogoUrlResolver stockSymbolLogoUrlResolver;

    public HomeDataService(MatchingRoomService matchingRoomService,
                           MeService meService,
                           RankingService rankingService,
                           CompetitionService competitionService,
                           CompetitionParticipationService competitionParticipationService,
                           VoteRepository voteRepository,
                           StockVisualAssetResolver stockVisualAssetResolver,
                           StockSymbolLogoUrlResolver stockSymbolLogoUrlResolver) {
        this.matchingRoomService = matchingRoomService;
        this.meService = meService;
        this.rankingService = rankingService;
        this.competitionService = competitionService;
        this.competitionParticipationService = competitionParticipationService;
        this.voteRepository = voteRepository;
        this.stockVisualAssetResolver = stockVisualAssetResolver;
        this.stockSymbolLogoUrlResolver = stockSymbolLogoUrlResolver;
    }

    public MockInvestingSummaryResponseDTO getSummary(User user) {
        long startedAt = System.currentTimeMillis();
        Long userId = user != null ? user.getId() : null;
        log.info("[home-summary] start userId={}", userId);

        long stepStartedAt = System.currentTimeMillis();
        log.info("[home-summary] step=meInvestment start userId={}", userId);
        MyInvestmentResponseDTO investment = meService.getMyInvestment(user);
        log.info("[home-summary] step=meInvestment completed userId={} elapsedMs={}",
                userId,
                System.currentTimeMillis() - stepStartedAt);
        CompetitionDataDTO competition = investment.getCompetitionData();

        stepStartedAt = System.currentTimeMillis();
        log.info("[home-summary] step=myRanking start userId={}", userId);
        Map<String, Object> myRanking = rankingService.getMyGroupRanking(user);
        log.info("[home-summary] step=myRanking completed userId={} present={} elapsedMs={}",
                userId,
                myRanking != null,
                System.currentTimeMillis() - stepStartedAt);

        stepStartedAt = System.currentTimeMillis();
        log.info("[home-summary] step=rooms start userId={}", userId);
        List<Map<String, Object>> rooms = user != null ? matchingRoomService.listRoomsJoinedBy(user) : List.of();
        log.info("[home-summary] step=rooms completed userId={} count={} elapsedMs={}",
                userId,
                rooms.size(),
                System.currentTimeMillis() - stepStartedAt);
        Map<String, Object> activeRoom = rooms.isEmpty() ? null : rooms.get(0);

        MockInvestingSummaryResponseDTO response = MockInvestingSummaryResponseDTO.builder()
                .activeMatch(HomeActiveMatchDTO.builder()
                        .roomId(parseRoomId(activeRoom))
                        .title(activeRoom != null ? stringValue(activeRoom.get("name")) : null)
                        .status(activeRoom != null ? normalizeRoomStatus(stringValue(activeRoom.get("status"))) : null)
                        .startable(activeRoom != null && "waiting".equalsIgnoreCase(stringValue(activeRoom.get("status"))))
                        .build())
                .ongoingCompetition(HomeCompetitionSummaryDTO.builder()
                        .id(null)
                        .name(competition != null ? competition.getName() : null)
                        .endDate(competition != null ? competition.getEndDate() : null)
                        .build())
                .myInvestment(HomeInvestmentSummaryDTO.builder()
                        .totalAssets(investment.getInvestmentData() != null ? investment.getInvestmentData().getTotalAssets() : BigDecimal.ZERO)
                        .profitLoss(investment.getInvestmentData() != null ? investment.getInvestmentData().getProfitLoss() : BigDecimal.ZERO)
                        .profitLossRate(investment.getInvestmentData() != null ? investment.getInvestmentData().getProfitLossPercentage() : BigDecimal.ZERO)
                        .build())
                .myGroupRanking(HomeMyGroupRankingDTO.builder()
                        .rank(asInteger(myRanking, "rank"))
                        .groupId(asLong(myRanking, "id"))
                        .groupName(myRanking != null ? stringValue(myRanking.get("groupName")) : null)
                        .build())
                .build();
        log.info("[home-summary] completed userId={} elapsedMs={}",
                userId,
                System.currentTimeMillis() - startedAt);
        return response;
    }

    @Transactional(readOnly = true)
    public GroupInsightsResponseDTO getGroupInsights() {
        List<Map<String, Object>> rankings = rankingService.getAllGroupsRankingSnapshot();
        if (rankings.isEmpty()) {
            return GroupInsightsResponseDTO.builder()
                    .topConsensus(List.of())
                    .topGroup(null)
                    .build();
        }

        Map<String, Object> topRanking = rankings.get(0);
        Long topGroupId = asLong(topRanking, "id");
        String topGroupName = stringValue(topRanking.get("groupName"));
        BigDecimal topReturnRate = percentRate(topRanking.get("profitRate"));
        List<Vote> votes = topGroupId != null
                ? voteRepository.findByRoomIdOrderByCreatedAtDesc(topGroupId).stream()
                        .filter(this::hasInsightStock)
                        .limit(MAX_INSIGHT_VOTES)
                        .toList()
                : List.of();
        Vote latestReasonVote = votes.stream()
                .filter(vote -> vote.getReason() != null && !vote.getReason().isBlank())
                .findFirst()
                .orElse(votes.isEmpty() ? null : votes.get(0));

        return GroupInsightsResponseDTO.builder()
                .topConsensus(buildConsensus(votes, topReturnRate))
                .topGroup(TopGroupInsightDTO.builder()
                        .groupId(topGroupId)
                        .groupName(topGroupName)
                        .dailyReturnRate(topReturnRate)
                        .topPick(latestReasonVote != null ? displayStockName(latestReasonVote) : null)
                        .comment(buildInsightComment(topGroupName, latestReasonVote))
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getGroupMatchingDashboard(User user) {
        long startedAt = System.currentTimeMillis();
        Long userId = user != null ? user.getId() : null;
        log.info("[home-dashboard] start userId={}", userId);

        List<Map<String, Object>> rankings = rankingService.getAllGroupsRankingSnapshot();
        Map<String, Object> myGroupRanking = user != null ? rankingService.getMyGroupRanking(user, rankings) : null;

        List<Map<String, Object>> rankingPreview = new ArrayList<>();
        for (int i = 0; i < Math.min(rankings.size(), 5); i++) {
            Map<String, Object> item = rankings.get(i);
            rankingPreview.add(Map.of(
                    "rank", i + 1,
                    "groupId", item.get("id"),
                    "groupName", stringValue(item.get("groupName")),
                    "totalAssets", item.get("currentAssets"),
                    "profitRate", item.get("profitRate")
            ));
        }

        String participantTeamId = resolveParticipantTeamId(user);
        List<Map<String, Object>> upcomingCards = competitionService.findByStatus("upcoming").stream()
                .limit(3)
                .map(competition -> Map.<String, Object>of(
                        "competitionId", competition.getId(),
                        "name", competition.getName(),
                        "statusLabel", "참가 신청",
                        "daysRemaining", Math.max(0, competitionService.daysRemaining(competition.getEndDate())),
                        "startDate", competition.getStartDate(),
                        "endDate", competition.getEndDate(),
                        "application", competitionParticipationService.getApplicationStatus(competition.getId(), user, participantTeamId)
                ))
                .toList();

        GroupInsightsResponseDTO insights = getGroupInsights();
        BigDecimal myProfitRate = extractMyProfitRate(user, myGroupRanking);
        BigDecimal unlockTarget = new BigDecimal("5.0");
        boolean unlocked = myProfitRate.compareTo(unlockTarget) >= 0;
        List<Map<String, Object>> myApplications = user != null
                ? competitionParticipationService.getMyApplications(user)
                : List.of();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("heroCards", List.of(
                Map.of(
                        "type", "GROUP_MATCH",
                        "title", "그룹 매칭",
                        "description", "관심사 기반 친구 매칭",
                        "ctaLabel", "바로 시작하기"
                ),
                Map.of(
                        "type", "TOURNAMENT",
                        "title", "토너먼트 대회",
                        "description", "실전형 수익률 경쟁",
                        "ctaLabel", "대회 둘러보기"
                )
        ));
        body.put("topConsensus", insights.getTopConsensus());
        body.put("topGroupInsight", insights.getTopGroup());
        body.put("upcomingTournaments", upcomingCards);
        body.put("realtimeRanking", rankingPreview);
        body.put("myGroupRanking", myGroupRanking);
        body.put("insightUnlock", Map.of(
                "unlocked", unlocked,
                "targetProfitRate", unlockTarget,
                "currentProfitRate", myProfitRate,
                "title", unlocked ? "상위 그룹 인사이트" : "잠금 대기",
                "description", unlocked
                        ? "이제 상위 그룹의 인사이트를 확인할 수 있어요."
                        : "수익률 5%를 달성하면 잠금이 해제돼요.",
                "ctaLabel", unlocked ? "인사이트 보러가기" : "잠금 대기"
        ));
        body.put("myApplications", myApplications);

        log.info("[home-dashboard] completed userId={} rankings={} upcoming={} applications={} elapsedMs={}",
                userId,
                rankings.size(),
                upcomingCards.size(),
                myApplications.size(),
                System.currentTimeMillis() - startedAt);
        return body;
    }

    private static Integer asInteger(Map<String, Object> map, String key) {
        return map != null && map.get(key) instanceof Number n ? n.intValue() : null;
    }

    private static Long asLong(Map<String, Object> map, String key) {
        return map != null && map.get(key) instanceof Number n ? n.longValue() : null;
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private String marketFor(String stockCode) {
        return stockCode != null && stockCode.matches("\\d{6}") ? "KRX" : "US";
    }

    private boolean hasInsightStock(Vote vote) {
        if (vote == null) {
            return false;
        }
        String stockCode = vote.getStockCode();
        String stockName = vote.getStockName();
        return (stockCode != null && !stockCode.isBlank())
                || (stockName != null && !stockName.isBlank());
    }

    private List<GroupInsightConsensusDTO> buildConsensus(List<Vote> votes, BigDecimal topReturnRate) {
        Map<String, ConsensusAccumulator> byStock = new LinkedHashMap<>();
        for (Vote vote : votes) {
            String key = consensusKey(vote);
            byStock.computeIfAbsent(key, ignored -> new ConsensusAccumulator(vote)).add(vote);
        }

        return byStock.values().stream()
                .limit(MAX_TOP_CONSENSUS_ITEMS)
                .map(accumulator -> {
                    String market = marketFor(accumulator.stockCode);
                    StockVisualDTO visual = stockVisualAssetResolver.resolve(market, accumulator.stockCode, accumulator.stockName, null);
                    String logoUrl = stockSymbolLogoUrlResolver.resolve(market, accumulator.stockCode, visual);
                    return GroupInsightConsensusDTO.builder()
                            .stockCode(accumulator.stockCode)
                            .stockName(accumulator.stockName)
                            .market(market)
                            .logoUrl(logoUrl)
                            .visual(visual)
                            .confidenceRate(accumulator.confidenceRate())
                            .dailyReturnRate(topReturnRate)
                            .signal(accumulator.signal())
                            .build();
                })
                .toList();
    }

    private String consensusKey(Vote vote) {
        String stockCode = vote.getStockCode();
        if (stockCode != null && !stockCode.isBlank()) {
            return stockCode.trim().toUpperCase();
        }
        return displayStockName(vote);
    }

    private static String displayStockName(Vote vote) {
        String stockName = vote.getStockName();
        if (stockName != null && !stockName.isBlank()) {
            return stockName;
        }
        return vote.getStockCode();
    }

    private String buildInsightComment(String topGroupName, Vote vote) {
        if (vote == null) {
            return "최근 매수/매도 투표 근거가 아직 없습니다.";
        }
        String groupName = topGroupName != null && !topGroupName.isBlank() ? topGroupName : "상위 그룹";
        String stockName = displayStockName(vote);
        String action = vote.getType() != null && !vote.getType().isBlank() ? vote.getType() : "투자";
        String reason = vote.getReason() != null && !vote.getReason().isBlank()
                ? vote.getReason()
                : "투표 근거가 비어 있습니다.";
        return groupName + "은 " + stockName + " " + action + " 의견을 냈어요. 근거: " + reason;
    }

    private static BigDecimal percentRate(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP);
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue())
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private static final class ConsensusAccumulator {
        private final String stockCode;
        private final String stockName;
        private int buyCount;
        private int sellCount;

        private ConsensusAccumulator(Vote vote) {
            this.stockCode = vote.getStockCode();
            this.stockName = displayStockName(vote);
        }

        private void add(Vote vote) {
            if (isSell(vote)) {
                sellCount++;
            } else {
                buyCount++;
            }
        }

        private String signal() {
            return sellCount > buyCount ? "SELL" : "BUY";
        }

        private int confidenceRate() {
            int total = buyCount + sellCount;
            if (total == 0) {
                return 0;
            }
            return Math.round((Math.max(buyCount, sellCount) * 100f) / total);
        }

        private static boolean isSell(Vote vote) {
            String type = vote.getType();
            return type != null && (type.equalsIgnoreCase("SELL") || type.contains("매도"));
        }
    }

    private static String normalizeRoomStatus(String status) {
        if (status == null) {
            return null;
        }
        return "started".equalsIgnoreCase(status) ? "STARTED" : "WAITING";
    }

    private static Long parseRoomId(Map<String, Object> room) {
        if (room == null || room.get("id") == null) {
            return null;
        }
        String value = String.valueOf(room.get("id"));
        if (!value.startsWith("room-")) {
            return null;
        }
        try {
            return Long.parseLong(value.substring(5));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private BigDecimal extractMyProfitRate(User user, Map<String, Object> myRanking) {
        if (myRanking != null && myRanking.get("profitRate") instanceof BigDecimal rate) {
            return rate.multiply(BigDecimal.valueOf(100));
        }
        MyInvestmentResponseDTO investment = meService.getMyInvestment(user);
        if (investment.getInvestmentData() != null && investment.getInvestmentData().getProfitLossPercentage() != null) {
            return investment.getInvestmentData().getProfitLossPercentage();
        }
        return BigDecimal.ZERO;
    }

    private String resolveParticipantTeamId(User user) {
        if (user == null) {
            return null;
        }
        if (user.getTeamId() != null && !user.getTeamId().isBlank()) {
            return user.getTeamId();
        }
        return user.getId() != null ? "solo-" + user.getId() : null;
    }
}
