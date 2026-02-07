package com.uniport.controller;

import com.uniport.dto.ApiResponse;
import com.uniport.dto.StockPriceDTO;
import com.uniport.service.StockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/stock")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/price")
    public ResponseEntity<ApiResponse<StockPriceDTO>> getPrice(@RequestParam("code") String stockCode) {
        StockPriceDTO dto = stockService.getStockPrice(stockCode);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<StockPriceDTO>>> search(@RequestParam(value = "query", required = false) String keyword) {
        List<StockPriceDTO> list = stockService.searchStocks(keyword != null ? keyword : "");
        return ResponseEntity.ok(ApiResponse.ok(list));
    }
}
