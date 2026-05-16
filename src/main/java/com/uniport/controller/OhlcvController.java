package com.uniport.controller;

import com.uniport.dto.IndexChartPriceItemDTO;
import com.uniport.service.KisApiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

/**
 * OHLCV 차트 데이터. GET /api/ohlcv?code=005930&tf=1D&count=100&beforeDate=20260102
 * KIS 국내주식 기간별시세(일/주/월/년) API 연동. 응답: [{ date, open, high, low, close }, ...]
 */
@RestController
@RequestMapping("/api")
public class OhlcvController {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final KisApiService kisApiService;

    public OhlcvController(KisApiService kisApiService) {
        this.kisApiService = kisApiService;
    }

    @GetMapping("/ohlcv")
    public ResponseEntity<List<IndexChartPriceItemDTO>> getOhlcv(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "tf", defaultValue = "1D") String tf,
            @RequestParam(value = "count", defaultValue = "200") Integer count,
            @RequestParam(value = "beforeDate", required = false) String beforeDate) {
        if (code == null || code.isBlank()) {
            return ResponseEntity.ok(List.of());
        }
        int limit = count != null && count > 0 && count <= 100 ? count : 100;
        LocalDate endDate = endDateFor(beforeDate);
        String period = "1D".equalsIgnoreCase(tf) || "D".equalsIgnoreCase(tf) ? "D"
                : "1W".equalsIgnoreCase(tf) || "W".equalsIgnoreCase(tf) ? "W"
                : "M".equalsIgnoreCase(tf) ? "M" : "Y".equalsIgnoreCase(tf) ? "Y" : "D";
        LocalDate startDate = startDateFor(endDate, period, limit);
        try {
            List<IndexChartPriceItemDTO> list = kisApiService.getStockDailyChartPrice(
                    code.trim(),
                    startDate.format(YYYYMMDD),
                    endDate.format(YYYYMMDD),
                    period);
            return ResponseEntity.ok(latestCandles(list, limit));
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    private LocalDate endDateFor(String beforeDate) {
        if (beforeDate == null || beforeDate.isBlank()) {
            return LocalDate.now();
        }
        LocalDate cursorDate = parseDate(beforeDate.trim());
        if (LocalDate.MIN.equals(cursorDate)) {
            return LocalDate.now();
        }
        return cursorDate.minusDays(1);
    }

    private LocalDate startDateFor(LocalDate endDate, String period, int count) {
        return switch (period) {
            case "W" -> endDate.minusWeeks(count + 2L);
            case "M" -> endDate.minusMonths(count + 1L);
            case "Y" -> endDate.minusYears(count + 1L);
            default -> endDate.minusDays(count * 2L);
        };
    }

    private List<IndexChartPriceItemDTO> latestCandles(List<IndexChartPriceItemDTO> candles, int limit) {
        if (candles == null || candles.isEmpty()) {
            return List.of();
        }
        List<IndexChartPriceItemDTO> sorted = candles.stream()
                .filter(candle -> candle != null && candle.getDate() != null && !candle.getDate().isBlank())
                .sorted(Comparator.comparing(candle -> parseDate(candle.getDate())))
                .toList();
        if (sorted.size() <= limit) {
            return sorted;
        }
        return sorted.subList(sorted.size() - limit, sorted.size());
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value, YYYYMMDD);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException ignoredAgain) {
                return LocalDate.MIN;
            }
        }
    }
}
