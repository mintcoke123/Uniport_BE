package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.dto.AuthUserDTO;
import com.uniport.dto.MyInvestmentResponseDTO;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.service.CurrentUserDeletionService;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.MeService;
import com.uniport.service.MatchingRoomService;
import com.uniport.service.RankingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final MeService meService;
    private final CurrentUserResolver currentUserResolver;
    private final MatchingRoomService matchingRoomService;
    private final RankingService rankingService;
    private final CurrentUserDeletionService currentUserDeletionService;

    public MeController(MeService meService,
                        CurrentUserResolver currentUserResolver,
                        MatchingRoomService matchingRoomService,
                        RankingService rankingService,
                        CurrentUserDeletionService currentUserDeletionService) {
        this.meService = meService;
        this.currentUserResolver = currentUserResolver;
        this.matchingRoomService = matchingRoomService;
        this.rankingService = rankingService;
        this.currentUserDeletionService = currentUserDeletionService;
    }

    @GetMapping
    public ResponseEntity<AuthUserDTO> getMe(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveNullable(principal, authorization);
        return ResponseEntity.ok(meService.getProfile(user));
    }

    @GetMapping("/investment")
    public ResponseEntity<MyInvestmentResponseDTO> getInvestment(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveNullable(principal, authorization);
        return ResponseEntity.ok(meService.getMyInvestment(user));
    }

    @GetMapping("/competition/competing-teams")
    public ResponseEntity<List<Map<String, Object>>> getCompetingTeams(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveNullable(principal, authorization);
        return ResponseEntity.ok(rankingService.getCompetingTeams(null, user));
    }

    @GetMapping("/matching-rooms")
    public ResponseEntity<List<Map<String, Object>>> getMyMatchingRooms(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveNullable(principal, authorization);
        if (user == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(matchingRoomService.listRoomsJoinedBy(user));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteMe(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveNullable(principal, authorization);
        if (user == null) {
            throw new ApiException("Authentication is required", HttpStatus.UNAUTHORIZED);
        }
        currentUserDeletionService.deleteCurrentUser(user);
        return ResponseEntity.ok(Map.of("success", true, "message", "Deleted"));
    }
}
