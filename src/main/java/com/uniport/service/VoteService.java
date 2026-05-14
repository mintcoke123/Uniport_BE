package com.uniport.service;

import com.uniport.dto.PlaceOrderRequestDTO;
import com.uniport.dto.StockPriceDTO;
import com.uniport.entity.Order;
import com.uniport.entity.OrderType;
import com.uniport.entity.OrderStatus;
import com.uniport.entity.MatchingRoomMember;
import com.uniport.entity.User;
import com.uniport.entity.Vote;
import com.uniport.entity.VoteParticipant;
import com.uniport.exception.ApiException;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.OrderRepository;
import com.uniport.repository.UserRepository;
import com.uniport.repository.VoteParticipantRepository;
import com.uniport.repository.VoteRepository;
import com.uniport.service.kisws.PriceCache;
import com.uniport.service.kisws.PriceSnapshot;
import com.uniport.websocket.GroupChatBroadcaster;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class VoteService {

    public static final String ORDER_STRATEGY_MARKET = "MARKET";
    public static final String ORDER_STRATEGY_LIMIT = "LIMIT";
    public static final String ORDER_STRATEGY_CONDITIONAL = "CONDITIONAL";
    public static final String TRIGGER_DIRECTION_ABOVE = "ABOVE";
    public static final String TRIGGER_DIRECTION_BELOW = "BELOW";
    public static final String STATUS_PASSED = "passed";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_EXECUTING = "executing";
    public static final String STATUS_EXECUTED = "executed";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_CANCELLED = "cancelled";
    private static final int EXECUTION_EXPIRY_DAYS = 60;

    private final VoteRepository voteRepository;
    private final VoteParticipantRepository voteParticipantRepository;
    private final MatchingRoomMemberRepository matchingRoomMemberRepository;
    private final OrderRepository orderRepository;
    private final TradeService tradeService;
    private final UserRepository userRepository;
    private final PriceCache priceCache;
    private final KisApiService kisApiService;
    private final ChatService chatService;
    private final GroupChatBroadcaster groupChatBroadcaster;
    private final StockVisualAssetResolver stockVisualAssetResolver;
    private final PushNotificationService pushNotificationService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public VoteService(VoteRepository voteRepository,
                       VoteParticipantRepository voteParticipantRepository,
                       MatchingRoomMemberRepository matchingRoomMemberRepository,
                       OrderRepository orderRepository,
                       TradeService tradeService,
                       UserRepository userRepository,
                       PriceCache priceCache,
                       KisApiService kisApiService,
                       ChatService chatService,
                       GroupChatBroadcaster groupChatBroadcaster,
                       StockVisualAssetResolver stockVisualAssetResolver,
                       PushNotificationService pushNotificationService) {
        this.voteRepository = voteRepository;
        this.voteParticipantRepository = voteParticipantRepository;
        this.matchingRoomMemberRepository = matchingRoomMemberRepository;
        this.orderRepository = orderRepository;
        this.tradeService = tradeService;
        this.userRepository = userRepository;
        this.priceCache = priceCache;
        this.kisApiService = kisApiService;
        this.chatService = chatService;
        this.groupChatBroadcaster = groupChatBroadcaster;
        this.stockVisualAssetResolver = stockVisualAssetResolver;
        this.pushNotificationService = pushNotificationService;
    }

    @Transactional
    public Vote createVote(Long groupId, User proposer, String type, String stockName, String stockCode,
                           int quantity, BigDecimal proposedPrice, String reason,
                           String orderStrategy, BigDecimal limitPrice, BigDecimal triggerPrice, String triggerDirection) {
        if (chatService.hasFeedbackMessage(groupId)) {
            throw new ApiException("대회 종료로 비활성화되었습니다.", HttpStatus.FORBIDDEN);
        }
        String strategy = (orderStrategy != null && !orderStrategy.isBlank()) ? orderStrategy.trim().toUpperCase() : ORDER_STRATEGY_MARKET;
        if (!ORDER_STRATEGY_MARKET.equals(strategy) && !ORDER_STRATEGY_LIMIT.equals(strategy) && !ORDER_STRATEGY_CONDITIONAL.equals(strategy)) {
            throw new ApiException("orderStrategy must be MARKET, LIMIT, or CONDITIONAL", HttpStatus.BAD_REQUEST);
        }
        if (ORDER_STRATEGY_MARKET.equals(strategy)) {
            limitPrice = null;
            triggerPrice = null;
            triggerDirection = null;
        } else if (ORDER_STRATEGY_LIMIT.equals(strategy)) {
            if (limitPrice == null || limitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ApiException("LIMIT order requires limitPrice > 0", HttpStatus.BAD_REQUEST);
            }
            triggerPrice = null;
            triggerDirection = null;
        } else {
            if (triggerPrice == null || triggerPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ApiException("CONDITIONAL order requires triggerPrice > 0", HttpStatus.BAD_REQUEST);
            }
            if (triggerDirection == null || triggerDirection.isBlank()) {
                throw new ApiException("CONDITIONAL order requires triggerDirection (ABOVE or BELOW)", HttpStatus.BAD_REQUEST);
            }
            String dir = triggerDirection.trim().toUpperCase();
            if (!TRIGGER_DIRECTION_ABOVE.equals(dir) && !TRIGGER_DIRECTION_BELOW.equals(dir)) {
                throw new ApiException("triggerDirection must be ABOVE or BELOW", HttpStatus.BAD_REQUEST);
            }
            triggerDirection = dir;
        }

        String normalizedCode = (stockCode != null && !stockCode.isBlank()) ? stockCode.trim() : "";
        List<Vote> ongoing = voteRepository.findByRoomIdAndStatusOrderByCreatedAtDesc(groupId, "ongoing");
        for (Vote v : ongoing) {
            String vCode = (v.getStockCode() != null && !v.getStockCode().isBlank()) ? v.getStockCode().trim() : "";
            if (v.getType() != null && v.getType().equals(type) && normalizedCode.equals(vCode)) {
                throw new ApiException(
                    "이미 해당 종목에 대한 " + type + " 투표가 진행 중입니다.",
                    HttpStatus.BAD_REQUEST
                );
            }
        }

        int totalMembers = (int) matchingRoomMemberRepository.countByMatchingRoomId(groupId);
        if (totalMembers <= 0) {
            totalMembers = 3;
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(24, ChronoUnit.HOURS);
        Instant executionExpiresAt = null;
        if (ORDER_STRATEGY_LIMIT.equals(strategy) || ORDER_STRATEGY_CONDITIONAL.equals(strategy)) {
            executionExpiresAt = now.plus(EXECUTION_EXPIRY_DAYS, ChronoUnit.DAYS);
        }

        Vote vote = Vote.builder()
                .roomId(groupId)
                .proposerId(proposer.getId())
                .proposerName(proposer.getNickname() != null ? proposer.getNickname() : "")
                .type(type != null ? type : "매수")
                .stockName(stockName != null ? stockName : "")
                .stockCode(stockCode != null && !stockCode.isBlank() ? stockCode : null)
                .quantity(quantity)
                .proposedPrice(proposedPrice != null ? proposedPrice : BigDecimal.ZERO)
                .reason(reason != null ? reason : "")
                .createdAt(now)
                .expiresAt(expiresAt)
                .totalMembers(totalMembers)
                .status("ongoing")
                .orderStrategy(strategy)
                .limitPrice(limitPrice)
                .triggerPrice(triggerPrice)
                .triggerDirection(triggerDirection)
                .executionExpiresAt(executionExpiresAt)
                .build();
        vote = voteRepository.save(vote);
        VoteParticipant proposerVote = VoteParticipant.builder()
                .vote(vote)
                .userId(proposer.getId())
                .userName(proposer.getNickname() != null ? proposer.getNickname() : "")
                .voteChoice("찬성")
                .build();
        voteParticipantRepository.save(proposerVote);
        broadcastVoteUpdate(groupId, vote.getId());
        sendVoteCreatedPush(groupId, vote, proposer);
        return vote;
    }

    private void sendVoteCreatedPush(Long groupId, Vote vote, User proposer) {
        if (pushNotificationService == null || groupId == null || vote == null) {
            return;
        }
        Long proposerId = proposer != null ? proposer.getId() : null;
        List<Long> recipientUserIds = matchingRoomMemberRepository.findByMatchingRoomIdWithUser(groupId).stream()
                .map(MatchingRoomMember::getUser)
                .filter(user -> user != null && user.getId() != null)
                .map(User::getId)
                .filter(userId -> proposerId == null || !proposerId.equals(userId))
                .distinct()
                .toList();
        pushNotificationService.sendVoteCreated(groupId, vote, recipientUserIds);
    }

    public List<Map<String, Object>> getVotesByRoomId(Long groupId) {
        List<Vote> votes = voteRepository.findByRoomIdOrderByCreatedAtDesc(groupId);
        return votes.stream().map(this::toMap).collect(Collectors.toList());
    }

    /** 팀(방)별 거래내역: 투표(Vote) + 바로 체결(Order) 합쳐서 일시 역순. 관리자 로그용 */
    public List<Map<String, Object>> getVotesAndOrdersByRoomId(Long groupId) {
        List<Map<String, Object>> voteMaps = getVotesByRoomId(groupId);
        for (Map<String, Object> m : voteMaps) {
            String exec = (String) m.get("executedAt");
            String created = (String) m.get("createdAt");
            try {
                Instant instant = exec != null && !exec.isEmpty()
                        ? Instant.parse(exec)
                        : Instant.parse(created);
                m.put("_ts", instant.toEpochMilli());
            } catch (Exception e) {
                m.put("_ts", 0L);
            }
        }
        List<Order> orders = orderRepository.findByTeamIdOrderByOrderDateDesc(groupId);
        List<Map<String, Object>> orderMaps = orders.stream()
                .map(this::orderToLogMap)
                .collect(Collectors.toList());
        List<Map<String, Object>> merged = new ArrayList<>(voteMaps);
        merged.addAll(orderMaps);
        merged.sort((a, b) -> {
            long ta = ((Number) a.get("_ts")).longValue();
            long tb = ((Number) b.get("_ts")).longValue();
            return Long.compare(tb, ta);
        });
        merged.forEach(m -> m.remove("_ts"));
        return merged;
    }

    private Map<String, Object> orderToLogMap(Order o) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "order-" + o.getId());
        map.put("type", o.getOrderType() == OrderType.BUY ? "매수" : "매도");
        map.put("stockName", null);
        map.put("stockCode", o.getStockCode() != null ? o.getStockCode() : "");
        map.put("market", "KRX");
        map.put("logoUrl", null);
        map.put("visual", stockVisualAssetResolver.resolve("KRX", o.getStockCode(), null, null));
        map.put("quantity", o.getQuantity());
        map.put("proposedPrice", o.getPrice());
        map.put("executionPrice", o.getPrice());
        String dateStr = o.getOrderDate() != null
                ? o.getOrderDate().atZone(ZoneId.systemDefault()).toInstant().toString()
                : Instant.now().toString();
        map.put("createdAt", dateStr);
        map.put("executedAt", dateStr);
        map.put("status", o.getStatus() == OrderStatus.COMPLETED ? "executed"
                : o.getStatus() == OrderStatus.PENDING ? "pending"
                : "cancelled");
        map.put("_ts", o.getOrderDate() != null
                ? o.getOrderDate().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                : 0L);
        return map;
    }

    private Map<String, Object> toMap(Vote v) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", v.getId());
        map.put("type", v.getType());
        map.put("stockName", v.getStockName());
        map.put("stockCode", v.getStockCode() != null ? v.getStockCode() : "");
        map.put("market", "KRX");
        map.put("logoUrl", null);
        map.put("visual", stockVisualAssetResolver.resolve("KRX", v.getStockCode(), v.getStockName(), null));
        map.put("proposerId", v.getProposerId());
        map.put("proposerName", v.getProposerName());
        map.put("quantity", v.getQuantity());
        map.put("proposedPrice", v.getProposedPrice());
        if (v.getExecutionPrice() != null) {
            map.put("executionPrice", v.getExecutionPrice());
        } else if (v.getStockCode() != null && !v.getStockCode().isBlank()) {
            LocalDateTime voteCreated = LocalDateTime.ofInstant(v.getCreatedAt(), ZoneId.systemDefault());
            orderRepository.findByTeamIdAndStockCodeOrderByOrderDateDesc(v.getRoomId(), v.getStockCode())
                    .stream()
                    .filter(o -> o.getOrderDate() != null && !o.getOrderDate().isBefore(voteCreated))
                    .findFirst()
                    .ifPresent(o -> map.put("executionPrice", o.getPrice()));
        }
        map.put("reason", v.getReason());
        map.put("createdAt", v.getCreatedAt().toString());
        map.put("expiresAt", v.getExpiresAt().toString());
        map.put("totalMembers", v.getTotalMembers());
        map.put("status", v.getStatus());
        map.put("statusLabel", toStatusLabel(v.getStatus()));
        map.put("title", v.getStockName() + " " + v.getType() + " 제안");
        map.put("description", switch (v.getStatus()) {
            case STATUS_PENDING -> "조건이 충족되면 자동으로 주문이 실행됩니다.";
            case STATUS_CANCELLED -> "대기 중이던 제안이 취소되었어요.";
            case STATUS_PASSED -> "투표가 통과되어 주문 대기 상태입니다.";
            case STATUS_EXECUTING -> "주문이 실행되는 중입니다.";
            case "rejected" -> "팀원 투표 결과 반대로 종료되었어요.";
            default -> "팀원 투표를 통해 거래 여부를 결정합니다.";
        });
        map.put("orderStrategy", v.getOrderStrategy() != null ? v.getOrderStrategy() : ORDER_STRATEGY_MARKET);
        map.put("limitPrice", v.getLimitPrice());
        map.put("triggerPrice", v.getTriggerPrice());
        map.put("triggerDirection", v.getTriggerDirection());
        map.put("executionExpiresAt", v.getExecutionExpiresAt() != null ? v.getExecutionExpiresAt().toString() : null);
        map.put("executedAt", v.getExecutedAt() != null ? v.getExecutedAt().toString() : null);

        List<Map<String, Object>> participants = voteParticipantRepository.findByVote_IdOrderById(v.getId())
                .stream()
                .map(p -> Map.<String, Object>of(
                        "orderId", p.getId(),
                        "userId", p.getUserId(),
                        "userName", p.getUserName(),
                        "vote", p.getVoteChoice()))
                .collect(Collectors.toList());
        map.put("votes", participants);
        return map;
    }

    @Transactional
    public Map<String, Object> submitVote(Long groupId, Long voteId, User user, String voteValue) {
        if (user == null || user.getId() == null) {
            throw new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED);
        }
        if (chatService.hasFeedbackMessage(groupId)) {
            throw new ApiException("대회 종료로 비활성화되었습니다.", HttpStatus.FORBIDDEN);
        }
        Vote vote = voteRepository.findById(voteId)
                .orElseThrow(() -> new ApiException("투표를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        if (vote.getRoomId() == null || !vote.getRoomId().equals(groupId)) {
            throw new ApiException("해당 그룹의 투표가 아닙니다.", HttpStatus.BAD_REQUEST);
        }
        if (!"ongoing".equals(vote.getStatus())) {
            throw new ApiException("이미 종료된 투표입니다.", HttpStatus.BAD_REQUEST);
        }
        if (!matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(groupId, user.getId())) {
            throw new ApiException("해당 채팅방 멤버만 투표할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        String v = (voteValue != null && ("찬성".equals(voteValue) || "반대".equals(voteValue))) ? voteValue : "보류";

        VoteParticipant participant = voteParticipantRepository.findByVote_IdAndUserId(voteId, user.getId())
                .orElse(null);
        if (participant != null) {
            participant.setVoteChoice(v);
            voteParticipantRepository.save(participant);
        } else {
            VoteParticipant newP = VoteParticipant.builder()
                    .vote(vote)
                    .userId(user.getId())
                    .userName(user.getNickname() != null ? user.getNickname() : "")
                    .voteChoice(v)
                    .build();
            voteParticipantRepository.save(newP);
        }

        List<VoteParticipant> all = voteParticipantRepository.findByVote_IdOrderById(voteId);
        long agree = all.stream().filter(p -> "찬성".equals(p.getVoteChoice())).count();
        long disagree = all.stream().filter(p -> "반대".equals(p.getVoteChoice())).count();
        int totalMembers = vote.getTotalMembers();
        int majority = totalMembers > 0 ? (totalMembers / 2) + 1 : 0;
        boolean passed = isVotePassedByRatio(agree, totalMembers);
        if (passed) {
            vote.setStatus(STATUS_PASSED);
            voteRepository.save(vote);
            String strategy = vote.getOrderStrategy() != null ? vote.getOrderStrategy() : ORDER_STRATEGY_MARKET;
            BigDecimal currentPrice = resolveCurrentPrice(vote.getStockCode(), fallbackPrice(vote));
            if (ORDER_STRATEGY_MARKET.equals(strategy)) {
                if (vote.getStockCode() != null && !vote.getStockCode().isBlank()) {
                    Optional<Vote> lockedOpt = voteRepository.findByIdForUpdate(voteId);
                    if (lockedOpt.isPresent() && STATUS_PASSED.equals(lockedOpt.get().getStatus())) {
                        Vote locked = lockedOpt.get();
                        locked.setStatus(STATUS_EXECUTING);
                        voteRepository.save(locked);
                        try {
                            executeVoteOrderWithPrice(locked, currentPrice);
                        } catch (Exception e) {
                            locked.setStatus(STATUS_PASSED);
                            voteRepository.save(locked);
                            throw e;
                        }
                    }
                }
            } else {
                if (vote.getStockCode() != null && !vote.getStockCode().isBlank() && shouldExecute(vote, currentPrice)) {
                    Optional<Vote> lockedOpt = voteRepository.findByIdForUpdate(voteId);
                    if (lockedOpt.isPresent() && STATUS_PASSED.equals(lockedOpt.get().getStatus())) {
                        Vote locked = lockedOpt.get();
                        locked.setStatus(STATUS_EXECUTING);
                        voteRepository.save(locked);
                        try {
                            executeVoteOrderWithPrice(locked, currentPrice);
                        } catch (Exception e) {
                            locked.setStatus(STATUS_PASSED);
                            voteRepository.save(locked);
                            throw e;
                        }
                    }
                } else {
                    vote.setStatus(STATUS_PENDING);
                    voteRepository.save(vote);
                }
            }
        } else if (disagree >= majority) {
            vote.setStatus("rejected");
            voteRepository.save(vote);
        } else {
            voteRepository.save(vote);
        }

        Vote updated = voteRepository.findById(voteId).orElse(vote);
        broadcastVoteUpdate(groupId, voteId);
        sendVoteClosedPushIfNeeded(groupId, updated);
        Map<String, Object> voteSummary = new HashMap<>();
        voteSummary.put("id", voteId);
        voteSummary.put("vote", v);
        voteSummary.put("status", updated.getStatus() != null ? updated.getStatus() : vote.getStatus());
        voteSummary.put("statusLabel", toStatusLabel(updated.getStatus() != null ? updated.getStatus() : vote.getStatus()));

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "투표가 반영되었습니다.");
        response.put("title", "의견이 반영되었어요");
        response.put("description", "팀원들의 투표 현황을 계속 확인해보세요.");
        response.put("vote", voteSummary);
        return response;
    }

    /** pending 상태 조건주문 취소. 제안자만 취소 가능. */
    @Transactional
    public Map<String, Object> cancelPendingVote(Long groupId, Long voteId, User user) {
        if (user == null || user.getId() == null) {
            throw new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED);
        }
        if (chatService.hasFeedbackMessage(groupId)) {
            throw new ApiException("대회 종료로 비활성화되었습니다.", HttpStatus.FORBIDDEN);
        }
        Vote vote = voteRepository.findById(voteId)
                .orElseThrow(() -> new ApiException("투표를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        if (vote.getRoomId() == null || !vote.getRoomId().equals(groupId)) {
            throw new ApiException("해당 그룹의 투표가 아닙니다.", HttpStatus.BAD_REQUEST);
        }
        if (!STATUS_PENDING.equals(vote.getStatus())) {
            throw new ApiException("대기 중인 투표만 취소할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
        if (!vote.getProposerId().equals(user.getId())) {
            throw new ApiException("제안자만 대기 취소할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
        vote.setStatus(STATUS_CANCELLED);
        voteRepository.save(vote);
        broadcastVoteUpdate(groupId, voteId);
        sendVoteClosedPush(groupId, vote);
        Map<String, Object> voteSummary = new HashMap<>();
        voteSummary.put("id", voteId);
        voteSummary.put("status", STATUS_CANCELLED);
        voteSummary.put("statusLabel", toStatusLabel(STATUS_CANCELLED));

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "대기가 취소되었습니다.");
        response.put("title", "대기 주문이 취소되었어요");
        response.put("description", "새로운 조건으로 다시 투표를 시작할 수 있습니다.");
        response.put("vote", voteSummary);
        return response;
    }

    /**
     * 투표 통과 여부: (agreeCount / totalMembers) > 0.5.
     * 동점(0.5)은 미통과. totalMembers가 0이면 미통과.
     */
    static boolean isVotePassedByRatio(long agreeCount, int totalMembers) {
        if (totalMembers <= 0) {
            return false;
        }
        return ((double) agreeCount / totalMembers) > 0.5;
    }

    private String toStatusLabel(String status) {
        return switch (status) {
            case STATUS_PENDING -> "대기중";
            case STATUS_PASSED -> "통과";
            case STATUS_EXECUTING -> "주문 실행중";
            case STATUS_CANCELLED -> "취소됨";
            case "rejected" -> "반려됨";
            default -> "진행중";
        };
    }

    /** 찬성/반대·취소 등 투표 갱신 시 같은 방 WebSocket 클라이언트에 실시간 알림 (프론트에서 투표 목록 재조회) */
    private void broadcastVoteUpdate(Long groupId, Long voteId) {
        if (groupId == null || voteId == null || groupChatBroadcaster == null) return;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "vote_update");
            payload.put("groupId", groupId);
            payload.put("voteId", voteId);
            groupChatBroadcaster.broadcast(String.valueOf(groupId), OBJECT_MAPPER.writeValueAsString(payload));
        } catch (Exception ignored) {
        }
    }

    /** 종목코드 6자리 정규화 (캐시 키 등에 사용) */
    private static String normalizeStockCode(String code) {
        if (code == null || code.isBlank()) return "";
        String t = code.trim();
        return t.length() >= 6 ? t : String.format("%6s", t).replace(' ', '0');
    }

    /**
     * 체결가용 현재가 조회.
     * getStockPrice(KIS API + 60초 HTTP 캐시)를 우선 사용해, 장 마감/모의 환경에서 WS 틱에 따라
     * 체결가가 요청마다 바뀌는 현상을 막음. 실패 시에만 PriceCache → fallback.
     */
    public BigDecimal resolveCurrentPrice(String stockCode, BigDecimal fallbackProposedPrice) {
        String code = normalizeStockCode(stockCode);
        if (code.isEmpty()) return fallbackOrDefault(fallbackProposedPrice);
        try {
            StockPriceDTO dto = kisApiService.getStockPrice(stockCode);
            if (dto != null && dto.getCurrentPrice() != null && dto.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {
                return dto.getCurrentPrice();
            }
        } catch (Exception ignored) {
        }
        BigDecimal fromCache = priceCache.get(code)
                .map(PriceSnapshot::getCurrentPrice)
                .filter(p -> p != null && p.compareTo(BigDecimal.ZERO) > 0)
                .orElse(null);
        if (fromCache != null) return fromCache;
        return fallbackOrDefault(fallbackProposedPrice);
    }

    private static BigDecimal fallbackOrDefault(BigDecimal fallback) {
        return fallback != null && fallback.compareTo(BigDecimal.ZERO) > 0 ? fallback : BigDecimal.ONE;
    }

    /** 조건 만족 시에만 체결. MARKET 항상 true; LIMIT: 매수 currentPrice<=limitPrice, 매도 currentPrice>=limitPrice; CONDITIONAL: ABOVE currentPrice>=triggerPrice, BELOW currentPrice<=triggerPrice */
    public boolean shouldExecute(Vote vote, BigDecimal currentPrice) {
        if (vote == null || currentPrice == null) return false;
        String strategy = vote.getOrderStrategy() != null ? vote.getOrderStrategy() : ORDER_STRATEGY_MARKET;
        if (ORDER_STRATEGY_MARKET.equals(strategy)) return true;
        if (ORDER_STRATEGY_LIMIT.equals(strategy)) {
            BigDecimal limit = vote.getLimitPrice();
            if (limit == null) return false;
            boolean isBuy = "매수".equals(vote.getType());
            return isBuy ? currentPrice.compareTo(limit) <= 0 : currentPrice.compareTo(limit) >= 0;
        }
        if (ORDER_STRATEGY_CONDITIONAL.equals(strategy)) {
            BigDecimal trigger = vote.getTriggerPrice();
            String dir = vote.getTriggerDirection();
            if (trigger == null || dir == null) return false;
            if (TRIGGER_DIRECTION_ABOVE.equals(dir)) return currentPrice.compareTo(trigger) >= 0;
            if (TRIGGER_DIRECTION_BELOW.equals(dir)) return currentPrice.compareTo(trigger) <= 0;
            return false;
        }
        return false;
    }

    /** 체결 실행: 주문 후 vote.status=executed, executedAt 저장. 호출 전에 status=executing 저장해 두는 것은 호출자 책임. */
    private void executeVoteOrderWithPrice(Vote vote, BigDecimal currentPrice) {
        if (vote.getStockCode() == null || vote.getStockCode().isBlank() || vote.getProposerId() == null || vote.getRoomId() == null) {
            return;
        }
        OrderType orderType = "매도".equals(vote.getType()) ? OrderType.SELL : OrderType.BUY;
        String name = (vote.getStockName() != null && !vote.getStockName().isBlank()) ? vote.getStockName() : null;
        PlaceOrderRequestDTO request = PlaceOrderRequestDTO.builder()
                .stockCode(vote.getStockCode())
                .stockName(name)
                .quantity(vote.getQuantity())
                .price(currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0 ? currentPrice : BigDecimal.ONE)
                .orderType(orderType)
                .build();
        User proposer = userRepository.findById(vote.getProposerId()).orElse(null);
        tradeService.placeOrderForTeam(request, vote.getRoomId(), proposer);
        BigDecimal execPrice = currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0 ? currentPrice : BigDecimal.ONE;
        vote.setStatus(STATUS_EXECUTED);
        vote.setExecutedAt(Instant.now());
        vote.setExecutionPrice(execPrice);
        voteRepository.save(vote);
        String nickname = proposer != null && proposer.getNickname() != null ? proposer.getNickname() : "팀";
        try {
            chatService.saveExecutionMessage(vote.getRoomId(), vote.getProposerId(), nickname, vote.getType(), vote.getStockName(), vote.getQuantity(), execPrice);
        } catch (Exception ignored) { /* 채팅 저장 실패해도 체결은 완료 */ }
        sendTradeExecutedPush(vote.getRoomId(), vote);
    }

    private static BigDecimal fallbackPrice(Vote vote) {
        return vote.getProposedPrice() != null && vote.getProposedPrice().compareTo(BigDecimal.ZERO) > 0
                ? vote.getProposedPrice() : BigDecimal.ONE;
    }

    /** 표결 만료: ongoing 중 expiresAt <= now → status=expired */
    @Transactional
    public void processExpiredOngoingVotes() {
        Instant now = Instant.now();
        List<Vote> ongoing = voteRepository.findByStatus("ongoing");
        for (Vote v : ongoing) {
            if (v.getExpiresAt() != null && !v.getExpiresAt().isAfter(now)) {
                v.setStatus(STATUS_EXPIRED);
                voteRepository.save(v);
                sendVoteClosedPush(v.getRoomId(), v);
            }
        }
    }

    /** pending 스캔: executionExpiresAt 만료 → expired; 조건 만족 시 executing → execute → executed */
    @Transactional
    public void processPendingVotes() {
        Instant now = Instant.now();
        List<Vote> pending = voteRepository.findByStatus(STATUS_PENDING);
        for (Vote v : pending) {
            if (v.getStockCode() == null || v.getStockCode().isBlank()) continue;
            if (v.getExecutionExpiresAt() != null && !v.getExecutionExpiresAt().isAfter(now)) {
                v.setStatus(STATUS_EXPIRED);
                voteRepository.save(v);
                sendVoteClosedPush(v.getRoomId(), v);
                continue;
            }
            BigDecimal currentPrice = resolveCurrentPrice(v.getStockCode(), v.getProposedPrice());
            if (!shouldExecute(v, currentPrice)) continue;
            Optional<Vote> lockedOpt = voteRepository.findByIdForUpdate(v.getId());
            if (lockedOpt.isEmpty()) continue;
            Vote locked = lockedOpt.get();
            if (!STATUS_PENDING.equals(locked.getStatus())) continue;
            locked.setStatus(STATUS_EXECUTING);
            voteRepository.save(locked);
            try {
                executeVoteOrderWithPrice(locked, currentPrice);
            } catch (Exception e) {
                locked.setStatus(STATUS_PENDING);
                voteRepository.save(locked);
                throw e;
            }
        }
    }

    private void sendVoteClosedPushIfNeeded(Long groupId, Vote vote) {
        if (vote == null || vote.getStatus() == null) {
            return;
        }
        if ("ongoing".equals(vote.getStatus()) || STATUS_EXECUTED.equals(vote.getStatus()) || STATUS_EXECUTING.equals(vote.getStatus())) {
            return;
        }
        sendVoteClosedPush(groupId, vote);
    }

    private void sendVoteClosedPush(Long groupId, Vote vote) {
        if (pushNotificationService == null || groupId == null || vote == null) {
            return;
        }
        pushNotificationService.sendVoteClosed(groupId, vote, roomMemberUserIds(groupId));
    }

    private void sendTradeExecutedPush(Long groupId, Vote vote) {
        if (pushNotificationService == null || groupId == null || vote == null) {
            return;
        }
        pushNotificationService.sendTradeExecuted(groupId, vote, roomMemberUserIds(groupId));
    }

    private List<Long> roomMemberUserIds(Long groupId) {
        if (groupId == null) {
            return List.of();
        }
        return matchingRoomMemberRepository.findByMatchingRoomIdWithUser(groupId).stream()
                .map(MatchingRoomMember::getUser)
                .filter(user -> user != null && user.getId() != null)
                .map(User::getId)
                .distinct()
                .toList();
    }

}
