package com.uniport.service;

import com.uniport.dto.StockSearchResponseDTO;
import com.uniport.dto.StockVisualDTO;
import com.uniport.entity.StockMaster;
import com.uniport.exception.ApiException;
import com.uniport.repository.StockMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockMasterSearchServiceTest {

    @Mock
    private StockMasterRepository stockMasterRepository;

    @Mock
    private StockVisualAssetResolver stockVisualAssetResolver;

    private StockMasterSearchService stockMasterSearchService;

    @BeforeEach
    void setUp() {
        stockMasterSearchService = new StockMasterSearchService(
                stockMasterRepository,
                stockVisualAssetResolver,
                new StockSymbolLogoUrlResolver("https://uniportbe-production.up.railway.app")
        );
    }

    @Test
    void search_blankKeyword_returnsEmptyPagedResponse() {
        StockSearchResponseDTO result = stockMasterSearchService.search("", null, null, null, null);
        assertEquals(List.of(), result.getItems());
        assertEquals(0, result.getPage());
        assertEquals(10, result.getSize());
        assertEquals(Boolean.FALSE, result.getHasNext());

        result = stockMasterSearchService.search("   ", null, null, null, null);
        assertEquals(List.of(), result.getItems());

        result = stockMasterSearchService.search(null, null, null, null, null);
        assertEquals(List.of(), result.getItems());

        verify(stockMasterRepository, never()).findByNameKrIlikeOrderByNameKrAsc(any(), any());
    }

    @Test
    void search_invalidLegacyLimit_throwsBadRequest() {
        ApiException ex = assertThrows(ApiException.class,
                () -> stockMasterSearchService.search("삼성", null, null, null, "not-a-number"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Invalid size", ex.getMessage());
    }

    @Test
    void search_usesKeywordPaginationAndMapsItems() {
        StockMaster samsung = StockMaster.builder()
                .code("005930")
                .nameKr("삼성전자")
                .market("KOSPI")
                .build();
        StockMaster apple = StockMaster.builder()
                .code("AAPL")
                .nameKr("Apple Inc.")
                .market("NASDAQ")
                .build();

        when(stockMasterRepository.findByNameKrIlikeOrderByNameKrAsc(eq("삼성"), any(Pageable.class)))
                .thenReturn(List.of(samsung));
        when(stockMasterRepository.findByNameKrIlikeOrderByNameKrAsc(eq("apple"), any(Pageable.class)))
                .thenReturn(List.of(apple));
        when(stockVisualAssetResolver.resolve("KOSPI", "005930", "삼성전자", null))
                .thenReturn(visual("삼성"));
        when(stockVisualAssetResolver.resolve("NASDAQ", "AAPL", "Apple Inc.", null))
                .thenReturn(visual("AAPL"));

        StockSearchResponseDTO koreanResult =
                stockMasterSearchService.search("삼성", 0, 10, null, null);
        assertEquals(1, koreanResult.getItems().size());
        assertEquals("KRX_005930", koreanResult.getItems().get(0).getStockId());
        assertEquals("005930", koreanResult.getItems().get(0).getSymbol());
        assertEquals("삼성", koreanResult.getItems().get(0).getVisual().getText());

        StockSearchResponseDTO usResult =
                stockMasterSearchService.search("apple", 1, 5, null, null);
        assertEquals(1, usResult.getPage());
        assertEquals(5, usResult.getSize());
        assertEquals("US_AAPL", usResult.getItems().get(0).getStockId());
        assertEquals("NASDAQ", usResult.getItems().get(0).getMarket());
        assertEquals("AAPL", usResult.getItems().get(0).getVisual().getText());

        verify(stockMasterRepository).findByNameKrIlikeOrderByNameKrAsc(eq("삼성"), any(Pageable.class));
        verify(stockMasterRepository).findByNameKrIlikeOrderByNameKrAsc(eq("apple"), any(Pageable.class));
    }

    @Test
    void search_virtualStockKeyword_returnsWaveTechWithoutRepositoryHit() {
        StockSearchResponseDTO result =
                stockMasterSearchService.search("웨이브", 0, 10, null, null);

        assertEquals(1, result.getItems().size());
        assertEquals("KRX_999999", result.getItems().get(0).getStockId());
        assertEquals("999999", result.getItems().get(0).getSymbol());
        assertEquals("웨이브테크", result.getItems().get(0).getName());
        assertEquals("VIRTUAL", result.getItems().get(0).getMarket());
        verify(stockMasterRepository, never()).findByNameKrIlikeOrderByNameKrAsc(any(), any());
    }

    @Test
    void search_genericVirtualKeywordReturnsAllVirtualStocksWithoutRepositoryHit() {
        StockSearchResponseDTO result =
                stockMasterSearchService.search("가상", 0, 10, null, null);

        assertEquals(6, result.getItems().size());
        assertEquals("KRX_999999", result.getItems().get(0).getStockId());
        assertEquals("KRX_999998", result.getItems().get(1).getStockId());
        assertEquals("뉴로펄스", result.getItems().get(1).getName());
        assertEquals(Boolean.FALSE, result.getHasNext());
        verify(stockMasterRepository, never()).findByNameKrIlikeOrderByNameKrAsc(any(), any());
    }

    @Test
    void search_clampsLegacyLimitToMaxSize() {
        StockMaster samsung = StockMaster.builder()
                .code("005930")
                .nameKr("삼성전자")
                .market("KOSPI")
                .build();

        when(stockMasterRepository.findByNameKrIlikeOrderByNameKrAsc(eq("삼성"), any(Pageable.class)))
                .thenReturn(List.of(samsung));
        when(stockVisualAssetResolver.resolve("KOSPI", "005930", "삼성전자", null))
                .thenReturn(visual("삼성"));

        StockSearchResponseDTO result =
                stockMasterSearchService.search(null, null, null, "삼성", "100");

        assertEquals(20, result.getSize());
        assertTrue(result.getItems().size() <= 20);
        verify(stockMasterRepository).findByNameKrIlikeOrderByNameKrAsc(eq("삼성"), any(Pageable.class));
    }

    private StockVisualDTO visual(String text) {
        return StockVisualDTO.builder()
                .type("FALLBACK_SYMBOL")
                .text(text)
                .bgColor("#EEF2FF")
                .textColor("#4F46E5")
                .build();
    }
}
