package com.uniport.controller;

import com.uniport.dto.InvestmentTestReservationRequestDTO;
import com.uniport.dto.InvestmentTestReservationResponseDTO;
import com.uniport.service.InvestmentTestReservationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvestmentTestReservationControllerTest {

    @Test
    void submitReservationAllowsAnonymousRequestAndReturnsReservationResponse() throws Exception {
        InvestmentTestReservationService service = mock(InvestmentTestReservationService.class);
        when(service.submit(any(InvestmentTestReservationRequestDTO.class), eq("Mozilla/5.0")))
                .thenReturn(InvestmentTestReservationResponseDTO.builder()
                        .id(15L)
                        .name("김유니")
                        .contactType("EMAIL")
                        .contactValue("kim@example.com")
                        .resultKey("turtle")
                        .resultTitle("조심스러운 거북이형")
                        .message("투자성향 테스트 사전예약이 저장됐습니다.")
                        .build());
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new InvestmentTestReservationController(service))
                .build();

        mockMvc.perform(post("/api/investment-test/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Mozilla/5.0")
                        .content("""
                                {
                                  "name": "김유니",
                                  "contact": "kim@example.com",
                                  "consent": true,
                                  "resultKey": "turtle",
                                  "resultTitle": "조심스러운 거북이형",
                                  "interestKeywords": ["AI 반도체"],
                                  "answers": {"risk": "hold"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(15))
                .andExpect(jsonPath("$.name").value("김유니"))
                .andExpect(jsonPath("$.contactType").value("EMAIL"))
                .andExpect(jsonPath("$.contactValue").value("kim@example.com"))
                .andExpect(jsonPath("$.resultKey").value("turtle"))
                .andExpect(jsonPath("$.resultTitle").value("조심스러운 거북이형"))
                .andExpect(jsonPath("$.message").value("투자성향 테스트 사전예약이 저장됐습니다."));

        ArgumentCaptor<InvestmentTestReservationRequestDTO> captor =
                ArgumentCaptor.forClass(InvestmentTestReservationRequestDTO.class);
        verify(service).submit(captor.capture(), eq("Mozilla/5.0"));
        assertEquals("김유니", captor.getValue().getName());
        assertEquals("kim@example.com", captor.getValue().getContact());
    }
}
