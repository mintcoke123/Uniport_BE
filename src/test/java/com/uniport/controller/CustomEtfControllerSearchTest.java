package com.uniport.controller;

import com.uniport.dto.CustomEtfAssetSearchItemDTO;
import com.uniport.dto.CustomEtfAssetSearchResponseDTO;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.EtfDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CustomEtfControllerSearchTest {

    @Mock
    private EtfDataService etfDataService;

    @Mock
    private CurrentUserResolver currentUserResolver;

    @InjectMocks
    private CustomEtfController customEtfController;

    @Test
    void searchAssets_passesFiltersToEtfDataService() {
        CustomEtfAssetSearchResponseDTO expected = CustomEtfAssetSearchResponseDTO.builder()
                .items(List.of(CustomEtfAssetSearchItemDTO.builder()
                        .assetId("US_AAPL")
                        .stockId("US_AAPL")
                        .name("Apple Inc.")
                        .symbol("AAPL")
                        .market("NASDAQ")
                        .assetType("STOCK")
                        .currency("USD")
                        .build()))
                .page(0)
                .size(10)
                .totalCount(1)
                .hasNext(false)
                .build();
        when(etfDataService.searchAssets("apple", "STOCK", "US", 0, 10)).thenReturn(expected);

        ResponseEntity<CustomEtfAssetSearchResponseDTO> response =
                customEtfController.searchAssets("apple", null, null, "STOCK", "US", 0, 10);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("US_AAPL", response.getBody().getItems().get(0).getAssetId());
        verifyNoInteractions(currentUserResolver);
        verify(etfDataService).searchAssets("apple", "STOCK", "US", 0, 10);
    }

    @Test
    void searchAssets_acceptsQueryAliasUsedByFrontend() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(customEtfController).build();
        CustomEtfAssetSearchResponseDTO expected = CustomEtfAssetSearchResponseDTO.builder()
                .items(List.of(CustomEtfAssetSearchItemDTO.builder()
                        .assetId("US_AAPL")
                        .stockId("US_AAPL")
                        .name("Apple Inc.")
                        .symbol("AAPL")
                        .market("NASDAQ")
                        .assetType("STOCK")
                        .currency("USD")
                        .build()))
                .page(0)
                .size(10)
                .totalCount(1)
                .hasNext(false)
                .build();
        when(etfDataService.searchAssets("apple", "STOCK", "US", 0, 10)).thenReturn(expected);

        mockMvc.perform(get("/api/custom-etfs/assets/search")
                        .param("query", "apple")
                        .param("assetType", "STOCK")
                        .param("market", "US")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].assetId").value("US_AAPL"));

        verifyNoInteractions(currentUserResolver);
    }
}
