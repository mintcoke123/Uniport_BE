package com.uniport.controller;

import com.uniport.dto.StockDetailDTO;
import com.uniport.dto.StockSearchItemDTO;
import com.uniport.entity.User;
import com.uniport.service.AuthService;
import com.uniport.service.StockMasterSearchService;
import com.uniport.service.StockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 명세 §3-5: 종목 상세. GET /api/stocks/:id
 * 검색: GET /api/stocks/search?query=...&limit=20
 */
@RestController
@RequestMapping("/api/stocks")
public class ApiStockController {

    private final StockService stockService;
    private final AuthService authService;
    private final StockMasterSearchService stockMasterSearchService;

    public ApiStockController(StockService stockService, AuthService authService,
                              StockMasterSearchService stockMasterSearchService) {
        this.stockService = stockService;
        this.authService = authService;
        this.stockMasterSearchService = stockMasterSearchService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<StockSearchItemDTO>> search(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "limit", required = false) String limit) {
        List<StockSearchItemDTO> list = stockMasterSearchService.search(query, limit);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StockDetailDTO> getStockDetail(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = null;
        try {
            user = authService.getUserFromToken(authorization != null ? authorization : "");
        } catch (Exception ignored) {
        }
        StockDetailDTO dto = stockService.getStockDetail(id, user);
        return ResponseEntity.ok(dto);
    }
}
