package com.uniport.service;

import com.uniport.entity.User;
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
}
