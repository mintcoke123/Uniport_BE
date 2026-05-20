package com.uniport.controller;

import com.uniport.dto.InvestmentTestReservationRequestDTO;
import com.uniport.dto.InvestmentTestReservationResponseDTO;
import com.uniport.service.InvestmentTestReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/investment-test/reservations")
public class InvestmentTestReservationController {

    private final InvestmentTestReservationService reservationService;

    public InvestmentTestReservationController(InvestmentTestReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<InvestmentTestReservationResponseDTO> submitReservation(
            @RequestBody InvestmentTestReservationRequestDTO request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        return ResponseEntity.ok(reservationService.submit(request, userAgent));
    }
}
