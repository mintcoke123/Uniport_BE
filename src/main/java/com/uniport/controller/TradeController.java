package com.uniport.controller;

import com.uniport.dto.ApiResponse;
import com.uniport.dto.OrderResponseDTO;
import com.uniport.dto.PlaceOrderRequestDTO;
import com.uniport.entity.User;
import com.uniport.service.AuthService;
import com.uniport.service.TradeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/trade")
public class TradeController {

    private final TradeService tradeService;
    private final AuthService authService;

    public TradeController(TradeService tradeService, AuthService authService) {
        this.tradeService = tradeService;
        this.authService = authService;
    }

    private User getCurrentUser(String authorization) {
        return authService.getUserFromToken(authorization != null ? authorization : "");
    }

    @PostMapping("/order")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> placeOrder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PlaceOrderRequestDTO request) {
        User user = getCurrentUser(authorization);
        OrderResponseDTO result = tradeService.placeOrder(request, user);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<OrderResponseDTO>>> getOrders(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = getCurrentUser(authorization);
        List<OrderResponseDTO> list = tradeService.getOrders(user);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @DeleteMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> cancelOrder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long orderId) {
        User user = getCurrentUser(authorization);
        OrderResponseDTO result = tradeService.cancelOrder(orderId, user);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
