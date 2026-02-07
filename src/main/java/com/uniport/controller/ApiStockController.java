package com.uniport.controller;

import com.uniport.dto.StockDetailDTO;
import com.uniport.entity.User;
import com.uniport.service.AuthService;
import com.uniport.service.StockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 명세 §3-5: 종목 상세. GET /api/stocks/:id
 */
@RestController
@RequestMapping("/api/stocks")
public class ApiStockController {

    private final StockService stockService;
    private final AuthService authService;

    public ApiStockController(StockService stockService, AuthService authService) {
        this.stockService = stockService;
        this.authService = authService;
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
