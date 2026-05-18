package com.uniport.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualStockServiceTest {

    @Test
    void priceAtMillis_followsFiveMinuteSineWaveWithTwoHundredThousandAmplitude() {
        VirtualStockService service = new VirtualStockService();

        assertEquals(new BigDecimal("1000000"), service.priceAtMillis(0L));
        assertEquals(new BigDecimal("1190211"), service.priceAtMillis(60_000L));
        assertEquals(new BigDecimal("1200000"), service.priceAtMillis(75_000L));
        assertEquals(new BigDecimal("1000000"), service.priceAtMillis(150_000L));
        assertEquals(new BigDecimal("800000"), service.priceAtMillis(225_000L));
        assertEquals(new BigDecimal("1000000"), service.priceAtMillis(300_000L));
    }

    @Test
    void isVirtualStock_matchesCodeAndUserFacingKeywords() {
        VirtualStockService service = new VirtualStockService();

        assertTrue(service.isVirtualStockCode("999999"));
        assertTrue(service.isVirtualStockCode("999998"));
        assertTrue(service.isVirtualStockCode("999994"));
        assertTrue(service.matchesKeyword("웨이브"));
        assertTrue(service.matchesKeyword("Wave"));
        assertTrue(service.matchesKeyword("뉴로"));
        assertTrue(service.matchesKeyword("virtual"));
        assertTrue(service.matchesKeyword("999999"));
    }

    @Test
    void codes_returnsWaveTechAndFiveAdditionalVirtualStocks() {
        VirtualStockService service = new VirtualStockService();

        assertEquals(
                List.of("999999", "999998", "999997", "999996", "999995", "999994"),
                service.codes()
        );
    }

    @Test
    void priceAtMillis_usesEachVirtualStocksOwnBaseAmplitudeAndPeriod() {
        VirtualStockService service = new VirtualStockService();

        assertEquals(new BigDecimal("575000"), service.priceAtMillis("999998", 45_000L));
        assertEquals(new BigDecimal("1500000"), service.priceAtMillis("999997", 105_000L));
        assertEquals(new BigDecimal("300000"), service.priceAtMillis("999996", 30_000L));
        assertEquals(new BigDecimal("900000"), service.priceAtMillis("999995", 150_000L));
        assertEquals(new BigDecimal("450000"), service.priceAtMillis("999994", 60_000L));
    }

    @Test
    void searchItemsMatching_returnsAllVirtualStocksForGenericVirtualKeyword() {
        VirtualStockService service = new VirtualStockService();

        assertEquals(6, service.searchItemsMatching("가상").size());
        assertEquals("뉴로펄스", service.searchItemsMatching("뉴로").get(0).getName());
        assertEquals("999998", service.searchItemsMatching("999998").get(0).getSymbol());
    }

    @Test
    void currentPriceDto_usesRequestedVirtualStockIdentityAndPriceRange() {
        VirtualStockService service = new VirtualStockService();

        assertEquals("솔라리온", service.currentPriceDto("999997").getStockName());
        assertEquals("999997", service.currentPriceDto("999997").getStockCode());
        assertEquals(new BigDecimal("900000"), service.currentPriceDto("999997").getLowPrice());
        assertEquals(new BigDecimal("1500000"), service.currentPriceDto("999997").getHighPrice());
    }
}
