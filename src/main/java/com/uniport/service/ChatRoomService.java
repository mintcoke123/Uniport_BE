package com.uniport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.entity.ChatMessage;
import com.uniport.entity.MatchingRoom;
import com.uniport.entity.MatchingRoomMember;
import com.uniport.entity.TeamAccount;
import com.uniport.entity.TeamHolding;
import com.uniport.entity.User;
import com.uniport.entity.UserMyPagePreference;
import com.uniport.entity.Vote;
import com.uniport.exception.ApiException;
import com.uniport.repository.ChatMessageRepository;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import com.uniport.repository.TeamAccountRepository;
import com.uniport.repository.TeamHoldingRepository;
import com.uniport.repository.UserMyPagePreferenceRepository;
import com.uniport.repository.VoteParticipantRepository;
import com.uniport.repository.VoteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ChatRoomService {

    private static final String ROOM_STATUS_STARTED = "started";
    private static final BigDecimal INITIAL_TEAM_BALANCE = new BigDecimal("10000000");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MatchingRoomMemberRepository matchingRoomMemberRepository;
    private final MatchingRoomRepository matchingRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TeamAccountRepository teamAccountRepository;
    private final TeamHoldingRepository teamHoldingRepository;
    private final VoteRepository voteRepository;
    private final VoteParticipantRepository voteParticipantRepository;
    private final KisApiService kisApiService;
    private final UserMyPagePreferenceRepository userMyPagePreferenceRepository;
    private final ProfileImageUrlService profileImageUrlService;

    public ChatRoomService(MatchingRoomMemberRepository matchingRoomMemberRepository,
                           MatchingRoomRepository matchingRoomRepository,
                           ChatMessageRepository chatMessageRepository,
                           TeamAccountRepository teamAccountRepository,
                           TeamHoldingRepository teamHoldingRepository,
                           VoteRepository voteRepository,
                           VoteParticipantRepository voteParticipantRepository,
                           KisApiService kisApiService,
                           UserMyPagePreferenceRepository userMyPagePreferenceRepository,
                           ProfileImageUrlService profileImageUrlService) {
        this.matchingRoomMemberRepository = matchingRoomMemberRepository;
        this.matchingRoomRepository = matchingRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.teamAccountRepository = teamAccountRepository;
        this.teamHoldingRepository = teamHoldingRepository;
        this.voteRepository = voteRepository;
        this.voteParticipantRepository = voteParticipantRepository;
        this.kisApiService = kisApiService;
        this.userMyPagePreferenceRepository = userMyPagePreferenceRepository;
        this.profileImageUrlService = profileImageUrlService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMyChatRooms(User user) {
        List<MatchingRoomMember> memberships = matchingRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(user.getId());
        List<Map<String, Object>> result = new ArrayList<>();
        for (MatchingRoomMember membership : memberships) {
            MatchingRoom room = membership.getMatchingRoom();
            if (room == null || room.getId() == null) {
                continue;
            }
            if (!ROOM_STATUS_STARTED.equalsIgnoreCase(room.getStatus())) {
                continue;
            }
            Map<String, Object> roomMap = new HashMap<>();
            roomMap.put("roomId", room.getId());
            roomMap.put("groupId", room.getId());
            roomMap.put("name", room.getName());
            roomMap.put("modeLabel", deriveModeLabel(room));
            roomMap.put("marketType", room.getMarketType());
            roomMap.put("marketLabel", toMarketLabel(room.getMarketType()));
            roomMap.put("matchType", room.getMatchType());
            roomMap.put("matchLabel", toMatchLabel(room.getMatchType()));
            roomMap.put("memberCount", room.getMemberCount());
            roomMap.put("capacity", room.getCapacity());
            roomMap.put("joinedAt", membership.getJoinedAt() != null ? membership.getJoinedAt().toString() : null);
            roomMap.put("title", room.getName() + " 채팅방");
            roomMap.put("description", "팀원들과 실시간으로 투자 전략을 이야기해보세요.");
            roomMap.put("portfolioSummary", buildPortfolioSummary(room.getId()));
            roomMap.put("lastMessage", buildLastMessagePreview(room.getId()));
            roomMap.put("activeVote", buildActiveVotePreview(room.getId(), user));
            roomMap.put("unreadCount", countUnreadMessages(room.getId(), membership, user));
            result.add(roomMap);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> getChatRoomSummary(Long roomId, User user) {
        if (!matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(roomId, user.getId())) {
            throw new IllegalArgumentException("Chat room access denied");
        }

        MatchingRoom room = matchingRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Chat room not found"));

        Map<String, Object> summary = new HashMap<>();
        summary.put("roomId", room.getId());
        summary.put("groupId", room.getId());
        summary.put("name", room.getName());
        summary.put("modeLabel", deriveModeLabel(room));
        summary.put("marketType", room.getMarketType());
        summary.put("marketLabel", toMarketLabel(room.getMarketType()));
        summary.put("matchType", room.getMatchType());
        summary.put("matchLabel", toMatchLabel(room.getMatchType()));
        summary.put("memberCount", room.getMemberCount());
        summary.put("capacity", room.getCapacity());
        summary.put("title", room.getName() + " 채팅방");
        summary.put("description", "투표, 포트폴리오, 팀원 정보를 한 번에 확인할 수 있어요.");
        summary.put("roomInfo", buildRoomInfo(room, user));
        summary.put("portfolio", buildDetailedPortfolio(room.getId()));
        summary.put("activeVotes", buildVotes(room.getId(), user, true));
        summary.put("closedVotes", buildVotes(room.getId(), user, false));
        summary.put("lastMessage", buildLastMessagePreview(room.getId()));
        markRoomAsRead(roomId, user);
        return summary;
    }

    @Transactional
    public Map<String, Object> markRoomAsRead(Long roomId, User user) {
        MatchingRoomMember membership = matchingRoomMemberRepository.findByMatchingRoomIdAndUserId(roomId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Chat room access denied"));
        membership.setLastReadAt(Instant.now());
        matchingRoomMemberRepository.save(membership);
        return Map.of(
                "roomId", roomId,
                "unreadCount", 0
        );
    }

    @Transactional
    public Map<String, Object> renameChatRoom(Long roomId, User user, String name) {
        String trimmedName = name != null ? name.trim() : "";
        if (trimmedName.isBlank()) {
            throw new ApiException("name is required", HttpStatus.BAD_REQUEST);
        }
        if (trimmedName.length() > 30) {
            throw new ApiException("name must be 30 characters or less", HttpStatus.BAD_REQUEST);
        }

        MatchingRoom room = matchingRoomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException("Chat room not found", HttpStatus.NOT_FOUND));
        if (!matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(roomId, user.getId())) {
            throw new ApiException("Chat room access denied", HttpStatus.FORBIDDEN);
        }

        room.setName(trimmedName);
        matchingRoomRepository.save(room);
        return Map.of(
                "roomId", room.getId(),
                "groupId", room.getId(),
                "name", room.getName(),
                "title", room.getName() + " 채팅방"
        );
    }

    private long countUnreadMessages(Long roomId, MatchingRoomMember membership, User user) {
        Instant baseline = membership.getLastReadAt() != null
                ? membership.getLastReadAt()
                : membership.getJoinedAt();
        if (baseline == null) {
            baseline = Instant.EPOCH;
        }
        return chatMessageRepository.countByRoomIdAndCreatedAtAfterAndUserIdNot(roomId, baseline, user.getId());
    }

    private Map<String, Object> buildRoomInfo(MatchingRoom room, User currentUser) {
        List<MatchingRoomMember> members = matchingRoomMemberRepository.findByMatchingRoomIdWithUser(room.getId());
        List<Map<String, Object>> memberSummaries = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            User member = members.get(i).getUser();
            if (member == null) {
                continue;
            }
            boolean isMe = currentUser.getId() != null && currentUser.getId().equals(member.getId());
            memberSummaries.add(Map.of(
                    "userId", member.getId(),
                    "nickname", member.getNickname() != null ? member.getNickname() : "",
                    "profileImageUrl", resolveProfileImageUrl(member),
                    "role", i == 0 ? "HOST" : "MEMBER",
                    "roleLabel", i == 0 ? "방장" : "팀원",
                    "isMe", isMe
            ));
        }

        Map<String, Object> roomInfo = new HashMap<>();
        roomInfo.put("modeLabel", deriveModeLabel(room));
        roomInfo.put("marketLabel", toMarketLabel(room.getMarketType()));
        roomInfo.put("matchLabel", toMatchLabel(room.getMatchType()));
        roomInfo.put("members", memberSummaries);
        return roomInfo;
    }

    private String resolveProfileImageUrl(User member) {
        if (member == null || member.getId() == null) {
            return "";
        }
        Optional<UserMyPagePreference> preference = userMyPagePreferenceRepository.findById(member.getId());
        if (preference.isPresent() && preference.get().getSelectedCharacterCode() != null && !preference.get().getSelectedCharacterCode().isBlank()) {
            return profileImageUrlService.profileOptionImageUrl(preference.get().getSelectedCharacterCode());
        }
        return member.getProfileImageUrl() != null ? member.getProfileImageUrl() : "";
    }

    private Map<String, Object> buildPortfolioSummary(Long roomId) {
        BigDecimal cashBalance = teamAccountRepository.findByTeamId(roomId)
                .map(TeamAccount::getCashBalance)
                .orElse(INITIAL_TEAM_BALANCE);
        BigDecimal holdingsValue = calculateHoldingsValue(roomId);
        BigDecimal totalValue = cashBalance.add(holdingsValue);
        BigDecimal profitLoss = totalValue.subtract(INITIAL_TEAM_BALANCE);
        BigDecimal profitLossPercentage = INITIAL_TEAM_BALANCE.compareTo(BigDecimal.ZERO) != 0
                ? profitLoss.divide(INITIAL_TEAM_BALANCE, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        Map<String, Object> summary = new HashMap<>();
        summary.put("title", "유니 투자클럽의 실시간 투자금");
        summary.put("description", "현재 팀 자산과 누적 손익을 빠르게 확인해보세요.");
        summary.put("totalValue", totalValue);
        summary.put("profitLoss", profitLoss);
        summary.put("profitLossPercentage", profitLossPercentage);
        return summary;
    }

    private Map<String, Object> buildDetailedPortfolio(Long roomId) {
        BigDecimal cashBalance = teamAccountRepository.findByTeamId(roomId)
                .map(TeamAccount::getCashBalance)
                .orElse(INITIAL_TEAM_BALANCE);
        List<TeamHolding> holdings = teamHoldingRepository.findByTeamId(roomId);
        List<Map<String, Object>> holdingItems = new ArrayList<>();
        BigDecimal holdingsValue = BigDecimal.ZERO;

        for (TeamHolding holding : holdings) {
            BigDecimal currentPrice = resolveCurrentPrice(holding.getStockCode(), holding.getAveragePurchasePrice());
            BigDecimal currentValue = currentPrice.multiply(BigDecimal.valueOf(holding.getQuantity()));
            BigDecimal investedValue = holding.getAveragePurchasePrice().multiply(BigDecimal.valueOf(holding.getQuantity()));
            BigDecimal profitLoss = currentValue.subtract(investedValue);
            BigDecimal profitLossPercentage = investedValue.compareTo(BigDecimal.ZERO) != 0
                    ? profitLoss.divide(investedValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            holdingsValue = holdingsValue.add(currentValue);

            holdingItems.add(Map.of(
                    "stockCode", holding.getStockCode(),
                    "stockName", holding.getStockName() != null ? holding.getStockName() : holding.getStockCode(),
                    "quantity", holding.getQuantity(),
                    "averagePrice", holding.getAveragePurchasePrice(),
                    "currentPrice", currentPrice,
                    "currentValue", currentValue,
                    "profitLoss", profitLoss,
                    "profitLossPercentage", profitLossPercentage
            ));
        }

        BigDecimal totalValue = cashBalance.add(holdingsValue);
        BigDecimal totalProfitLoss = totalValue.subtract(INITIAL_TEAM_BALANCE);
        BigDecimal totalProfitLossPercentage = INITIAL_TEAM_BALANCE.compareTo(BigDecimal.ZERO) != 0
                ? totalProfitLoss.divide(INITIAL_TEAM_BALANCE, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        Map<String, Object> portfolio = new HashMap<>();
        portfolio.put("title", "포트폴리오");
        portfolio.put("description", "보유 종목과 평가손익을 한눈에 볼 수 있어요.");
        portfolio.put("investmentAmount", INITIAL_TEAM_BALANCE);
        portfolio.put("cashBalance", cashBalance);
        portfolio.put("totalValue", totalValue);
        portfolio.put("profitLoss", totalProfitLoss);
        portfolio.put("profitLossPercentage", totalProfitLossPercentage);
        portfolio.put("holdings", holdingItems);
        return portfolio;
    }

    private BigDecimal calculateHoldingsValue(Long roomId) {
        BigDecimal total = BigDecimal.ZERO;
        for (TeamHolding holding : teamHoldingRepository.findByTeamId(roomId)) {
            BigDecimal currentPrice = resolveCurrentPrice(holding.getStockCode(), holding.getAveragePurchasePrice());
            total = total.add(currentPrice.multiply(BigDecimal.valueOf(holding.getQuantity())));
        }
        return total;
    }

    private Map<String, Object> buildLastMessagePreview(Long roomId) {
        Optional<ChatMessage> lastMessageOpt = chatMessageRepository.findTopByRoomIdOrderByCreatedAtDesc(roomId);
        if (lastMessageOpt.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("type", "empty");
            empty.put("title", "아직 대화가 없어요");
            empty.put("description", "첫 메시지를 보내 팀원과 이야기를 시작해보세요.");
            empty.put("preview", "아직 대화가 없어요");
            empty.put("timestamp", null);
            return empty;
        }

        ChatMessage lastMessage = lastMessageOpt.get();
        Map<String, Object> preview = new HashMap<>();
        preview.put("messageId", lastMessage.getId());
        preview.put("type", "user");
        preview.put("preview", lastMessage.getMessage());
        preview.put("timestamp", lastMessage.getCreatedAt() != null ? lastMessage.getCreatedAt().toString() : null);
        preview.put("senderNickname", lastMessage.getUserNickname());
        preview.put("title", "최근 메시지");
        preview.put("description", "팀원의 최신 대화를 확인해보세요.");

        String message = lastMessage.getMessage();
        if (message != null && message.trim().startsWith("{")) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = OBJECT_MAPPER.readValue(message, Map.class);
                String type = String.valueOf(parsed.get("type"));
                preview.put("type", type);
                if ("trade".equals(type)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> tradeData = (Map<String, Object>) parsed.get("tradeData");
                    String stockName = tradeData != null && tradeData.get("stockName") != null ? String.valueOf(tradeData.get("stockName")) : "종목";
                    String action = tradeData != null && tradeData.get("type") != null ? String.valueOf(tradeData.get("type")) : "매수";
                    preview.put("preview", stockName + " " + action + " 투표를 시작할게요!");
                    preview.put("title", "투자 계획 공유");
                    preview.put("description", "새로운 거래 제안이 채팅방에 올라왔어요.");
                } else if ("execution".equals(type)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> executionData = (Map<String, Object>) parsed.get("executionData");
                    String stockName = executionData != null && executionData.get("stockName") != null ? String.valueOf(executionData.get("stockName")) : "종목";
                    String action = executionData != null && executionData.get("action") != null ? String.valueOf(executionData.get("action")) : "매수";
                    preview.put("preview", stockName + " " + action + " 체결 완료");
                    preview.put("title", "거래 체결");
                    preview.put("description", "제안된 주문이 실제로 체결되었어요.");
                } else if ("feedback".equals(type) || ChatService.TYPE_GROUP_INVESTMENT_FEEDBACK_REPORT.equals(type)) {
                    preview.put("preview", "투자 끝! 피드백 리포트가 도착했어요!");
                    preview.put("title", "피드백 리포트");
                    preview.put("description", "이번 투자 결과를 요약한 리포트가 도착했어요.");
                } else if (ChatService.TYPE_NEWS_SHARE.equals(type)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> news = (Map<String, Object>) parsed.get("news");
                    String title = news != null && news.get("title") != null ? String.valueOf(news.get("title")) : "뉴스";
                    preview.put("type", ChatService.TYPE_NEWS_SHARE);
                    preview.put("preview", title);
                    preview.put("title", "뉴스 공유");
                    preview.put("description", "팀원이 함께 볼 뉴스를 공유했어요.");
                } else if (ChatService.TYPE_MENTION_ALL.equals(type)) {
                    Object mentionMessage = parsed.get("message");
                    preview.put("preview", mentionMessage != null ? String.valueOf(mentionMessage) : ChatService.MENTION_ALL_MESSAGE);
                    preview.put("title", "전체 호출");
                    preview.put("description", "팀원이 모두에게 알림을 보냈어요.");
                }
            } catch (Exception ignored) {
            }
        }

        return preview;
    }

    private Map<String, Object> buildActiveVotePreview(Long roomId, User currentUser) {
        List<Map<String, Object>> activeVotes = buildVotes(roomId, currentUser, true);
        return activeVotes.isEmpty() ? null : activeVotes.get(0);
    }

    private List<Map<String, Object>> buildVotes(Long roomId, User currentUser, boolean activeOnly) {
        List<Vote> votes = voteRepository.findByRoomIdOrderByCreatedAtDesc(roomId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Vote vote : votes) {
            boolean active = isActiveVoteStatus(vote.getStatus());
            if (activeOnly != active) {
                continue;
            }

            Map<String, Object> voteMap = new HashMap<>();
            voteMap.put("voteId", vote.getId());
            voteMap.put("type", vote.getType());
            voteMap.put("typeLabel", vote.getType());
            voteMap.put("stockName", vote.getStockName());
            voteMap.put("stockCode", vote.getStockCode());
            voteMap.put("question", vote.getStockName() + "를 " + vote.getType() + " 할까요?");
            voteMap.put("status", vote.getStatus());
            voteMap.put("statusLabel", toVoteStatusLabel(vote.getStatus()));
            voteMap.put("title", vote.getStockName() + " " + vote.getType() + " 제안");
            voteMap.put("description", active ? "지금 팀원들의 투표를 기다리고 있어요." : "종료된 투표 결과를 확인해보세요.");
            voteMap.put("proposerName", vote.getProposerName());
            voteMap.put("createdAt", vote.getCreatedAt() != null ? vote.getCreatedAt().toString() : null);
            voteMap.put("expiresAt", vote.getExpiresAt() != null ? vote.getExpiresAt().toString() : null);
            voteMap.put("timeRemainingLabel", buildTimeRemainingLabel(vote.getExpiresAt()));
            voteMap.put("reason", vote.getReason());
            voteMap.put("quantity", vote.getQuantity());
            voteMap.put("proposedPrice", vote.getProposedPrice());
            voteMap.put("orderStrategy", vote.getOrderStrategy());
            voteMap.put("voteSummary", buildVoteSummary(vote, currentUser));
            result.add(voteMap);
        }
        result.sort(Comparator.comparing(m -> String.valueOf(m.get("createdAt")), Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    private Map<String, Object> buildVoteSummary(Vote vote, User currentUser) {
        long approveCount = 0L;
        long holdCount = 0L;
        long rejectCount = 0L;
        String myVote = null;

        for (var participant : voteParticipantRepository.findByVote_IdOrderById(vote.getId())) {
            String choice = participant.getVoteChoice();
            if ("찬성".equals(choice)) {
                approveCount++;
            } else if ("보류".equals(choice)) {
                holdCount++;
            } else if ("반대".equals(choice)) {
                rejectCount++;
            }
            if (currentUser.getId() != null && currentUser.getId().equals(participant.getUserId())) {
                myVote = choice;
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("approveCount", approveCount);
        summary.put("holdCount", holdCount);
        summary.put("rejectCount", rejectCount);
        summary.put("participantCount", approveCount + holdCount + rejectCount);
        summary.put("myVote", myVote);
        summary.put("summaryLabel", String.format("찬성 %d / 보류 %d / 반대 %d", approveCount, holdCount, rejectCount));
        return summary;
    }

    private boolean isActiveVoteStatus(String status) {
        return "ongoing".equals(status)
                || "pending".equals(status)
                || "passed".equals(status)
                || "executing".equals(status);
    }

    private String buildTimeRemainingLabel(Instant expiresAt) {
        if (expiresAt == null) {
            return null;
        }
        Duration duration = Duration.between(Instant.now(), expiresAt);
        if (duration.isNegative() || duration.isZero()) {
            return "투표 종료";
        }
        long hours = duration.toHours();
        if (hours >= 1) {
            return hours + "시간 남음";
        }
        long minutes = Math.max(1, duration.toMinutes());
        return minutes + "분 남음";
    }

    private String deriveModeLabel(MatchingRoom room) {
        if (room.getName() != null && room.getName().contains("대회")) {
            return "토너먼트";
        }
        return "그룹투자";
    }

    private String toMarketLabel(String marketType) {
        if ("US".equalsIgnoreCase(marketType)) {
            return "해외 주식";
        }
        if ("KR".equalsIgnoreCase(marketType)) {
            return "국내 주식";
        }
        return "국내 주식";
    }

    private String toMatchLabel(String matchType) {
        if ("FRIEND".equalsIgnoreCase(matchType)) {
            return "친구 초대";
        }
        if ("RANDOM".equalsIgnoreCase(matchType)) {
            return "랜덤 매칭";
        }
        return "그룹 매칭";
    }

    private String toVoteStatusLabel(String status) {
        if ("ongoing".equals(status)) return "진행중";
        if ("pending".equals(status)) return "대기중";
        if ("passed".equals(status)) return "통과";
        if ("executing".equals(status)) return "체결중";
        if ("executed".equals(status)) return "종료";
        if ("expired".equals(status)) return "투표 종료";
        if ("cancelled".equals(status)) return "취소";
        if ("rejected".equals(status)) return "반대";
        return status;
    }

    private BigDecimal resolveCurrentPrice(String stockCode, BigDecimal fallbackPrice) {
        try {
            var dto = kisApiService.getStockPrice(stockCode);
            if (dto != null && dto.getCurrentPrice() != null) {
                return dto.getCurrentPrice();
            }
        } catch (Exception ignored) {
        }
        return fallbackPrice != null ? fallbackPrice : BigDecimal.ZERO;
    }
}
