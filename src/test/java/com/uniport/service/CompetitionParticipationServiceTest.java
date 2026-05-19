package com.uniport.service;

import com.uniport.entity.Competition;
import com.uniport.entity.CompetitionApplication;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.CompetitionApplicationRepository;
import com.uniport.repository.CompetitionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompetitionParticipationServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Test
    void applyIgnoresTeamFieldsAndReturnsNullTeamMetadata() {
        CompetitionRepository competitionRepository = mock(CompetitionRepository.class);
        CompetitionApplicationRepository applicationRepository = mock(CompetitionApplicationRepository.class);
        Competition competition = Competition.builder()
                .id(7L)
                .name("세종대 대축제")
                .startDate("2026-05-20T00:00:00")
                .endDate("2026-05-21T23:59:59")
                .status("upcoming")
                .build();
        User user = User.builder()
                .id(10L)
                .nickname("참가자")
                .teamId("team-337")
                .build();
        when(competitionRepository.findById(7L)).thenReturn(Optional.of(competition));
        when(applicationRepository.findByCompetition_IdAndUser_Id(7L, 10L)).thenReturn(Optional.empty());
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CompetitionService competitionService = new CompetitionService(
                competitionRepository,
                Clock.fixed(Instant.parse("2026-05-19T00:00:00Z"), KST)
        );
        CompetitionParticipationService service = new CompetitionParticipationService(
                competitionRepository,
                applicationRepository,
                competitionService
        );

        var response = service.apply(7L, user, "team-337", "기존 팀");

        assertEquals(null, response.get("teamId"));
        assertEquals(null, response.get("teamName"));
        verify(applicationRepository).findByCompetition_IdAndUser_Id(7L, 10L);
        verify(applicationRepository, never()).existsByCompetition_IdAndUser_IdAndStatus(anyLong(), anyLong(), any());
    }

    @Test
    void getApplicationStatusDoesNotExposeFallbackTeamIdWhenNotApplied() {
        CompetitionRepository competitionRepository = mock(CompetitionRepository.class);
        CompetitionApplicationRepository applicationRepository = mock(CompetitionApplicationRepository.class);
        Competition competition = Competition.builder()
                .id(7L)
                .name("세종대 대축제")
                .startDate("2026-05-20T00:00:00")
                .endDate("2026-05-21T23:59:59")
                .status("upcoming")
                .build();
        User user = User.builder().id(10L).nickname("참가자").teamId("team-337").build();
        when(competitionRepository.findById(7L)).thenReturn(Optional.of(competition));
        when(applicationRepository.findByCompetition_IdAndUser_Id(7L, 10L)).thenReturn(Optional.empty());
        CompetitionService competitionService = new CompetitionService(
                competitionRepository,
                Clock.fixed(Instant.parse("2026-05-19T00:00:00Z"), KST)
        );
        CompetitionParticipationService service = new CompetitionParticipationService(
                competitionRepository,
                applicationRepository,
                competitionService
        );

        var response = service.getApplicationStatus(7L, user, "team-337");

        assertEquals(null, response.get("teamId"));
        assertEquals(null, response.get("teamName"));
        assertEquals("NOT_APPLIED", response.get("status"));
    }

    @Test
    void myApplicationsReturnNullTeamMetadataForLegacyRows() {
        CompetitionRepository competitionRepository = mock(CompetitionRepository.class);
        CompetitionApplicationRepository applicationRepository = mock(CompetitionApplicationRepository.class);
        Competition competition = Competition.builder()
                .id(7L)
                .name("세종대 대축제")
                .startDate("2026-05-20T00:00:00")
                .endDate("2026-05-21T23:59:59")
                .status("upcoming")
                .build();
        User user = User.builder().id(10L).nickname("참가자").teamId("team-337").build();
        CompetitionApplication application = CompetitionApplication.builder()
                .competition(competition)
                .user(user)
                .teamId("team-337")
                .teamName("기존 팀")
                .status("APPLIED")
                .appliedAt(Instant.parse("2026-05-18T00:00:00Z"))
                .build();
        when(applicationRepository.findByUser_IdOrderByAppliedAtDesc(10L)).thenReturn(java.util.List.of(application));
        CompetitionService competitionService = new CompetitionService(
                competitionRepository,
                Clock.fixed(Instant.parse("2026-05-19T00:00:00Z"), KST)
        );
        CompetitionParticipationService service = new CompetitionParticipationService(
                competitionRepository,
                applicationRepository,
                competitionService
        );

        var response = service.getMyApplications(user).getFirst();

        assertEquals(null, response.get("teamId"));
        assertEquals(null, response.get("teamName"));
    }

    @Test
    void applyRejectsCompetitionThatAlreadyStarted() {
        CompetitionRepository competitionRepository = mock(CompetitionRepository.class);
        CompetitionApplicationRepository applicationRepository = mock(CompetitionApplicationRepository.class);
        Competition competition = Competition.builder()
                .id(7L)
                .name("세종대 대축제")
                .startDate("2026-05-19T00:00:00")
                .endDate("2026-05-20T23:59:59")
                .status("upcoming")
                .build();
        when(competitionRepository.findById(7L)).thenReturn(Optional.of(competition));
        CompetitionService competitionService = new CompetitionService(
                competitionRepository,
                Clock.fixed(Instant.parse("2026-05-19T01:00:00Z"), KST)
        );
        CompetitionParticipationService service = new CompetitionParticipationService(
                competitionRepository,
                applicationRepository,
                competitionService
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.apply(7L, User.builder().id(10L).nickname("참가자").build(), "team-10", "참가자 팀")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("시작 전 토너먼트만 참가 신청할 수 있습니다.", exception.getMessage());
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void applyRejectsWhenUserAlreadyAppliedToAnotherActiveTournament() {
        CompetitionRepository competitionRepository = mock(CompetitionRepository.class);
        CompetitionApplicationRepository applicationRepository = mock(CompetitionApplicationRepository.class);
        Competition requestedCompetition = Competition.builder()
                .id(8L)
                .name("새 토너먼트")
                .startDate("2026-05-20T00:00:00")
                .endDate("2026-05-21T23:59:59")
                .status("upcoming")
                .build();
        Competition existingCompetition = Competition.builder()
                .id(7L)
                .name("기존 토너먼트")
                .startDate("2026-05-19T00:00:00")
                .endDate("2026-05-22T23:59:59")
                .status("upcoming")
                .build();
        User user = User.builder().id(10L).nickname("참가자").build();
        CompetitionApplication existingApplication = CompetitionApplication.builder()
                .competition(existingCompetition)
                .user(user)
                .status("APPLIED")
                .appliedAt(Instant.parse("2026-05-18T00:00:00Z"))
                .build();
        when(competitionRepository.findById(8L)).thenReturn(Optional.of(requestedCompetition));
        when(applicationRepository.findByUser_IdOrderByAppliedAtDesc(10L))
                .thenReturn(java.util.List.of(existingApplication));
        CompetitionService competitionService = new CompetitionService(
                competitionRepository,
                Clock.fixed(Instant.parse("2026-05-19T00:00:00Z"), KST)
        );
        CompetitionParticipationService service = new CompetitionParticipationService(
                competitionRepository,
                applicationRepository,
                competitionService
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.apply(8L, user, null, null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals(
                "이미 다른 토너먼트를 참여하고 있다면 새로운 토너먼트에 참여할 수 없어요",
                exception.getMessage()
        );
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void applyAllowsAnotherApplicationWhenExistingTournamentHasNotStartedYet() {
        CompetitionRepository competitionRepository = mock(CompetitionRepository.class);
        CompetitionApplicationRepository applicationRepository = mock(CompetitionApplicationRepository.class);
        Competition requestedCompetition = Competition.builder()
                .id(8L)
                .name("새 토너먼트")
                .startDate("2026-05-21T00:00:00")
                .endDate("2026-05-22T23:59:59")
                .status("upcoming")
                .build();
        Competition existingCompetition = Competition.builder()
                .id(7L)
                .name("기존 예정 토너먼트")
                .startDate("2026-05-20T00:00:00")
                .endDate("2026-05-23T23:59:59")
                .status("upcoming")
                .build();
        User user = User.builder().id(10L).nickname("참가자").build();
        CompetitionApplication existingApplication = CompetitionApplication.builder()
                .competition(existingCompetition)
                .user(user)
                .status("APPLIED")
                .appliedAt(Instant.parse("2026-05-18T00:00:00Z"))
                .build();
        when(competitionRepository.findById(8L)).thenReturn(Optional.of(requestedCompetition));
        when(applicationRepository.findByUser_IdOrderByAppliedAtDesc(10L))
                .thenReturn(java.util.List.of(existingApplication));
        when(applicationRepository.findByCompetition_IdAndUser_Id(8L, 10L)).thenReturn(Optional.empty());
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CompetitionService competitionService = new CompetitionService(
                competitionRepository,
                Clock.fixed(Instant.parse("2026-05-19T00:00:00Z"), KST)
        );
        CompetitionParticipationService service = new CompetitionParticipationService(
                competitionRepository,
                applicationRepository,
                competitionService
        );

        var response = service.apply(8L, user, null, null);

        assertEquals(true, response.get("applied"));
        verify(applicationRepository).save(any());
    }

    @Test
    void cancelRejectsCompetitionThatAlreadyStarted() {
        CompetitionRepository competitionRepository = mock(CompetitionRepository.class);
        CompetitionApplicationRepository applicationRepository = mock(CompetitionApplicationRepository.class);
        Competition competition = Competition.builder()
                .id(7L)
                .name("세종대 대축제")
                .startDate("2026-05-19T00:00:00")
                .endDate("2026-05-20T23:59:59")
                .status("upcoming")
                .build();
        User user = User.builder().id(10L).nickname("참가자").build();
        CompetitionApplication application = CompetitionApplication.builder()
                .competition(competition)
                .user(user)
                .teamId("team-10")
                .teamName("참가자 팀")
                .status("APPLIED")
                .appliedAt(Instant.parse("2026-05-18T00:00:00Z"))
                .build();
        when(competitionRepository.findById(7L)).thenReturn(Optional.of(competition));
        when(applicationRepository.findByCompetition_IdAndUser_Id(7L, 10L)).thenReturn(Optional.of(application));
        CompetitionService competitionService = new CompetitionService(
                competitionRepository,
                Clock.fixed(Instant.parse("2026-05-19T01:00:00Z"), KST)
        );
        CompetitionParticipationService service = new CompetitionParticipationService(
                competitionRepository,
                applicationRepository,
                competitionService
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.cancel(7L, user, "team-10")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("시작된 토너먼트는 참가 신청을 취소할 수 없습니다.", exception.getMessage());
        verify(applicationRepository, never()).save(any());
    }
}
