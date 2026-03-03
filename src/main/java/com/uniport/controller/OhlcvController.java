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
import java.util.List;

/**
 * OHLCV 차트 데이터. GET /api/ohlcv?code=005930&tf=1D&count=200
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
            @RequestParam(value = "count", defaultValue = "200") Integer count) {
        if (code == null || code.isBlank()) {
            return ResponseEntity.ok(List.of());
        }
        int limit = count != null && count > 0 && count <= 200 ? count : 200;
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(limit - 1);
        String period = "1D".equalsIgnoreCase(tf) || "D".equalsIgnoreCase(tf) ? "D"
                : "1W".equalsIgnoreCase(tf) || "W".equalsIgnoreCase(tf) ? "W"
                : "M".equalsIgnoreCase(tf) ? "M" : "Y".equalsIgnoreCase(tf) ? "Y" : "D";
        try {
            List<IndexChartPriceItemDTO> list = kisApiService.getStockDailyChartPrice(
                    code.trim(),
                    startDate.format(YYYYMMDD),
                    endDate.format(YYYYMMDD),
                    period);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }
}
