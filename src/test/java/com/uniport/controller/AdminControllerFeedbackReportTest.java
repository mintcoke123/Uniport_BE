package com.uniport.controller;

import com.uniport.entity.MatchingRoom;
import com.uniport.entity.User;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import com.uniport.repository.UserRepository;
import com.uniport.service.AuthService;
import com.uniport.service.ChatService;
import com.uniport.service.CompetitionService;
import com.uniport.service.MatchingRoomService;
import com.uniport.service.RankingService;
import com.uniport.service.UserDeletionReferenceCleanupService;
import com.uniport.service.VoteService;
import com.uniport.service.feedback.GenerateGroupInvestmentFeedbackReportUseCase;
import com.uniport.service.importer.AssetMasterImportService;
import com.uniport.websocket.PriceBroadcaster;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerFeedbackReportTest {

    @Test
    void forceEndFeedbackReport_setsEndedAtToNowAndGeneratesReportForSisuAdmin() throws Exception {
        AuthService authService = mock(AuthService.class);
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        GenerateGroupInvestmentFeedbackReportUseCase feedbackReportUseCase = mock(GenerateGroupInvestmentFeedbackReportUseCase.class);
        MatchingRoom room = MatchingRoom.builder()
                .id(12L)
                .name("QA room")
                .capacity(3)
                .memberCount(2)
                .status("started")
                .createdAt(Instant.parse("2026-05-01T00:00:00Z"))
                .endedAt(Instant.parse("2026-05-24T00:00:00Z"))
                .build();
        when(authService.getUserFromToken("Bearer sisu"))
                .thenReturn(User.builder().id(77L).role("sisu_admin").build());
        when(matchingRoomRepository.findById(12L)).thenReturn(Optional.of(room));
        when(feedbackReportUseCase.generateForRoom(12L)).thenReturn(Map.of(
                "reportId", 91L,
                "roomId", 12L,
                "status", "PUBLISHED"
        ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(newController(
                authService,
                matchingRoomRepository,
                feedbackReportUseCase
        )).build();
        Instant before = Instant.now();

        mockMvc.perform(post("/api/admin/matching-rooms/room-12/force-end-feedback-report")
                        .header("Authorization", "Bearer sisu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(91))
                .andExpect(jsonPath("$.roomId").value(12))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        Instant after = Instant.now();
        assertEquals("ended", room.getStatus());
        assertFalse(room.getEndedAt().isBefore(before));
        assertFalse(room.getEndedAt().isAfter(after));
        verify(matchingRoomRepository).save(room);
        verify(feedbackReportUseCase).generateForRoom(12L);
    }

    @Test
    void forceEndFeedbackReport_acceptsPlainNumericRoomIdForAdmin() throws Exception {
        AuthService authService = mock(AuthService.class);
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        GenerateGroupInvestmentFeedbackReportUseCase feedbackReportUseCase = mock(GenerateGroupInvestmentFeedbackReportUseCase.class);
        MatchingRoom room = MatchingRoom.builder()
                .id(12L)
                .name("QA room")
                .status("waiting")
                .createdAt(Instant.parse("2026-05-01T00:00:00Z"))
                .build();
        when(authService.getUserFromToken("Bearer admin"))
                .thenReturn(User.builder().id(1L).role("admin").build());
        when(matchingRoomRepository.findById(12L)).thenReturn(Optional.of(room));
        when(feedbackReportUseCase.generateForRoom(12L)).thenReturn(Map.of("reportId", 92L));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(newController(
                authService,
                matchingRoomRepository,
                feedbackReportUseCase
        )).build();

        mockMvc.perform(post("/api/admin/matching-rooms/12/force-end-feedback-report")
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(92));

        assertEquals("ended", room.getStatus());
        assertTrue(room.getEndedAt() != null);
        verify(matchingRoomRepository).save(room);
        verify(feedbackReportUseCase).generateForRoom(12L);
    }

    private AdminController newController(AuthService authService,
                                          MatchingRoomRepository matchingRoomRepository,
                                          GenerateGroupInvestmentFeedbackReportUseCase feedbackReportUseCase) {
        return new AdminController(
                authService,
                mock(UserRepository.class),
                matchingRoomRepository,
                mock(MatchingRoomMemberRepository.class),
                mock(UserDeletionReferenceCleanupService.class),
                mock(MatchingRoomService.class),
                mock(CompetitionService.class),
                mock(RankingService.class),
                mock(ChatService.class),
                mock(VoteService.class),
                mock(PriceBroadcaster.class),
                feedbackReportUseCase,
                mock(AssetMasterImportService.class)
        );
    }
}
