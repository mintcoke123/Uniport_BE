package com.uniport.service;

import com.uniport.dto.MockInvestmentLeaderboardItemDTO;
import com.uniport.dto.StockPriceDTO;
import com.uniport.entity.MatchingRoom;
import com.uniport.entity.TeamAccount;
import com.uniport.entity.TeamHolding;
import com.uniport.entity.User;
import com.uniport.repository.CompetitionResultRepository;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import com.uniport.repository.TeamAccountRepository;
import com.uniport.repository.TeamHoldingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    private MatchingRoomRepository matchingRoomRepository;
    @Mock
    private MatchingRoomMemberRepository matchingRoomMemberRepository;
    @Mock
    private TeamAccountRepository teamAccountRepository;
    @Mock
    private TeamHoldingRepository teamHoldingRepository;
    @Mock
    private KisApiService kisApiService;
    @Mock
    private CompetitionResultRepository competitionResultRepository;

    @InjectMocks
    private RankingService rankingService;

    @Test
    void getMyGroupRankingReturnsNullWithoutBuildingAllRankingsWhenUserHasNoStartedTeam() {
        User user = User.builder()
                .id(483L)
                .teamId(null)
                .build();
        when(matchingRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(483L)).thenReturn(List.of());

        Map<String, Object> result = rankingService.getMyGroupRanking(user);

        assertThat(result).isNull();
        verify(matchingRoomMemberRepository).findByUserIdOrderByJoinedAtDesc(483L);
        verify(matchingRoomRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void getCompetingTeamsOnlyRanksStartedRoomsInRequestedCompetition() {
        MatchingRoom included = MatchingRoom.create("주간리그 A팀", 3);
        included.setCompetitionId(7L);
        included.setStatus("started");
        included.setId(101L);
        MatchingRoom otherCompetition = MatchingRoom.create("다른 리그 팀", 3);
        otherCompetition.setCompetitionId(8L);
        otherCompetition.setStatus("started");
        otherCompetition.setId(102L);

        when(matchingRoomRepository.findByStatusAndCompetitionIdOrderByCreatedAtDesc("started", 7L))
                .thenReturn(List.of(included));
        when(teamAccountRepository.findByTeamId(101L))
                .thenReturn(java.util.Optional.of(
                        TeamAccount.builder()
                                .teamId(101L)
                                .cashBalance(new java.math.BigDecimal("12000000"))
                                .build()
                ));
        when(teamHoldingRepository.findByTeamId(101L)).thenReturn(List.of());

        List<Map<String, Object>> result = rankingService.getCompetingTeams(7L, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().get("teamId")).isEqualTo("team-101");
        assertThat(result.getFirst().get("groupName")).isEqualTo("주간리그 A팀");
        verify(matchingRoomRepository).findByStatusAndCompetitionIdOrderByCreatedAtDesc("started", 7L);
        verify(teamAccountRepository, never()).findByTeamId(102L);
    }

    @Test
    void activeTeamLeaderboardSortsByReturnRateThenCurrentAssets() {
        MatchingRoom steadyRoom = room(1L, "꾸준한 팀", 3, "2026-05-19T00:00:00Z", "2026-05-26T00:00:00Z");
        MatchingRoom leadingRoom = room(2L, "수익률 팀", 4, "2026-05-19T01:00:00Z", "2026-05-26T01:00:00Z");
        when(matchingRoomRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(steadyRoom, leadingRoom));
        when(teamAccountRepository.findByTeamId(1L)).thenReturn(Optional.of(account(1L, "10000000.0000")));
        when(teamAccountRepository.findByTeamId(2L)).thenReturn(Optional.of(account(2L, "10000000.0000")));
        when(teamHoldingRepository.findByTeamId(1L)).thenReturn(List.of(holding(1L, "005930", 10, "100000.0000")));
        when(teamHoldingRepository.findByTeamId(2L)).thenReturn(List.of(holding(2L, "000660", 10, "100000.0000")));
        when(kisApiService.getCachedStockPrice("005930")).thenReturn(Optional.of(price("005930", "110000.0000")));
        when(kisApiService.getCachedStockPrice("000660")).thenReturn(Optional.of(price("000660", "125000.0000")));

        List<MockInvestmentLeaderboardItemDTO> leaderboard = rankingService.getActiveTeamGameLeaderboard(5);

        assertThat(leaderboard.getFirst().getGroupId()).isEqualTo(2L);
        assertThat(leaderboard.getFirst().getGroupName()).isEqualTo("수익률 팀");
        assertThat(leaderboard.getFirst().getRank()).isEqualTo(1);
        assertThat(leaderboard.get(1).getGroupId()).isEqualTo(1L);
        assertThat(leaderboard.get(1).getRank()).isEqualTo(2);
        verify(kisApiService, never()).getStockPrice(anyString());
    }

    @Test
    void activeTeamLeaderboardUsesCurrentAssetsAsTieBreakerWhenReturnRatesAreEqual() {
        MatchingRoom lowerAssetRoom = room(3L, "동률 낮은 자산 팀", 3, "2026-05-19T00:00:00Z", "2026-05-26T00:00:00Z");
        MatchingRoom higherAssetRoom = room(4L, "동률 높은 자산 팀", 4, "2026-05-19T01:00:00Z", "2026-05-26T01:00:00Z");
        when(matchingRoomRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(lowerAssetRoom, higherAssetRoom));
        when(teamAccountRepository.findByTeamId(3L)).thenReturn(Optional.of(account(3L, "11240000.0000")));
        when(teamAccountRepository.findByTeamId(4L)).thenReturn(Optional.of(account(4L, "11240004.0000")));
        when(teamHoldingRepository.findByTeamId(3L)).thenReturn(List.of());
        when(teamHoldingRepository.findByTeamId(4L)).thenReturn(List.of());

        List<MockInvestmentLeaderboardItemDTO> leaderboard = rankingService.getActiveTeamGameLeaderboard(5);

        assertThat(leaderboard.getFirst().getReturnRate()).isEqualByComparingTo(new BigDecimal("12.4000"));
        assertThat(leaderboard.get(1).getReturnRate()).isEqualByComparingTo(new BigDecimal("12.4000"));
        assertThat(leaderboard.getFirst().getGroupId()).isEqualTo(4L);
        assertThat(leaderboard.getFirst().getTotalAssetAmount()).isEqualByComparingTo(new BigDecimal("11240004.0000"));
        assertThat(leaderboard.get(1).getGroupId()).isEqualTo(3L);
        assertThat(leaderboard.get(1).getTotalAssetAmount()).isEqualByComparingTo(new BigDecimal("11240000.0000"));
        verify(kisApiService, never()).getStockPrice(anyString());
    }

    @Test
    void activeTeamLeaderboardUsesGroupIdAsFinalTieBreakerWhenReturnRateAndAssetsAreEqual() {
        MatchingRoom higherIdRoom = room(8L, "높은 아이디 팀", 3, "2026-05-19T01:00:00Z", "2026-05-26T01:00:00Z");
        MatchingRoom lowerIdRoom = room(7L, "낮은 아이디 팀", 3, "2026-05-19T00:00:00Z", "2026-05-26T00:00:00Z");
        when(matchingRoomRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(higherIdRoom, lowerIdRoom));
        when(teamAccountRepository.findByTeamId(7L)).thenReturn(Optional.of(account(7L, "11240000.0000")));
        when(teamAccountRepository.findByTeamId(8L)).thenReturn(Optional.of(account(8L, "11240000.0000")));
        when(teamHoldingRepository.findByTeamId(7L)).thenReturn(List.of());
        when(teamHoldingRepository.findByTeamId(8L)).thenReturn(List.of());

        List<MockInvestmentLeaderboardItemDTO> leaderboard = rankingService.getActiveTeamGameLeaderboard(5);

        assertThat(leaderboard.getFirst().getReturnRate()).isEqualByComparingTo(new BigDecimal("12.4000"));
        assertThat(leaderboard.get(1).getReturnRate()).isEqualByComparingTo(new BigDecimal("12.4000"));
        assertThat(leaderboard.getFirst().getTotalAssetAmount()).isEqualByComparingTo(new BigDecimal("11240000.0000"));
        assertThat(leaderboard.get(1).getTotalAssetAmount()).isEqualByComparingTo(new BigDecimal("11240000.0000"));
        assertThat(leaderboard.getFirst().getGroupId()).isEqualTo(7L);
        assertThat(leaderboard.get(1).getGroupId()).isEqualTo(8L);
        verify(kisApiService, never()).getStockPrice(anyString());
    }

    @Test
    void activeTeamLeaderboardEmitsPercentReturnRateAndIsoDates() {
        MatchingRoom room = room(10L, "퍼센트 팀", 2, "2026-05-19T02:03:04Z", "2026-05-26T02:03:04Z");
        when(matchingRoomRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(room));
        when(teamAccountRepository.findByTeamId(10L)).thenReturn(Optional.of(account(10L, "10000000.0000")));
        when(teamHoldingRepository.findByTeamId(10L)).thenReturn(List.of(holding(10L, "035420", 10, "100000.0000")));
        when(kisApiService.getCachedStockPrice("035420")).thenReturn(Optional.of(price("035420", "124000.0000")));

        List<MockInvestmentLeaderboardItemDTO> leaderboard = rankingService.getActiveTeamGameLeaderboard(5);

        MockInvestmentLeaderboardItemDTO item = leaderboard.getFirst();
        assertThat(item.getTeamGameId()).isEqualTo("team_game_10");
        assertThat(item.getStartedAt()).isEqualTo("2026-05-19T02:03:04Z");
        assertThat(item.getEndsAt()).isEqualTo("2026-05-26T02:03:04Z");
        assertThat(item.getTotalAssetAmount()).isEqualByComparingTo(new BigDecimal("11240000.0000"));
        assertThat(item.getReturnRate()).isEqualByComparingTo(new BigDecimal("12.4000"));
        assertThat(item.getAvatarUrl()).isNull();
        verify(kisApiService, never()).getStockPrice(anyString());
    }

    private static MatchingRoom room(Long id, String name, int memberCount, String startedAt, String endsAt) {
        return MatchingRoom.builder()
                .id(id)
                .name(name)
                .memberCount(memberCount)
                .status("started")
                .createdAt(Instant.parse(startedAt))
                .endedAt(Instant.parse(endsAt))
                .build();
    }

    private static TeamAccount account(Long teamId, String cashBalance) {
        return TeamAccount.builder()
                .teamId(teamId)
                .cashBalance(new BigDecimal(cashBalance))
                .build();
    }

    private static TeamHolding holding(Long teamId, String stockCode, int quantity, String averagePurchasePrice) {
        return TeamHolding.builder()
                .teamId(teamId)
                .stockCode(stockCode)
                .quantity(quantity)
                .averagePurchasePrice(new BigDecimal(averagePurchasePrice))
                .build();
    }

    private static StockPriceDTO price(String stockCode, String currentPrice) {
        return StockPriceDTO.builder()
                .stockCode(stockCode)
                .currentPrice(new BigDecimal(currentPrice))
                .build();
    }
}
