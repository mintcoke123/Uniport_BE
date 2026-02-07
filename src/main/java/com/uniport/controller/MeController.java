package com.uniport.controller;

import com.uniport.dto.AuthUserDTO;
import com.uniport.dto.MyInvestmentResponseDTO;
import com.uniport.entity.User;
import com.uniport.service.AuthService;
import com.uniport.service.MeService;
import com.uniport.service.MatchingRoomService;
import com.uniport.service.RankingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 명세 §2-1: 내 투자 요약 (자산 + 보유 종목 + 대회 요약).
 * §6-4: 현재 사용자 기준 진행 중 대회의 경쟁 팀 목록.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final MeService meService;
    private final AuthService authService;
    private final MatchingRoomService matchingRoomService;
    private final RankingService rankingService;

    public MeController(MeService meService, AuthService authService, MatchingRoomService matchingRoomService, RankingService rankingService) {
        this.meService = meService;
        this.authService = authService;
        this.matchingRoomService = matchingRoomService;
        this.rankingService = rankingService;
    }

    /** 현재 로그인 사용자 프로필 (teamId 등 DB 최신값). 미로그인 시 200 + 빈 객체. */
    @GetMapping
    public ResponseEntity<AuthUserDTO> getMe(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        AuthUserDTO dto = authService.getCurrentUserDto(authorization);
        return ResponseEntity.ok(dto != null ? dto : new AuthUserDTO());
    }

    @GetMapping("/investment")
    public ResponseEntity<MyInvestmentResponseDTO> getInvestment(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authService.getUserFromTokenOrNull(authorization != null ? authorization : "");
        MyInvestmentResponseDTO dto = meService.getMyInvestment(user);
        return ResponseEntity.ok(dto);
    }

    /** 진행 중 대회의 경쟁 팀 목록 (실시간 투자금·수익률·순위). 미로그인 시 빈 배열. DB 팀 랭킹 기준. */
    @GetMapping("/competition/competing-teams")
    public ResponseEntity<List<Map<String, Object>>> getCompetingTeams(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authService.getUserFromTokenOrNull(authorization != null ? authorization : "");
        return ResponseEntity.ok(rankingService.getCompetingTeams(null, user));
    }

    /** 내가 참가 중인 매칭방 목록 (최신 참가 순). 미로그인 시 빈 배열. */
    @GetMapping("/matching-rooms")
    public ResponseEntity<List<Map<String, Object>>> getMyMatchingRooms(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authService.getUserFromTokenOrNull(authorization != null ? authorization : "");
        if (user == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(matchingRoomService.listRoomsJoinedBy(user));
    }
}
