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
import com.uniport.entity.User;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MockInvestingHomeService {

    private final MatchingRoomService matchingRoomService;
    private final MeService meService;
    private final RankingService rankingService;
    private final CompetitionService competitionService;
    private final CompetitionParticipationService competitionParticipationService;
    private final StockVisualAssetResolver stockVisualAssetResolver;
    private final StockSymbolLogoUrlResolver stockSymbolLogoUrlResolver;

    public MockInvestingHomeService(MatchingRoomService matchingRoomService,
                                    MeService meService,
                                    RankingService rankingService,
                                    CompetitionService competitionService,
                                    CompetitionParticipationService competitionParticipationService,
                                    StockVisualAssetResolver stockVisualAssetResolver,
                                    StockSymbolLogoUrlResolver stockSymbolLogoUrlResolver) {
        this.matchingRoomService = matchingRoomService;
        this.meService = meService;
        this.rankingService = rankingService;
        this.competitionService = competitionService;
        this.competitionParticipationService = competitionParticipationService;
        this.stockVisualAssetResolver = stockVisualAssetResolver;
        this.stockSymbolLogoUrlResolver = stockSymbolLogoUrlResolver;
    }

    public MockInvestingSummaryResponseDTO getSummary(User user) {
        MyInvestmentResponseDTO investment = meService.getMyInvestment(user);
        CompetitionDataDTO competition = investment.getCompetitionData();
        Map<String, Object> myRanking = rankingService.getMyGroupRanking(user);
        List<Map<String, Object>> rooms = user != null ? matchingRoomService.listRoomsJoinedBy(user) : List.of();
        Map<String, Object> activeRoom = rooms.isEmpty() ? null : rooms.get(0);

        return MockInvestingSummaryResponseDTO.builder()
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
                        .rank(myRanking != null && myRanking.get("rank") instanceof Number ? ((Number) myRanking.get("rank")).intValue() : null)
                        .groupId(myRanking != null && myRanking.get("id") instanceof Number ? ((Number) myRanking.get("id")).longValue() : null)
                        .groupName(myRanking != null ? stringValue(myRanking.get("groupName")) : null)
                        .build())
                .build();
    }

    public GroupInsightsResponseDTO getGroupInsights() {
        List<Map<String, Object>> ranking = rankingService.getAllGroupsRanking();
        Map<String, Object> topGroup = ranking.isEmpty() ? null : ranking.get(0);
        BigDecimal topGroupRate = topGroup != null && topGroup.get("profitRate") instanceof BigDecimal
                ? ((BigDecimal) topGroup.get("profitRate")).multiply(BigDecimal.valueOf(100))
                : null;
        StockVisualDTO nvidiaVisual = stockVisualAssetResolver.resolve("US", "NVDA", "엔비디아", null);
        String nvidiaLogoUrl = stockSymbolLogoUrlResolver.resolve("US", "NVDA", nvidiaVisual);
        StockVisualDTO teslaVisual = stockVisualAssetResolver.resolve("US", "TSLA", "테슬라", null);
        String teslaLogoUrl = stockSymbolLogoUrlResolver.resolve("US", "TSLA", teslaVisual);

        return GroupInsightsResponseDTO.builder()
                .topConsensus(List.of(
                        GroupInsightConsensusDTO.builder()
                                .stockCode("NVDA")
                                .stockName("엔비디아")
                                .market("US")
                                .logoUrl(nvidiaLogoUrl)
                                .visual(nvidiaVisual)
                                .confidenceRate(92)
                                .dailyReturnRate(new BigDecimal("12.5"))
                                .signal("BUY")
                                .build(),
                        GroupInsightConsensusDTO.builder()
                                .stockCode("TSLA")
                                .stockName("테슬라")
                                .market("US")
                                .logoUrl(teslaLogoUrl)
                                .visual(teslaVisual)
                                .confidenceRate(74)
                                .dailyReturnRate(new BigDecimal("4.1"))
                                .signal("SELL")
                                .build()
                ))
                .topGroup(TopGroupInsightDTO.builder()
                        .groupId(topGroup != null && topGroup.get("id") instanceof Number ? ((Number) topGroup.get("id")).longValue() : null)
                        .groupName(topGroup != null ? stringValue(topGroup.get("groupName")) : null)
                        .dailyReturnRate(topGroupRate)
                        .topPick("NVDA")
                        .comment("실적 발표 전 수급과 모멘텀을 같이 확인한 뒤 수익을 확정한 인사이트예요.")
                        .build())
                .build();
    }

    public Map<String, Object> getGroupMatchingDashboard(User user) {
        List<Map<String, Object>> rankings = rankingService.getAllGroupsRanking();
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
        BigDecimal myProfitRate = extractMyProfitRate(user);
        BigDecimal unlockTarget = new BigDecimal("5.0");
        boolean unlocked = myProfitRate.compareTo(unlockTarget) >= 0;

        Map<String, Object> body = new java.util.LinkedHashMap<>();
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
        body.put("myGroupRanking", user != null ? rankingService.getMyGroupRanking(user) : null);
        body.put("insightUnlock", Map.of(
                "unlocked", unlocked,
                "targetProfitRate", unlockTarget,
                "currentProfitRate", myProfitRate,
                "title", unlocked ? "상위 그룹 인사이트" : "잠금 풀기",
                "description", unlocked
                        ? "어제 수익률 전국 1위 그룹의 인사이트를 확인할 수 있어요."
                        : "수익률 5%를 달성하고 잠금을 해제하세요!",
                "ctaLabel", unlocked ? "인사이트 더 보기" : "잠금 풀기"
        ));
        body.put("myApplications", user != null ? competitionParticipationService.getMyApplications(user) : List.of());
        return body;
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
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

    private BigDecimal extractMyProfitRate(User user) {
        Map<String, Object> myRanking = user != null ? rankingService.getMyGroupRanking(user) : null;
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
