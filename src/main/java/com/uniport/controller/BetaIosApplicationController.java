package com.uniport.controller;

import com.uniport.dto.BetaIosApplicationRequestDTO;
import com.uniport.dto.BetaIosApplicationResponseDTO;
import com.uniport.service.BetaIosApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/beta/ios-applications")
public class BetaIosApplicationController {

    private final BetaIosApplicationService betaIosApplicationService;

    public BetaIosApplicationController(BetaIosApplicationService betaIosApplicationService) {
        this.betaIosApplicationService = betaIosApplicationService;
    }

    @PostMapping
    public ResponseEntity<BetaIosApplicationResponseDTO> submitIosApplication(
            @RequestBody BetaIosApplicationRequestDTO request
    ) {
        return ResponseEntity.ok(betaIosApplicationService.submit(request));
    }
}
