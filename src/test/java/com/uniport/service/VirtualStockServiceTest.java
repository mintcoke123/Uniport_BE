package com.uniport.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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
        assertTrue(service.matchesKeyword("웨이브"));
        assertTrue(service.matchesKeyword("Wave"));
        assertTrue(service.matchesKeyword("999999"));
    }
}
