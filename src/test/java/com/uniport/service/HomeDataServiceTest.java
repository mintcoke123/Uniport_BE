package com.uniport.service;

import com.uniport.dto.GroupInsightsResponseDTO;
import com.uniport.entity.ManagedGroupInsight;
import com.uniport.entity.User;
import com.uniport.repository.ManagedGroupInsightRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        when(managedGroupInsightRepository.findByInsightKey("HOME_TOP")).thenReturn(Optional.of(
                ManagedGroupInsight.builder()
                        .insightKey("HOME_TOP")
                        .consensusJson("[]")
                        .build()
        ));
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
}
