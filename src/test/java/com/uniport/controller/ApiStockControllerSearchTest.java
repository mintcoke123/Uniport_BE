package com.uniport.controller;

import com.uniport.dto.StockSearchItemDTO;
import com.uniport.dto.StockSearchResponseDTO;
import com.uniport.service.AuthService;
import com.uniport.service.StockMasterSearchService;
import com.uniport.service.StockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiStockControllerSearchTest {

    @Mock
    private StockService stockService;

    @Mock
    private AuthService authService;

    @Mock
    private StockMasterSearchService stockMasterSearchService;

    @InjectMocks
    private ApiStockController apiStockController;

    @Test
    void search_passesCurrentParametersToService() {
        StockSearchResponseDTO expected = StockSearchResponseDTO.builder()
                .items(List.of())
                .page(0)
                .size(10)
                .hasNext(false)
                .build();
        when(stockMasterSearchService.search(null, null, null, null, null)).thenReturn(expected);

        ResponseEntity<StockSearchResponseDTO> response = apiStockController.search(null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expected, response.getBody());
        verify(stockMasterSearchService).search(null, null, null, null, null);
    }

    @Test
    void search_supportsLegacyQueryAndLimitParameters() {
        StockSearchResponseDTO expected = StockSearchResponseDTO.builder()
                .items(List.of(
                        StockSearchItemDTO.builder()
                                .stockId("KRX_005930")
                                .name("삼성전자")
                                .symbol("005930")
                                .market("KOSPI")
                                .build()
                ))
                .page(0)
                .size(20)
                .hasNext(false)
                .build();
        when(stockMasterSearchService.search(null, null, null, "삼성", "20")).thenReturn(expected);

        ResponseEntity<StockSearchResponseDTO> response =
                apiStockController.search(null, null, null, "삼성", "20");

        assertEquals(1, response.getBody().getItems().size());
        assertEquals("KRX_005930", response.getBody().getItems().get(0).getStockId());
        assertEquals("005930", response.getBody().getItems().get(0).getSymbol());
        assertFalse(Boolean.TRUE.equals(response.getBody().getHasNext()));
        verify(stockMasterSearchService).search(null, null, null, "삼성", "20");
    }

    @Test
    void search_supportsKeywordPageAndSizeParameters() {
        StockSearchResponseDTO expected = StockSearchResponseDTO.builder()
                .items(List.of(
                        StockSearchItemDTO.builder()
                                .stockId("US_AAPL")
                                .name("Apple Inc.")
                                .symbol("AAPL")
                                .market("NASDAQ")
                                .build()
                ))
                .page(1)
                .size(5)
                .hasNext(true)
                .build();
        when(stockMasterSearchService.search("apple", 1, 5, null, null)).thenReturn(expected);

        ResponseEntity<StockSearchResponseDTO> response =
                apiStockController.search("apple", 1, 5, null, null);

        assertEquals(1, response.getBody().getPage());
        assertEquals(5, response.getBody().getSize());
        assertTrue(Boolean.TRUE.equals(response.getBody().getHasNext()));
        assertEquals("US_AAPL", response.getBody().getItems().get(0).getStockId());
        verify(stockMasterSearchService).search("apple", 1, 5, null, null);
    }
}
