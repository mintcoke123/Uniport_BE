package com.uniport.service;

import com.uniport.entity.Competition;
import com.uniport.repository.CompetitionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompetitionServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Test
    void derivesOngoingStatusFromKoreanTimeZone() {
        CompetitionRepository repository = mock(CompetitionRepository.class);
        Competition competition = Competition.builder()
                .id(5L)
                .name("세종대 대축제")
                .startDate("2026-05-19T03:00")
                .endDate("2026-05-23T12:59")
                .status("upcoming")
                .build();
        when(repository.findAll()).thenReturn(List.of(competition));

        Clock clock = Clock.fixed(Instant.parse("2026-05-18T18:37:00Z"), KST);
        CompetitionService service = new CompetitionService(repository, clock);

        assertEquals(5L, service.findOngoing().orElseThrow().getId());
        assertTrue(service.findByStatus("upcoming").isEmpty());
        assertEquals("ongoing", service.toMap(competition).get("status"));
    }
}
