package com.uniport.controller;

import com.uniport.entity.TeamAccount;
import com.uniport.entity.TeamHolding;
import com.uniport.entity.User;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import com.uniport.repository.TeamAccountRepository;
import com.uniport.repository.TeamHoldingRepository;
import com.uniport.service.AuthService;
import com.uniport.service.ChatService;
import com.uniport.service.KisApiService;
import com.uniport.service.VoteService;
import com.uniport.service.kisws.KisWsSubscriptionManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 명세 §6: 그룹 포트폴리오. §7: 채팅. §8: 투표.
 */
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private static final BigDecimal INITIAL_TEAM_BALANCE = new BigDecimal("10000000");

    private final ChatService chatService;
    private final AuthService authService;
    private final MatchingRoomMemberRepository matchingRoomMemberRepository;
    private final TeamAccountRepository teamAccountRepository;
    private final TeamHoldingRepository teamHoldingRepository;
    private final MatchingRoomRepository matchingRoomRepository;
    private final KisApiService kisApiService;
    private final VoteService voteService;
    private final KisWsSubscriptionManager kisWsSubscriptionManager;

    public GroupController(ChatService chatService, AuthService authService,
                           MatchingRoomMemberRepository matchingRoomMemberRepository,
                           TeamAccountRepository teamAccountRepository,
                           TeamHoldingRepository teamHoldingRepository,
                           MatchingRoomRepository matchingRoomRepository,
                           KisApiService kisApiService,
                           VoteService voteService,
                           KisWsSubscriptionManager kisWsSubscriptionManager) {
        this.chatService = chatService;
        this.authService = authService;
        this.matchingRoomMemberRepository = matchingRoomMemberRepository;
        this.teamAccountRepository = teamAccountRepository;
        this.teamHoldingRepository = teamHoldingRepository;
        this.matchingRoomRepository = matchingRoomRepository;
        this.kisApiService = kisApiService;
        this.voteService = voteService;
        this.kisWsSubscriptionManager = kisWsSubscriptionManager;
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<Map<String, Object>> getGroup(@PathVariable Long groupId) {
        String groupName = matchingRoomRepository.findById(groupId)
                .map(r -> r.getName())
                .orElse("팀 " + groupId);

        Optional<TeamAccount> accountOpt = teamAccountRepository.findByTeamId(groupId);
        BigDecimal cashBalance = accountOpt.map(TeamAccount::getCashBalance).orElse(INITIAL_TEAM_BALANCE);
        BigDecimal holdingsValue = BigDecimal.ZERO;
        List<Map<String, Object>> holdingsList = new ArrayList<>();

        List<TeamHolding> holdings = teamHoldingRepository.findByTeamId(groupId);
        for (TeamHolding h : holdings) {
            try {
                kisWsSubscriptionManager.ensureSubscribed(h.getStockCode());
            } catch (Exception ignored) {
                /* WS 구독은 best-effort */
            }
            String stockName = (h.getStockName() != null && !h.getStockName().isBlank())
                    ? h.getStockName()
                    : "종목_" + h.getStockCode();
            BigDecimal currentPrice = h.getAveragePurchasePrice();
            try {
                var priceDto = kisApiService.getStockPrice(h.getStockCode());
                currentPrice = priceDto.getCurrentPrice();
                if ((stockName.startsWith("종목_") || stockName.equals("종목_" + h.getStockCode()))
                        && priceDto.getStockName() != null && !priceDto.getStockName().isBlank()) {
                    stockName = priceDto.getStockName();
                }
            } catch (Exception ignored) {
            }
            BigDecimal value = currentPrice.multiply(BigDecimal.valueOf(h.getQuantity()));
            holdingsValue = holdingsValue.add(value);
            Map<String, Object> item = new HashMap<>();
            item.put("id", h.getId());
            item.put("stockCode", h.getStockCode());
            item.put("stockName", stockName);
            item.put("quantity", h.getQuantity());
            item.put("averagePrice", h.getAveragePurchasePrice());
            item.put("currentPrice", currentPrice);
            item.put("currentValue", value);
            holdingsList.add(item);
        }

        BigDecimal totalValue = cashBalance.add(holdingsValue);
        BigDecimal profitLoss = totalValue.subtract(INITIAL_TEAM_BALANCE);
        BigDecimal profitLossPercentage = INITIAL_TEAM_BALANCE.compareTo(BigDecimal.ZERO) != 0
                ? profitLoss.divide(INITIAL_TEAM_BALANCE, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        Map<String, Object> body = new HashMap<>();
        body.put("groupId", groupId);
        body.put("groupName", groupName);
        body.put("totalValue", totalValue);
        body.put("investmentAmount", INITIAL_TEAM_BALANCE);
        body.put("profitLoss", profitLoss);
        body.put("profitLossPercentage", profitLossPercentage);
        body.put("holdings", holdingsList);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{groupId}/holdings-summary")
    public ResponseEntity<List<Map<String, Object>>> getHoldingsSummary(@PathVariable Long groupId) {
        List<TeamHolding> holdings = teamHoldingRepository.findByTeamId(groupId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (TeamHolding h : holdings) {
            String stockName = (h.getStockName() != null && !h.getStockName().isBlank())
                    ? h.getStockName()
                    : "종목_" + h.getStockCode();
            BigDecimal currentPrice = h.getAveragePurchasePrice();
            try {
                var priceDto = kisApiService.getStockPrice(h.getStockCode());
                currentPrice = priceDto.getCurrentPrice();
                if ((stockName.startsWith("종목_") || stockName.equals("종목_" + h.getStockCode()))
                        && priceDto.getStockName() != null && !priceDto.getStockName().isBlank()) {
                    stockName = priceDto.getStockName();
                }
            } catch (Exception ignored) {
            }
            result.add(Map.<String, Object>of(
                    "stockCode", h.getStockCode(),
                    "stockName", stockName,
                    "quantity", h.getQuantity(),
                    "averagePurchasePrice", h.getAveragePurchasePrice(),
                    "currentPrice", currentPrice,
                    "value", currentPrice.multiply(BigDecimal.valueOf(h.getQuantity()))
            ));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<Map<String, Object>>> getMembers(@PathVariable Long groupId) {
        return ResponseEntity.ok(List.of(
                Map.<String, Object>of("id", 1, "nickname", "멤버1")
        ));
    }

    /** §7: 채팅 메시지 목록 (해당 그룹 멤버만 조회 가능) */
    @GetMapping("/{groupId}/chat/messages")
    public ResponseEntity<?> getChatMessages(
            @PathVariable Long groupId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authService.getUserFromTokenOrNull(authorization != null ? authorization : "");
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "로그인이 필요합니다."));
        }
        if (!matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(groupId, user.getId())) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "해당 채팅방에 대한 접근 권한이 없습니다."));
        }
        return ResponseEntity.ok(Map.<String, Object>of("roomId", groupId, "messages", chatService.getMessages(groupId)));
    }

    /** §7: 채팅 메시지 전송. body: message (일반 채팅) 또는 type=trade + tradeData (투자계획 공유) */
    @PostMapping("/{groupId}/chat/messages")
    public ResponseEntity<Map<String, Object>> postChatMessage(
            @PathVariable Long groupId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authService.getUserFromTokenOrNull(authorization != null ? authorization : "");
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "로그인이 필요합니다."));
        }
        if (!matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(groupId, user.getId())) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "해당 채팅방에 대한 접근 권한이 없습니다."));
        }
        if (body != null && "trade".equals(body.get("type")) && body.containsKey("tradeData")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> tradeData = (Map<String, Object>) body.get("tradeData");
            var saved = chatService.saveTradeMessage(groupId, user.getId(), user.getNickname(), tradeData);
            return ResponseEntity.ok(Map.of("success", true, "messageId", saved.getId()));
        }
        String message = body != null && body.containsKey("message") ? String.valueOf(body.get("message")) : "";
        var saved = chatService.saveMessage(groupId, user.getId(), user.getNickname(), message);
        return ResponseEntity.ok(Map.of("success", true, "messageId", saved.getId()));
    }

    /** §8: 투표 목록 (DB 저장된 투표 반환) */
    @GetMapping("/{groupId}/votes")
    public ResponseEntity<List<Map<String, Object>>> getVotes(@PathVariable Long groupId) {
        return ResponseEntity.ok(voteService.getVotesByRoomId(groupId));
    }

    /** §8: 투표 생성 (투자계획 공유 시 호출). body: type, stockName, quantity, proposedPrice, reason */
    @PostMapping("/{groupId}/votes")
    public ResponseEntity<Map<String, Object>> createVote(
            @PathVariable Long groupId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authService.getUserFromTokenOrNull(authorization != null ? authorization : "");
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "로그인이 필요합니다."));
        }
        if (!matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(groupId, user.getId())) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "해당 채팅방에 대한 접근 권한이 없습니다."));
        }
        String type = body != null && body.containsKey("type") ? String.valueOf(body.get("type")) : "매수";
        String stockName = body != null && body.containsKey("stockName") ? String.valueOf(body.get("stockName")) : "";
        String stockCode = body != null && body.containsKey("stockCode") ? String.valueOf(body.get("stockCode")) : null;
        int quantity = 0;
        if (body != null && body.containsKey("quantity")) {
            Object q = body.get("quantity");
            if (q instanceof Number) quantity = ((Number) q).intValue();
            else if (q != null) try { quantity = Integer.parseInt(String.valueOf(q)); } catch (NumberFormatException ignored) {}
        }
        java.math.BigDecimal proposedPrice = body != null && body.containsKey("proposedPrice")
                ? new java.math.BigDecimal(String.valueOf(body.get("proposedPrice"))) : java.math.BigDecimal.ZERO;
        String reason = body != null && body.containsKey("reason") ? String.valueOf(body.get("reason")) : "";
        var vote = voteService.createVote(groupId, user, type, stockName, stockCode, quantity, proposedPrice, reason);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("voteId", vote.getId());
        result.put("message", "투표가 생성되었습니다.");
        return ResponseEntity.ok(result);
    }

    /** §8: 투표 제출 (찬성/반대/보류) */
    @PostMapping("/{groupId}/votes/{voteId}")
    public ResponseEntity<Map<String, Object>> submitVote(
            @PathVariable Long groupId,
            @PathVariable Long voteId,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authService.getUserFromTokenOrNull(authorization != null ? authorization : "");
        if (user == null || user.getId() == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "로그인이 필요합니다."));
        }
        String voteValue = (body != null && body.containsKey("vote") && body.get("vote") != null)
                ? body.get("vote") : "보류";
        return ResponseEntity.ok(voteService.submitVote(groupId, voteId, user, voteValue));
    }
}
