package com.uniport.scheduler;

import com.uniport.entity.MatchingRoom;
import com.uniport.entity.TeamGameSnapshot;
import com.uniport.repository.MatchingRoomRepository;
import com.uniport.repository.TeamGameSnapshotRepository;
import com.uniport.service.RankingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamGameSnapshotSchedulerTest {

    private MatchingRoomRepository matchingRoomRepository;
    private RankingService rankingService;
    private TeamGameSnapshotRepository teamGameSnapshotRepository;
    private TeamGameSnapshotScheduler scheduler;

    @BeforeEach
    void setUp() {
        matchingRoomRepository = mock(MatchingRoomRepository.class);
        rankingService = mock(RankingService.class);
        teamGameSnapshotRepository = mock(TeamGameSnapshotRepository.class);
        scheduler = new TeamGameSnapshotScheduler(
                matchingRoomRepository,
                rankingService,
                teamGameSnapshotRepository,
                Clock.fixed(Instant.parse("2026-05-19T15:30:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void runCreatesSnapshotForStartedRoomUsingKstSnapshotDateAndPercentReturnRate() {
        MatchingRoom room = room(42L, "수익률 팀", 4, "2026-05-19T01:00:00Z", "2026-05-26T01:00:00Z");
        when(matchingRoomRepository.findByStatusAndCompetitionIdIsNullOrderByCreatedAtDesc("started")).thenReturn(List.of(room));
        when(rankingService.evaluateTeam(42L, false))
                .thenReturn(new RankingService.TeamValuation(
                        new BigDecimal("11240000.0000"),
                        new BigDecimal("12.4000")
                ));

        scheduler.run();

        ArgumentCaptor<TeamGameSnapshot> captor = ArgumentCaptor.forClass(TeamGameSnapshot.class);
        verify(teamGameSnapshotRepository).save(captor.capture());
        TeamGameSnapshot snapshot = captor.getValue();
        assertEquals(42L, snapshot.getTeamId());
        assertEquals("수익률 팀", snapshot.getTeamName());
        assertEquals("team_game_42", snapshot.getTeamGameId());
        assertEquals(4, snapshot.getMemberCount());
        assertEquals(Instant.parse("2026-05-19T01:00:00Z"), snapshot.getStartedAt());
        assertEquals(Instant.parse("2026-05-26T01:00:00Z"), snapshot.getEndsAt());
        assertEquals(new BigDecimal("11240000.0000"), snapshot.getTotalAssetAmount());
        assertEquals(new BigDecimal("12.4000"), snapshot.getReturnRate());
        assertEquals(Instant.parse("2026-05-19T15:30:00Z"), snapshot.getSnapshotAt());
        assertEquals(LocalDate.of(2026, 5, 20), snapshot.getSnapshotDate());
        verify(rankingService).evaluateTeam(42L, false);
        verify(matchingRoomRepository).findByStatusAndCompetitionIdIsNullOrderByCreatedAtDesc("started");
    }

    @Test
    void runLoadsOnlyStartedRooms() {
        when(matchingRoomRepository.findByStatusAndCompetitionIdIsNullOrderByCreatedAtDesc("started")).thenReturn(List.of());

        scheduler.run();

        verify(matchingRoomRepository).findByStatusAndCompetitionIdIsNullOrderByCreatedAtDesc("started");
    }

    @Test
    void runSkipsThrowingRoomAndStillSavesLaterValidRoom() {
        MatchingRoom throwingRoom = room(1L, "깨진 팀", 2, "2026-05-19T00:00:00Z", "2026-05-26T00:00:00Z");
        MatchingRoom validRoom = room(2L, "정상 팀", 3, "2026-05-19T01:00:00Z", "2026-05-26T01:00:00Z");
        when(matchingRoomRepository.findByStatusAndCompetitionIdIsNullOrderByCreatedAtDesc("started"))
                .thenReturn(List.of(throwingRoom, validRoom));
        when(rankingService.evaluateTeam(1L, false)).thenThrow(new IllegalStateException("valuation failed"));
        when(rankingService.evaluateTeam(2L, false))
                .thenReturn(new RankingService.TeamValuation(
                        new BigDecimal("11240000.0000"),
                        new BigDecimal("12.4000")
                ));

        scheduler.run();

        ArgumentCaptor<TeamGameSnapshot> captor = ArgumentCaptor.forClass(TeamGameSnapshot.class);
        verify(teamGameSnapshotRepository, times(1)).save(captor.capture());
        assertEquals(2L, captor.getValue().getTeamId());
        assertEquals("정상 팀", captor.getValue().getTeamName());
        verify(rankingService).evaluateTeam(1L, false);
        verify(rankingService).evaluateTeam(2L, false);
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
}
