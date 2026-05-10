package com.uniport.controller;

import com.uniport.dto.CustomEtfAssetSearchItemDTO;
import com.uniport.dto.CustomEtfAssetSearchResponseDTO;
import com.uniport.entity.User;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.EtfDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        when(currentUserResolver.resolveRequired(null, null)).thenReturn(User.builder().id(1L).build());
        when(etfDataService.searchAssets("apple", "STOCK", "US", 0, 10)).thenReturn(expected);

        ResponseEntity<CustomEtfAssetSearchResponseDTO> response =
                customEtfController.searchAssets(null, null, "apple", "STOCK", "US", 0, 10);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("US_AAPL", response.getBody().getItems().get(0).getAssetId());
        verify(currentUserResolver).resolveRequired(null, null);
        verify(etfDataService).searchAssets("apple", "STOCK", "US", 0, 10);
    }
}
