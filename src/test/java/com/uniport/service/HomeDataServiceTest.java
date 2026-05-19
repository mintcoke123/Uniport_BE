package com.uniport.service;

import com.uniport.dto.GroupInsightsResponseDTO;
import com.uniport.entity.Vote;
import com.uniport.entity.User;
import com.uniport.repository.ManagedGroupInsightRepository;
import com.uniport.repository.VoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeDataServiceTest {

    @Mock
    private MatchingRoomService matchingRoomService;
    @Mock
    private MeService meService;
    @Mock
    private RankingService rankingService;
    @Mock
    private CompetitionService competitionService;
    @Mock
    private CompetitionParticipationService competitionParticipationService;
    @Mock
    private ManagedGroupInsightRepository managedGroupInsightRepository;
    @Mock
    private VoteRepository voteRepository;
    @Mock
    private StockVisualAssetResolver stockVisualAssetResolver;
    @Mock
    private StockSymbolLogoUrlResolver stockSymbolLogoUrlResolver;

    @Spy
    @InjectMocks
    private HomeDataService homeDataService;

    @Test
    void getGroupMatchingDashboardUsesSnapshotRankingOnce() {
        User user = User.builder()
                .id(7L)
                .teamId("team-1")
                .build();

        List<Map<String, Object>> rankings = List.of(
                Map.of(
                        "id", 1L,
                        "groupName", "Alpha",
                        "currentAssets", new BigDecimal("11000000"),
                        "profitRate", new BigDecimal("0.1000")
                )
        );

        when(rankingService.getAllGroupsRankingSnapshot()).thenReturn(rankings);
        when(rankingService.getMyGroupRanking(user, rankings)).thenReturn(Map.of(
                "id", 1L,
                "groupName", "Alpha",
                "currentAssets", new BigDecimal("11000000"),
                "profitRate", new BigDecimal("0.1000"),
                "rank", 1
        ));
        when(competitionService.findByStatus("upcoming")).thenReturn(List.of());
        when(competitionParticipationService.getMyApplications(user)).thenReturn(List.of());
        doReturn(GroupInsightsResponseDTO.builder().topConsensus(List.of()).topGroup(null).build())
                .when(homeDataService).getGroupInsights();

        Map<String, Object> body = homeDataService.getGroupMatchingDashboard(user);

        assertThat(body).containsKey("realtimeRanking");
        assertThat(body.get("myGroupRanking")).isNotNull();
        verify(rankingService).getAllGroupsRankingSnapshot();
        verify(rankingService).getMyGroupRanking(user, rankings);
        verify(rankingService, never()).getAllGroupsRanking();
        verify(competitionParticipationService, never()).getApplicationStatus(anyLong(), any(), any());
    }

    @Test
    void getGroupInsightsBuildsInsightFromTopRankedGroupsLatestVoteReason() {
        when(rankingService.getAllGroupsRankingSnapshot()).thenReturn(List.of(
                Map.of(
                        "id", 11L,
                        "groupName", "가즈아팀",
                        "currentAssets", new BigDecimal("11234000"),
                        "profitRate", new BigDecimal("0.1234")
                )
        ));
        when(voteRepository.findByRoomIdOrderByCreatedAtDesc(11L)).thenReturn(List.of(
                Vote.builder()
                        .roomId(11L)
                        .proposerId(7L)
                        .proposerName("팀장")
                        .type("매수")
                        .stockName("엔비디아")
                        .stockCode("NVDA")
                        .quantity(3)
                        .proposedPrice(new BigDecimal("900"))
                        .reason("AI 수요가 계속 늘고 실적 발표 기대감이 커서 매수")
                        .createdAt(Instant.parse("2026-05-19T09:00:00Z"))
                        .expiresAt(Instant.parse("2026-05-19T09:10:00Z"))
                        .totalMembers(3)
                        .status("executed")
                        .build()
        ));

        GroupInsightsResponseDTO response = homeDataService.getGroupInsights();

        assertThat(response.getTopGroup().getGroupId()).isEqualTo(11L);
        assertThat(response.getTopGroup().getGroupName()).isEqualTo("가즈아팀");
        assertThat(response.getTopGroup().getTopPick()).isEqualTo("엔비디아");
        assertThat(response.getTopGroup().getDailyReturnRate()).isEqualByComparingTo("12.3400");
        assertThat(response.getTopGroup().getComment()).contains("매수", "AI 수요가 계속 늘고 실적 발표 기대감이 커서 매수");
        assertThat(response.getTopConsensus()).hasSize(1);
        assertThat(response.getTopConsensus().get(0).getStockCode()).isEqualTo("NVDA");
        assertThat(response.getTopConsensus().get(0).getStockName()).isEqualTo("엔비디아");
        assertThat(response.getTopConsensus().get(0).getSignal()).isEqualTo("BUY");
        assertThat(response.getTopConsensus().get(0).getConfidenceRate()).isEqualTo(100);
        verify(managedGroupInsightRepository, never()).findByInsightKey(any());
    }
}
