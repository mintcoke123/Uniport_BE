package com.uniport.controller;

import com.uniport.dto.IndexChartPriceItemDTO;
import com.uniport.service.KisApiService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OhlcvControllerTest {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    @Mock
    private KisApiService kisApiService;

    @InjectMocks
    private OhlcvController ohlcvController;

    @Test
    void getOhlcv_weeklyPeriodUsesEnoughLookbackForRequestedCandleCount() {
        when(kisApiService.getStockDailyChartPrice(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of());

        ohlcvController.getOhlcv("005930", "1W", 100, null);

        ArgumentCaptor<String> startDateCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> endDateCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> periodCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(kisApiService).getStockDailyChartPrice(
                org.mockito.Mockito.eq("005930"),
                startDateCaptor.capture(),
                endDateCaptor.capture(),
                periodCaptor.capture()
        );
        LocalDate startDate = LocalDate.parse(startDateCaptor.getValue(), YYYYMMDD);
        LocalDate endDate = LocalDate.parse(endDateCaptor.getValue(), YYYYMMDD);

        assertEquals("W", periodCaptor.getValue());
        assertTrue(
                ChronoUnit.DAYS.between(startDate, endDate) >= 700,
                "weekly chart should request at least 100 weeks of source data"
        );
    }

    @Test
    void getOhlcv_returnsLatestRequestedCandlesSortedAscending() {
        when(kisApiService.getStockDailyChartPrice(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(
                        candle("20260101", "100"),
                        candle("20260103", "120"),
                        candle("20260102", "110")
                ));

        ResponseEntity<List<IndexChartPriceItemDTO>> response = ohlcvController.getOhlcv("005930", "1D", 2, null);

        List<IndexChartPriceItemDTO> body = response.getBody();
        assertEquals(2, body.size());
        assertEquals(List.of("20260102", "20260103"), body.stream().map(IndexChartPriceItemDTO::getDate).toList());
        assertEquals(0, new BigDecimal("110").compareTo(body.get(0).getClose()));
        assertEquals(0, new BigDecimal("120").compareTo(body.get(1).getClose()));
    }

    @Test
    void getOhlcv_beforeDateRequestsOlderWindowExcludingCursorDate() {
        when(kisApiService.getStockDailyChartPrice(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(candle("20251231", "90")));

        ResponseEntity<List<IndexChartPriceItemDTO>> response =
                ohlcvController.getOhlcv("005930", "1D", 100, "20260102");

        ArgumentCaptor<String> startDateCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> endDateCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(kisApiService).getStockDailyChartPrice(
                org.mockito.Mockito.eq("005930"),
                startDateCaptor.capture(),
                endDateCaptor.capture(),
                org.mockito.Mockito.eq("D")
        );
        LocalDate startDate = LocalDate.parse(startDateCaptor.getValue(), YYYYMMDD);
        LocalDate endDate = LocalDate.parse(endDateCaptor.getValue(), YYYYMMDD);

        assertEquals(LocalDate.of(2026, 1, 1), endDate);
        assertTrue(startDate.isBefore(endDate));
        assertEquals(List.of("20251231"), response.getBody().stream().map(IndexChartPriceItemDTO::getDate).toList());
    }

    private IndexChartPriceItemDTO candle(String date, String close) {
        BigDecimal price = new BigDecimal(close);
        return IndexChartPriceItemDTO.builder()
                .date(date)
                .open(price)
                .high(price)
                .low(price)
                .close(price)
                .build();
    }
}
