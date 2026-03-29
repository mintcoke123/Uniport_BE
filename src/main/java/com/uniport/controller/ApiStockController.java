package com.uniport.controller;

import com.uniport.dto.StockDetailDTO;
import com.uniport.dto.StockSearchResponseDTO;
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
    public ResponseEntity<StockSearchResponseDTO> search(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "limit", required = false) String limit) {
        StockSearchResponseDTO response = stockMasterSearchService.search(keyword, page, size, query, limit);
        return ResponseEntity.ok(response);
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
