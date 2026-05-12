package com.uniport.controller;

import com.uniport.entity.User;
import com.uniport.repository.FriendInviteRepository;
import com.uniport.repository.HoldingRepository;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import com.uniport.repository.OrderRepository;
import com.uniport.repository.UserRepository;
import com.uniport.service.AuthService;
import com.uniport.service.ChatService;
import com.uniport.service.CompetitionService;
import com.uniport.service.MatchingRoomService;
import com.uniport.service.RankingService;
import com.uniport.service.VoteService;
import com.uniport.service.feedback.GenerateGroupInvestmentFeedbackReportUseCase;
import com.uniport.service.importer.AssetMasterImportService;
import com.uniport.service.importer.ImportResult;
import com.uniport.websocket.PriceBroadcaster;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminControllerAssetImportTest {

    @Test
    void importAllAssets_runsDomesticAndUsImporterForAdmin() throws Exception {
        AuthService authService = mock(AuthService.class);
        AssetMasterImportService importService = mock(AssetMasterImportService.class);
        AdminController controller = newController(authService, importService);
        when(authService.getUserFromToken("Bearer admin"))
                .thenReturn(User.builder().role("admin").build());
        when(importService.importAll()).thenReturn(new AssetMasterImportService.CombinedImportResult(
                new ImportResult(2, 3, 1),
                new ImportResult(5, 7, 2)
        ));

        ResponseEntity<Map<String, Object>> response = controller.importAllAssets("Bearer admin");

        verify(importService).importAll();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertEquals(true, body.get("success"));
        assertEquals("all", body.get("scope"));
        assertEquals(7, ((Map<?, ?>) body.get("total")).get("inserted"));
        assertEquals(10, ((Map<?, ?>) body.get("total")).get("updated"));
        assertEquals(3, ((Map<?, ?>) body.get("total")).get("skipped"));
    }

    private AdminController newController(AuthService authService,
                                          AssetMasterImportService importService) {
        return new AdminController(
                authService,
                mock(UserRepository.class),
                mock(FriendInviteRepository.class),
                mock(OrderRepository.class),
                mock(HoldingRepository.class),
                mock(MatchingRoomRepository.class),
                mock(MatchingRoomMemberRepository.class),
                mock(MatchingRoomService.class),
                mock(CompetitionService.class),
                mock(RankingService.class),
                mock(ChatService.class),
                mock(VoteService.class),
                mock(PriceBroadcaster.class),
                mock(GenerateGroupInvestmentFeedbackReportUseCase.class),
                importService
        );
    }
}
