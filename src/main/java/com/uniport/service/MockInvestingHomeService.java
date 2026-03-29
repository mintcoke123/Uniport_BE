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
import com.uniport.dto.TopGroupInsightDTO;
import com.uniport.entity.User;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class MockInvestingHomeService {

    private final MatchingRoomService matchingRoomService;
    private final MeService meService;
    private final RankingService rankingService;

    public MockInvestingHomeService(MatchingRoomService matchingRoomService,
                                    MeService meService,
                                    RankingService rankingService) {
        this.matchingRoomService = matchingRoomService;
        this.meService = meService;
        this.rankingService = rankingService;
    }

    public MockInvestingSummaryResponseDTO getSummary(User user) {
        MyInvestmentResponseDTO investment = meService.getMyInvestment(user);
        CompetitionDataDTO competition = investment.getCompetitionData();
        Map<String, Object> myRanking = rankingService.getMyGroupRanking(user);
        List<Map<String, Object>> rooms = matchingRoomService.listRoomsJoinedBy(user);
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
                        .endDate(competition != null && competition.getEndDate() != null ? competition.getEndDate().toString() : null)
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

        return GroupInsightsResponseDTO.builder()
                .topConsensus(List.of(
                        GroupInsightConsensusDTO.builder()
                                .stockCode("NVDA")
                                .stockName("엔비디아")
                                .confidenceRate(92)
                                .dailyReturnRate(new BigDecimal("12.5"))
                                .signal("BUY")
                                .build(),
                        GroupInsightConsensusDTO.builder()
                                .stockCode("TSLA")
                                .stockName("테슬라")
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
                        .comment("실적 발표 전 기술적 신호에서 매수세가 확인된 전략입니다.")
                        .build())
                .build();
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static String normalizeRoomStatus(String status) {
        if (status == null) return null;
        return "started".equalsIgnoreCase(status) ? "STARTED" : "WAITING";
    }

    private static Long parseRoomId(Map<String, Object> room) {
        if (room == null || room.get("id") == null) return null;
        String value = String.valueOf(room.get("id"));
        if (value.startsWith("room-")) {
            try {
                return Long.parseLong(value.substring(5));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
