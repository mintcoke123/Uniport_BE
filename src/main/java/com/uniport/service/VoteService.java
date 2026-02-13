package com.uniport.service;

import com.uniport.dto.PlaceOrderRequestDTO;
import com.uniport.entity.OrderType;
import com.uniport.entity.User;
import com.uniport.entity.Vote;
import com.uniport.entity.VoteParticipant;
import com.uniport.exception.ApiException;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.OrderRepository;
import com.uniport.repository.UserRepository;
import com.uniport.repository.VoteParticipantRepository;
import com.uniport.repository.VoteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VoteService {

    private final VoteRepository voteRepository;
    private final VoteParticipantRepository voteParticipantRepository;
    private final MatchingRoomMemberRepository matchingRoomMemberRepository;
    private final OrderRepository orderRepository;
    private final TradeService tradeService;
    private final UserRepository userRepository;

    public VoteService(VoteRepository voteRepository,
                       VoteParticipantRepository voteParticipantRepository,
                       MatchingRoomMemberRepository matchingRoomMemberRepository,
                       OrderRepository orderRepository,
                       TradeService tradeService,
                       UserRepository userRepository) {
        this.voteRepository = voteRepository;
        this.voteParticipantRepository = voteParticipantRepository;
        this.matchingRoomMemberRepository = matchingRoomMemberRepository;
        this.orderRepository = orderRepository;
        this.tradeService = tradeService;
        this.userRepository = userRepository;
    }

    @Transactional
    public Vote createVote(Long groupId, User proposer, String type, String stockName, String stockCode,
                           int quantity, BigDecimal proposedPrice, String reason) {
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
                .build();
        vote = voteRepository.save(vote);
        VoteParticipant proposerVote = VoteParticipant.builder()
                .vote(vote)
                .userId(proposer.getId())
                .userName(proposer.getNickname() != null ? proposer.getNickname() : "")
                .voteChoice("찬성")
                .build();
        voteParticipantRepository.save(proposerVote);
        return vote;
    }

    public List<Map<String, Object>> getVotesByRoomId(Long groupId) {
        List<Vote> votes = voteRepository.findByRoomIdOrderByCreatedAtDesc(groupId);
        return votes.stream().map(this::toMap).collect(Collectors.toList());
    }

    private Map<String, Object> toMap(Vote v) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", v.getId());
        map.put("type", v.getType());
        map.put("stockName", v.getStockName());
        map.put("stockCode", v.getStockCode() != null ? v.getStockCode() : "");
        map.put("proposerId", v.getProposerId());
        map.put("proposerName", v.getProposerName());
        map.put("quantity", v.getQuantity());
        map.put("proposedPrice", v.getProposedPrice());
        if (v.getStockCode() != null && !v.getStockCode().isBlank()) {
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
        int totalMembers = Math.max(1, vote.getTotalMembers());
        int majority = (totalMembers / 2) + 1;
        boolean passed = agree >= 2 || (totalMembers == 1 && agree >= 1);
        if (passed) {
            vote.setStatus("passed");
            voteRepository.save(vote);
            executeVoteOrder(vote);
        } else if (all.size() >= totalMembers && disagree >= majority) {
            vote.setStatus("rejected");
            voteRepository.save(vote);
        } else {
            voteRepository.save(vote);
        }

        return Map.of(
                "success", true,
                "message", "투표가 반영되었습니다.",
                "vote", Map.<String, Object>of("id", voteId, "vote", v)
        );
    }

    private void executeVoteOrder(Vote vote) {
        if (vote.getStockCode() == null || vote.getStockCode().isBlank()) {
            return;
        }
        if (vote.getProposerId() == null || vote.getRoomId() == null) {
            return;
        }
        BigDecimal price = vote.getProposedPrice() != null && vote.getProposedPrice().compareTo(BigDecimal.ZERO) > 0
                ? vote.getProposedPrice() : BigDecimal.ONE;
        OrderType orderType = "매도".equals(vote.getType()) ? OrderType.SELL : OrderType.BUY;
        String name = (vote.getStockName() != null && !vote.getStockName().isBlank()) ? vote.getStockName() : null;
        PlaceOrderRequestDTO request = PlaceOrderRequestDTO.builder()
                .stockCode(vote.getStockCode())
                .stockName(name)
                .quantity(vote.getQuantity())
                .price(price)
                .orderType(orderType)
                .build();
        User proposer = userRepository.findById(vote.getProposerId()).orElse(null);
        try {
            tradeService.placeOrderForTeam(request, vote.getRoomId(), proposer);
        } catch (Exception e) {
            // 로그만 남기고 투표 상태는 이미 passed로 저장됨
        }
    }

}
