package com.uniport.controller;

import com.uniport.dto.TradeRequestDTO;
import com.uniport.dto.TradeResponseDTO;
import com.uniport.entity.User;
import com.uniport.service.AuthService;
import com.uniport.service.TradeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 명세 §3-6: 주식 매수/매도 체결. POST /api/trades
 */
@RestController
@RequestMapping("/api/trades")
public class ApiTradeController {

    private final TradeService tradeService;
    private final AuthService authService;

    public ApiTradeController(TradeService tradeService, AuthService authService) {
        this.tradeService = tradeService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<TradeResponseDTO> placeTrade(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody TradeRequestDTO request) {
        User user = authService.getUserFromToken(authorization != null ? authorization : "");
        TradeResponseDTO result = tradeService.placeOrderFromSpec(request, user);
        return ResponseEntity.ok(result);
    }
}
