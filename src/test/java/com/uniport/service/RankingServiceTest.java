package com.uniport.service;

import com.uniport.entity.User;
import com.uniport.entity.MatchingRoom;
import com.uniport.entity.TeamAccount;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
}
