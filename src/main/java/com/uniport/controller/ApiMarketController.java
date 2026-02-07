package com.uniport.controller;

import com.uniport.dto.MarketIndexItemDTO;
import com.uniport.dto.MarketStockItemDTO;
import com.uniport.service.MarketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 명세 §3-1~3-4: 시장 지수, 거래량/상승/하락 순 종목.
 */
@RestController
@RequestMapping("/api/market")
public class ApiMarketController {

    private final MarketService marketService;

    public ApiMarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    @GetMapping("/indices")
    public ResponseEntity<List<MarketIndexItemDTO>> getIndices() {
        List<MarketIndexItemDTO> list = marketService.getIndicesForApi();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/stocks")
    public ResponseEntity<List<MarketStockItemDTO>> getStocks(
            @RequestParam(value = "sort", required = false, defaultValue = "volume") String sort) {
        List<MarketStockItemDTO> list = marketService.getStocksForApi(sort);
        return ResponseEntity.ok(list);
    }
}
