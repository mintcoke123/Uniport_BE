package com.uniport.service;

import com.uniport.dto.StockSearchItemDTO;
import com.uniport.exception.ApiException;
import com.uniport.repository.StockMasterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StockMasterSearchService 검증/limit 파싱 테스트. DB/네트워크 미사용.
 */
@ExtendWith(MockitoExtension.class)
class StockMasterSearchServiceTest {

    @Mock
    private StockMasterRepository stockMasterRepository;

    @InjectMocks
    private StockMasterSearchService stockMasterSearchService;

    @Test
    void search_emptyQuery_returnsEmptyList() {
        List<StockSearchItemDTO> result = stockMasterSearchService.search("", null);
        assertEquals(List.of(), result);

        result = stockMasterSearchService.search("   ", null);
        assertEquals(List.of(), result);

        result = stockMasterSearchService.search(null, null);
        assertEquals(List.of(), result);

        verify(stockMasterRepository, org.mockito.Mockito.never()).findByNameKrIlikeOrderByNameKrAsc(any(), any());
    }

    @Test
    void search_invalidLimit_throwsApiException400() {
        ApiException ex = assertThrows(ApiException.class, () ->
                stockMasterSearchService.search("삼성", "not-a-number"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Invalid limit", ex.getMessage());
    }

    @Test
    void search_validQuery_callsRepositoryWithClampedLimit() {
        when(stockMasterRepository.findByNameKrIlikeOrderByNameKrAsc(eq("삼성"), any(Pageable.class)))
                .thenReturn(List.of());

        stockMasterSearchService.search("삼성", null);
        verify(stockMasterRepository).findByNameKrIlikeOrderByNameKrAsc("삼성", any(Pageable.class));

        stockMasterSearchService.search("삼성", "100");
        verify(stockMasterRepository).findByNameKrIlikeOrderByNameKrAsc("삼성", any(Pageable.class));
    }
}
