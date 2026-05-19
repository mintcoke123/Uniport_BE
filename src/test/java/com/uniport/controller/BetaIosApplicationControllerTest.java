package com.uniport.controller;

import com.uniport.dto.BetaIosApplicationRequestDTO;
import com.uniport.dto.BetaIosApplicationResponseDTO;
import com.uniport.service.BetaIosApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BetaIosApplicationControllerTest {

    @Test
    void submitIosApplicationAllowsAnonymousApplicationAndReturnsInviteStatus() throws Exception {
        BetaIosApplicationService service = mock(BetaIosApplicationService.class);
        when(service.submit(any(BetaIosApplicationRequestDTO.class)))
                .thenReturn(BetaIosApplicationResponseDTO.builder()
                        .id(10L)
                        .name("김유니")
                        .appleIdEmail("ios@example.com")
                        .contactEmail("contact@example.com")
                        .device("iPhone")
                        .status("USER_INVITE_SENT")
                        .message("Apple 초대 메일을 보냈습니다.")
                        .build());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new BetaIosApplicationController(service)).build();

        mockMvc.perform(post("/api/beta/ios-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "김유니",
                                  "appleIdEmail": "ios@example.com",
                                  "contactEmail": "contact@example.com",
                                  "device": "iPhone",
                                  "consent": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.appleIdEmail").value("ios@example.com"))
                .andExpect(jsonPath("$.status").value("USER_INVITE_SENT"))
                .andExpect(jsonPath("$.message").value("Apple 초대 메일을 보냈습니다."));

        verify(service).submit(any(BetaIosApplicationRequestDTO.class));
    }
}
