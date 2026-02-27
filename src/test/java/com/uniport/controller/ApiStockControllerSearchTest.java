package com.uniport.controller;

import com.uniport.dto.StockSearchItemDTO;
import com.uniport.exception.ApiException;
import com.uniport.exception.GlobalExceptionHandler;
import com.uniport.service.AuthService;
import com.uniport.service.StockMasterSearchService;
import com.uniport.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/stocks/search 검증 테스트. Repository/DB 없이 Service mock.
 */
@WebMvcTest(controllers = ApiStockController.class)
@Import(GlobalExceptionHandler.class)
class ApiStockControllerSearchTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StockService stockService;
    @MockBean
    private AuthService authService;
    @MockBean
    private StockMasterSearchService stockMasterSearchService;

    @Test
    void search_emptyQuery_returns200AndEmptyList() throws Exception {
        when(stockMasterSearchService.search(anyString(), anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/stocks/search").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(stockMasterSearchService).search(null, null);
    }

    @Test
    void search_blankQuery_returns200AndEmptyList() throws Exception {
        when(stockMasterSearchService.search("", anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/stocks/search")
                        .param("query", "   ")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void search_invalidLimit_returns400() throws Exception {
        when(stockMasterSearchService.search("삼성", "not-a-number"))
                .thenThrow(new ApiException("Invalid limit", HttpStatus.BAD_REQUEST));

        mockMvc.perform(get("/api/stocks/search")
                        .param("query", "삼성")
                        .param("limit", "not-a-number")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message").exists());

        verify(stockMasterSearchService).search("삼성", "not-a-number");
    }

    @Test
    void search_validQuery_returns200AndList() throws Exception {
        List<StockSearchItemDTO> list = List.of(
                StockSearchItemDTO.builder().id(5930L).code("005930").name("삼성전자").market("KOSPI").build()
        );
        when(stockMasterSearchService.search("삼성", "20")).thenReturn(list);

        mockMvc.perform(get("/api/stocks/search")
                        .param("query", "삼성")
                        .param("limit", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(5930)))
                .andExpect(jsonPath("$[0].code", is("005930")))
                .andExpect(jsonPath("$[0].name", is("삼성전자")))
                .andExpect(jsonPath("$[0].market", is("KOSPI")));

        verify(stockMasterSearchService).search("삼성", "20");
    }

    @Test
    void getStockDetail_unchanged_doesNotCallSearchService() throws Exception {
        mockMvc.perform(get("/api/stocks/5930").accept(MediaType.APPLICATION_JSON));
        verify(stockMasterSearchService, never()).search(anyString(), anyString());
    }
}
