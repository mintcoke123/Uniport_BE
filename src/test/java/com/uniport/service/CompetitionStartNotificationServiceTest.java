package com.uniport.service;

import com.uniport.entity.Competition;
import com.uniport.entity.CompetitionApplication;
import com.uniport.entity.User;
import com.uniport.repository.CompetitionApplicationRepository;
import com.uniport.repository.CompetitionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompetitionStartNotificationServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Test
    void sendsStartPushOnceToAppliedUsersOfDueCompetition() {
        CompetitionRepository competitionRepository = mock(CompetitionRepository.class);
        CompetitionApplicationRepository applicationRepository = mock(CompetitionApplicationRepository.class);
        PushNotificationService pushNotificationService = mock(PushNotificationService.class);
        Competition competition = Competition.builder()
                .id(55L)
                .name("세종대 대축제")
                .startDate("2026-05-19T00:00:00")
                .endDate("2026-05-20T23:59:59")
                .status("upcoming")
                .build();
        User first = User.builder().id(7L).build();
        User second = User.builder().id(8L).build();
        when(competitionRepository.findAll()).thenReturn(List.of(competition));
        when(applicationRepository.findByCompetition_IdAndStatus(55L, "APPLIED")).thenReturn(List.of(
                CompetitionApplication.builder().competition(competition).user(first).status("APPLIED").build(),
                CompetitionApplication.builder().competition(competition).user(second).status("APPLIED").build()
        ));
        Clock clock = Clock.fixed(Instant.parse("2026-05-19T01:00:00Z"), KST);
        CompetitionService competitionService = new CompetitionService(competitionRepository, clock);
        CompetitionStartNotificationService service = new CompetitionStartNotificationService(
                competitionRepository,
                applicationRepository,
                competitionService,
                pushNotificationService,
                clock
        );

        int sentCount = service.sendDueStartNotifications();

        assertEquals(1, sentCount);
        verify(pushNotificationService).sendTournamentStarted(competition, List.of(7L, 8L));
        ArgumentCaptor<Competition> competitionCaptor = ArgumentCaptor.forClass(Competition.class);
        verify(competitionRepository).save(competitionCaptor.capture());
        assertNotNull(competitionCaptor.getValue().getStartNotificationSentAt());
    }
}
