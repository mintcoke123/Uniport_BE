package com.uniport.controller;

import com.uniport.dto.FestivalLeaderboardItemDTO;
import com.uniport.dto.FestivalSessionCompleteRequestDTO;
import com.uniport.dto.FestivalSessionCompleteResponseDTO;
import com.uniport.dto.FestivalSessionStartRequestDTO;
import com.uniport.dto.FestivalSessionStartResponseDTO;
import com.uniport.service.FestivalTradingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/festival")
public class FestivalTradingController {

    private final FestivalTradingService festivalTradingService;

    public FestivalTradingController(FestivalTradingService festivalTradingService) {
        this.festivalTradingService = festivalTradingService;
    }

    @PostMapping("/sessions/start")
    public ResponseEntity<FestivalSessionStartResponseDTO> startSession(
            @RequestBody FestivalSessionStartRequestDTO request
    ) {
        return ResponseEntity.ok(festivalTradingService.startSession(request));
    }

    @PostMapping("/sessions/{sessionId}/complete")
    public ResponseEntity<FestivalSessionCompleteResponseDTO> completeSession(
            @PathVariable Long sessionId,
            @RequestBody FestivalSessionCompleteRequestDTO request
    ) {
        return ResponseEntity.ok(festivalTradingService.completeSession(sessionId, request));
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<FestivalLeaderboardItemDTO>> getLeaderboard() {
        return ResponseEntity.ok(festivalTradingService.getLeaderboard());
    }
}
