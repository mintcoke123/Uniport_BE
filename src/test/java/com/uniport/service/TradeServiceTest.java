package com.uniport.service;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeServiceTest {

    @Test
    void allowsTradingOutsideMarketHoursWhileTimeLimitIsDisabledForTesting() {
        assertTrue(TradeService.isTradingHours(LocalTime.of(3, 0)));
    }
}
