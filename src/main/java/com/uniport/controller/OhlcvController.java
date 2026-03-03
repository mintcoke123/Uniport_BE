package com.uniport.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OHLCV 차트 데이터. GET /api/ohlcv?code=005930&tf=1D&count=200
 * 프론트 lightweight-charts 캔들용. KIS 연동 전까지 빈 배열 반환.
 */
@RestController
@RequestMapping("/api")
public class OhlcvController {

    @GetMapping("/ohlcv")
    public ResponseEntity<List<?>> getOhlcv(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "tf", defaultValue = "1D") String tf,
            @RequestParam(value = "count", defaultValue = "200") Integer count) {
        // TODO: KisApiService에서 종목 일/주/월봉 조회 API 연동 시 여기서 호출 후 반환
        return ResponseEntity.ok(List.of());
    }
}
