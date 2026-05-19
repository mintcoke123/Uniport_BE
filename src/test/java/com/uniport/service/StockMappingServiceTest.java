package com.uniport.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockMappingServiceTest {

    @Test
    void mapStocks_mapsAliasToCanonicalStock() {
        StockMappingService service = new StockMappingService();

        List<MappedStock> stocks = service.mapStocks("하이닉스 HBM 투자 기대감 확대", "AI 서버 수요 증가");

        assertEquals("SK하이닉스", stocks.get(0).name());
        assertEquals("000660", stocks.get(0).symbol());
        assertEquals("DIRECT", stocks.get(0).matchType());
    }

    @Test
    void mapStocks_keepsDirectMentionsBeforeThemeCandidates() {
        StockMappingService service = new StockMappingService();

        List<MappedStock> stocks = service.mapStocks("Apple 반도체 투자 확대", "HBM 수요 증가");

        assertEquals(List.of("Apple", "삼성전자", "SK하이닉스", "한미반도체"),
                stocks.stream().map(MappedStock::name).toList());
        assertEquals(List.of("DIRECT", "THEME_CANDIDATE", "THEME_CANDIDATE", "THEME_CANDIDATE"),
                stocks.stream().map(MappedStock::matchType).toList());
    }

    @Test
    void mapStocks_deduplicatesDirectStocksWhenThemeAlsoMatches() {
        StockMappingService service = new StockMappingService();

        List<MappedStock> stocks = service.mapStocks("SK하이닉스 HBM 투자 기대감");

        assertEquals(1, stocks.stream()
                .filter(stock -> stock.symbol().equals("000660"))
                .count());
        assertEquals("DIRECT", stocks.stream()
                .filter(stock -> stock.symbol().equals("000660"))
                .findFirst()
                .orElseThrow()
                .matchType());
    }

    @Test
    void mapStocks_matchesEnglishAliasesCaseInsensitivelyInTextOrder() {
        StockMappingService service = new StockMappingService();

        List<MappedStock> stocks = service.mapStocks("tesla와 NVIDIA 실적 기대감");

        assertEquals(List.of("Tesla", "NVIDIA"), stocks.stream().map(MappedStock::name).toList());
        assertEquals(List.of("TSLA", "NVDA"), stocks.stream().map(MappedStock::symbol).toList());
    }

    @Test
    void mapStocks_doesNotMatchEnglishAliasInsideLargerToken() {
        StockMappingService service = new StockMappingService();

        assertTrue(service.mapStocks("NOTNAVER 실적 전망").isEmpty());
        assertTrue(service.mapStocks("S-OILERS refining update").isEmpty());
    }
}
