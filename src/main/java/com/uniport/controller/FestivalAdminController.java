package com.uniport.controller;

import com.uniport.dto.FestivalAdminOverviewDTO;
import com.uniport.service.FestivalTradingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/festival-admin")
public class FestivalAdminController {

    private final FestivalTradingService festivalTradingService;

    public FestivalAdminController(FestivalTradingService festivalTradingService) {
        this.festivalTradingService = festivalTradingService;
    }

    @GetMapping("/overview")
    public ResponseEntity<FestivalAdminOverviewDTO> getOverview() {
        return ResponseEntity.ok(festivalTradingService.getAdminOverview());
    }
}
