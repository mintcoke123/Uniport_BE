package com.uniport.controller;

import com.uniport.entity.User;
import com.uniport.service.AuthService;
import com.uniport.service.RankingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 명세 §5: 랭킹. 전체 그룹 랭킹, 내 그룹 랭킹.
 */
@RestController
@RequestMapping("/api/ranking")
public class RankingController {

    private final RankingService rankingService;
    private final AuthService authService;

    public RankingController(RankingService rankingService, AuthService authService) {
        this.rankingService = rankingService;
        this.authService = authService;
    }

    @GetMapping("/groups")
    public ResponseEntity<List<Map<String, Object>>> getGroups() {
        return ResponseEntity.ok(rankingService.getAllGroupsRanking());
    }

    @GetMapping("/my-group")
    public ResponseEntity<Map<String, Object>> getMyGroup(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authService.getUserFromTokenOrNull(authorization != null ? authorization : "");
        Map<String, Object> my = user != null ? rankingService.getMyGroupRanking(user) : null;
        return my != null ? ResponseEntity.ok(my) : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
