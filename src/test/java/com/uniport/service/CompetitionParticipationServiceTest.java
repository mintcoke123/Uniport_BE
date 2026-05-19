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
    void applyUsesApplicantScopedFallbackTeamIdInsteadOfCurrentUserTeamId() {
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

        var response = service.apply(7L, user, null, null);

        assertEquals("applicant-10", response.get("teamId"));
        verify(applicationRepository).findByCompetition_IdAndUser_Id(7L, 10L);
        verify(applicationRepository, never()).existsByCompetition_IdAndUser_IdAndStatus(anyLong(), anyLong(), any());
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
