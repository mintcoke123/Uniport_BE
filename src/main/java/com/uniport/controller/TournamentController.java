package com.uniport.controller;

import com.uniport.config.FirebaseAuthenticatedUser;
import com.uniport.entity.User;
import com.uniport.service.CurrentUserResolver;
import com.uniport.service.TournamentHomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/tournaments")
@Tag(name = "Tournaments", description = "Tournament home and participation APIs")
public class TournamentController {

    private final TournamentHomeService tournamentHomeService;
    private final CurrentUserResolver currentUserResolver;

    public TournamentController(TournamentHomeService tournamentHomeService,
                                CurrentUserResolver currentUserResolver) {
        this.tournamentHomeService = tournamentHomeService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/home")
    @Operation(summary = "Tournament home summary", security = @SecurityRequirement(name = "firebaseBearerAuth"))
    public ResponseEntity<Map<String, Object>> getTournamentHome(
            @AuthenticationPrincipal FirebaseAuthenticatedUser principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUserResolver.resolveRequired(principal, authorization);
        return ResponseEntity.ok(tournamentHomeService.getHome(user));
    }
}
