package com.uniport.controller;

import com.uniport.dto.BetaIosTestFlightSyncResponseDTO;
import com.uniport.service.BetaIosApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BetaIosTestFlightSyncAdminControllerTest {

    @Test
    void syncRequiresAdminTokenHeader() throws Exception {
        BetaIosApplicationService service = mock(BetaIosApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new BetaIosTestFlightSyncAdminController(service, "secret-token"))
                .build();

        mockMvc.perform(post("/api/admin/beta/ios/testflight-sync"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid beta admin token."));

        verify(service, never()).syncPendingInternalTesters();
    }

    @Test
    void syncFailsClosedWhenAdminTokenIsNotConfigured() throws Exception {
        BetaIosApplicationService service = mock(BetaIosApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new BetaIosTestFlightSyncAdminController(service, ""))
                .build();

        mockMvc.perform(post("/api/admin/beta/ios/testflight-sync")
                        .header("X-Beta-Admin-Token", "secret-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Beta admin token is not configured."));

        verify(service, never()).syncPendingInternalTesters();
    }

    @Test
    void syncRunsImmediatelyWithValidAdminToken() throws Exception {
        BetaIosApplicationService service = mock(BetaIosApplicationService.class);
        when(service.syncPendingInternalTesters())
                .thenReturn(BetaIosTestFlightSyncResponseDTO.builder()
                        .processed(3)
                        .added(1)
                        .pending(1)
                        .failed(1)
                        .skipped(0)
                        .build());
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new BetaIosTestFlightSyncAdminController(service, "secret-token"))
                .build();

        mockMvc.perform(post("/api/admin/beta/ios/testflight-sync")
                        .header("X-Beta-Admin-Token", "secret-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(3))
                .andExpect(jsonPath("$.added").value(1))
                .andExpect(jsonPath("$.pending").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.skipped").value(0));

        verify(service).syncPendingInternalTesters();
    }
}
