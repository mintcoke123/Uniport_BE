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
    void mapStocks_matchesSaveTickerDollarTickerTags() {
        StockMappingService service = new StockMappingService();

        List<MappedStock> stocks = service.mapStocks("$NVDA $FLNC $INTC $MSTR 관련 발표");

        assertEquals(List.of("NVIDIA", "Fluence Energy", "Intel", "Strategy"),
                stocks.stream().map(MappedStock::name).toList());
    }

    @Test
    void mapStocks_matchesSaveTickerCompanyMentions() {
        StockMappingService service = new StockMappingService();

        List<MappedStock> stocks = service.mapStocks(
                "이란 분쟁 장기화에 제트블루 유류비 상승 경고",
                "대법원, 로빈후드 IPO 분쟁 관련 의견 요청",
                "하니웰 퀀티넘, 미국 IPO 기업가치 목표"
        );

        assertEquals(List.of("JetBlue Airways", "Robinhood", "Honeywell"),
                stocks.stream().map(MappedStock::name).toList());
    }

    @Test
    void mapStocks_doesNotMatchEnglishAliasInsideLargerToken() {
        StockMappingService service = new StockMappingService();

        assertTrue(service.mapStocks("NOTNAVER 실적 전망").isEmpty());
        assertTrue(service.mapStocks("S-OILERS refining update").isEmpty());
    }

    @Test
    void mapStocks_mapsUsAiInfrastructureAndMegacapNames() {
        StockMappingService service = new StockMappingService();

        List<MappedStock> stocks = service.mapStocks(
                "Dell Technologies earnings beat on AI server demand",
                "Broadcom, AMD, Oracle and Meta are also watched as AI infrastructure names"
        );

        assertEquals(
                List.of("Dell Technologies", "Broadcom", "AMD", "Oracle", "Meta"),
                stocks.stream().limit(5).map(MappedStock::name).toList()
        );
        assertEquals(
                List.of("DELL", "AVGO", "AMD", "ORCL", "META"),
                stocks.stream().limit(5).map(MappedStock::symbol).toList()
        );
        assertTrue(stocks.stream().limit(5).allMatch(stock -> stock.matchType().equals("DIRECT")));
    }

    @Test
    void mapStocks_mapsTelegramDisclosureStockCodeAndNewKoreanAliases() {
        StockMappingService service = new StockMappingService();

        List<MappedStock> stocks = service.mapStocks(
                "기업명: 올릭스(시가총액: 3조 5,079억) A226950",
                "[특징주] 링네트, 엔비디아 주요 협력사 네이버클라우드 부각에 상승",
                "[MM] 풍산, 방산 매각 카드 다시 만지작"
        );

        assertEquals(
                List.of("올릭스", "링네트", "NVIDIA", "NAVER", "풍산"),
                stocks.stream().limit(5).map(MappedStock::name).toList()
        );
        assertEquals(
                List.of("226950", "042500", "NVDA", "035420", "103140"),
                stocks.stream().limit(5).map(MappedStock::symbol).toList()
        );
        assertTrue(stocks.stream().limit(5).allMatch(stock -> stock.matchType().equals("DIRECT")));
    }

    @Test
    void mapStocks_doesNotMatchDellInsideKoreanModelText() {
        StockMappingService service = new StockMappingService();

        List<MappedStock> stocks = service.mapStocks(
                "자화전자, 애플 차세대 모델 3종 카메라 액추에이터 사실상 독점 수주"
        );

        assertEquals(List.of("자화전자", "Apple"), stocks.stream().map(MappedStock::name).toList());
        assertEquals(List.of("033240", "AAPL"), stocks.stream().map(MappedStock::symbol).toList());
    }
}
