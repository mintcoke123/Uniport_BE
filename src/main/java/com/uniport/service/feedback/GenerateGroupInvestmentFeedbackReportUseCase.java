package com.uniport.service.feedback;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.entity.ChatMessage;
import com.uniport.entity.GroupInvestmentFeedbackReport;
import com.uniport.entity.GroupInvestmentMemberFeedback;
import com.uniport.entity.MatchingRoom;
import com.uniport.entity.MatchingRoomMember;
import com.uniport.entity.Vote;
import com.uniport.entity.VoteParticipant;
import com.uniport.exception.ApiException;
import com.uniport.repository.GroupInvestmentFeedbackReportRepository;
import com.uniport.repository.GroupInvestmentMemberFeedbackRepository;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import com.uniport.repository.VoteParticipantRepository;
import com.uniport.repository.VoteRepository;
import com.uniport.service.ChatService;
import com.uniport.service.PushNotificationService;
import com.uniport.service.StockVisualAssetResolver;
import com.uniport.service.TradeNewsContext;
import com.uniport.service.TradeNewsContextService;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GenerateGroupInvestmentFeedbackReportUseCase {

    private static final Logger log = LoggerFactory.getLogger(GenerateGroupInvestmentFeedbackReportUseCase.class);
    public static final BigDecimal INITIAL_TEAM_CAPITAL = new BigDecimal("10000000");
    private static final Duration MOCK_INVESTMENT_SESSION_DURATION = Duration.ofDays(7);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MatchingRoomRepository matchingRoomRepository;
    private final VoteRepository voteRepository;
    private final VoteParticipantRepository voteParticipantRepository;
    private final MatchingRoomMemberRepository matchingRoomMemberRepository;
    private final GroupInvestmentFeedbackReportRepository reportRepository;
    private final GroupInvestmentMemberFeedbackRepository memberFeedbackRepository;
    private final GroupInvestmentEndPriceProvider endPriceProvider;
    private final GroupInvestmentFeedbackCalculator calculator;
    private final MemberDecisionFeedbackAnalyzer analyzer;
    private final FeedbackCommentGenerator commentGenerator;
    private final GroupInvestmentPointSettlementService pointSettlementService;
    private final StockVisualAssetResolver stockVisualAssetResolver;
    private final TradeNewsContextService tradeNewsContextService;
    private final ChatService chatService;
    private final PushNotificationService pushNotificationService;

    public GenerateGroupInvestmentFeedbackReportUseCase(MatchingRoomRepository matchingRoomRepository,
                                                        VoteRepository voteRepository,
                                                        VoteParticipantRepository voteParticipantRepository,
                                                        MatchingRoomMemberRepository matchingRoomMemberRepository,
                                                        GroupInvestmentFeedbackReportRepository reportRepository,
                                                        GroupInvestmentMemberFeedbackRepository memberFeedbackRepository,
                                                        GroupInvestmentEndPriceProvider endPriceProvider,
                                                        GroupInvestmentFeedbackCalculator calculator,
                                                        MemberDecisionFeedbackAnalyzer analyzer,
                                                        FeedbackCommentGenerator commentGenerator,
                                                        GroupInvestmentPointSettlementService pointSettlementService,
                                                        StockVisualAssetResolver stockVisualAssetResolver,
                                                        TradeNewsContextService tradeNewsContextService,
                                                        ChatService chatService,
                                                        PushNotificationService pushNotificationService) {
        this.matchingRoomRepository = matchingRoomRepository;
        this.voteRepository = voteRepository;
        this.voteParticipantRepository = voteParticipantRepository;
        this.matchingRoomMemberRepository = matchingRoomMemberRepository;
        this.reportRepository = reportRepository;
        this.memberFeedbackRepository = memberFeedbackRepository;
        this.endPriceProvider = endPriceProvider;
        this.calculator = calculator;
        this.analyzer = analyzer;
        this.commentGenerator = commentGenerator;
        this.pointSettlementService = pointSettlementService;
        this.stockVisualAssetResolver = stockVisualAssetResolver;
        this.tradeNewsContextService = tradeNewsContextService;
        this.chatService = chatService;
        this.pushNotificationService = pushNotificationService;
    }

    @Transactional
    public Map<String, Object> generateForRoom(Long roomId) {
        MatchingRoom room = matchingRoomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException("방을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        Optional<GroupInvestmentFeedbackReport> existing = reportRepository.findBySessionId(room.getId());
        if (existing.isPresent()) {
            GroupInvestmentFeedbackReport report = existing.get();
            List<GroupInvestmentMemberFeedback> memberFeedbacks = memberFeedbackRepository.findByReportOrderBySortOrderAsc(report);
            if (!"SETTLED".equals(report.getPointSettlementStatus())) {
                pointSettlementService.settle(report, memberFeedbacks);
            }
            return toResponse(report, memberFeedbacks);
        }

        Instant endedAt = room.getEndedAt() != null ? room.getEndedAt() : Instant.now();
        GroupInvestmentFeedbackReport report = GroupInvestmentFeedbackReport.builder()
                .sessionId(room.getId())
                .roomId(room.getId())
                .status("GENERATING")
                .initialCapital(INITIAL_TEAM_CAPITAL)
                .finalEquity(INITIAL_TEAM_CAPITAL)
                .profitAmount(BigDecimal.ZERO)
                .returnRate(BigDecimal.ZERO)
                .aiComment("")
                .aiSource("TEMPLATE")
                .endedAt(endedAt)
                .generatedAt(Instant.now())
                .build();
        report = reportRepository.save(report);

        try {
            List<Vote> executedVotes = voteRepository.findByRoomIdOrderByCreatedAtDesc(room.getId()).stream()
                    .filter(this::isExecutedVote)
                    .sorted(Comparator.comparing(Vote::getExecutedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            List<ExecutedTradeSnapshot> trades = executedVotes.stream()
                    .map(this::toTradeSnapshot)
                    .toList();
            Map<String, BigDecimal> endPrices = resolveEndPrices(trades, endedAt);
            GroupInvestmentFeedbackCalculation calculation = calculator.calculate(
                    new GroupInvestmentSessionSnapshot(room.getId(), room.getId(), INITIAL_TEAM_CAPITAL, endedAt),
                    trades,
                    endPrices
            );
            List<MemberSnapshot> members = matchingRoomMemberRepository.findByMatchingRoomIdWithUser(room.getId()).stream()
                    .map(this::toMemberSnapshot)
                    .toList();
            Map<Long, DecisionVoteSnapshot> decisions = buildDecisionSnapshots(executedVotes);
            List<MemberDecisionFeedback> memberFeedbacks = analyzer.analyze(INITIAL_TEAM_CAPITAL, calculation.tradePnls(), decisions, members);
            GeneratedFeedbackComment comment = commentGenerator.generate(calculation);

            report.setStatus("PUBLISHED");
            report.setFinalEquity(calculation.finalEquity());
            report.setProfitAmount(calculation.profitAmount());
            report.setReturnRate(calculation.returnRate());
            report.setBestTradeJson(toJson(calculation.bestTrade().orElse(null)));
            report.setWorstTradeJson(toJson(calculation.worstTrade().orElse(null)));
            report.setAiComment(comment.comment());
            report.setAiSource(comment.source());
            report = reportRepository.save(report);

            List<GroupInvestmentMemberFeedback> entities = toMemberEntities(report, memberFeedbacks);
            List<GroupInvestmentMemberFeedback> savedEntities = memberFeedbackRepository.saveAll(entities);
            if (savedEntities != null) {
                entities = savedEntities;
            }
            GroupInvestmentPointSettlementResult settlementResult = pointSettlementService.settle(report, entities);

            Map<String, Object> snapshot = toResponse(report, entities);
            try {
                ChatMessage message = chatService.saveFeedbackReportMessage(room.getId(), report.getId(), snapshot);
                report.setPublishedMessageId(message.getId());
                report = reportRepository.save(report);
            } catch (RuntimeException messageError) {
                log.warn("[group-feedback-report] report saved but chat message publish failed. roomId={} reportId={} error={}",
                        room.getId(), report.getId(), messageError.getMessage());
            }
            pushNotificationService.sendGroupInvestmentFeedbackReport(
                    room.getId(),
                    report.getId(),
                    signedDecimal(report.getReturnRate()) + "%",
                    settlementResult != null ? settlementResult.totalSettledPoint() : totalSettledPoint(entities),
                    settlementResult != null ? settlementResult.totalSettledExp() : totalSettledExp(entities),
                    members.stream()
                            .map(MemberSnapshot::memberId)
                            .filter(Objects::nonNull)
                            .toList()
            );
            sendPointSettlementNotifications(room.getId(), report.getId(), entities);
            return toResponse(report, entities);
        } catch (RuntimeException ex) {
            report.setStatus("FAILED");
            reportRepository.save(report);
            throw ex;
        }
    }

    @Transactional
    public int generatePendingReports() {
        Instant now = Instant.now();
        int generated = 0;
        Set<Long> processedRoomIds = new HashSet<>();
        for (MatchingRoom room : matchingRoomRepository.findByStatusAndEndedAtLessThanEqualOrderByEndedAtAsc("started", now)) {
            generated += endStartedRoomAndGenerateReport(room, processedRoomIds);
        }
        Instant legacyStartedCutoff = now.minus(MOCK_INVESTMENT_SESSION_DURATION);
        for (MatchingRoom room : matchingRoomRepository.findByStatusAndEndedAtIsNullAndCreatedAtLessThanEqualOrderByCreatedAtAsc("started", legacyStartedCutoff)) {
            if (room.getId() == null || processedRoomIds.contains(room.getId())) {
                continue;
            }
            Instant inferredEndedAt = room.getCreatedAt() != null
                    ? room.getCreatedAt().plus(MOCK_INVESTMENT_SESSION_DURATION)
                    : now;
            if (inferredEndedAt.isAfter(now)) {
                continue;
            }
            room.setEndedAt(inferredEndedAt);
            generated += endStartedRoomAndGenerateReport(room, processedRoomIds);
        }
        for (MatchingRoom room : matchingRoomRepository.findByStatusAndEndedAtLessThanEqualOrderByEndedAtAsc("ended", now)) {
            if (room.getId() == null || processedRoomIds.contains(room.getId()) || reportRepository.existsBySessionId(room.getId())) {
                continue;
            }
            generateForRoom(room.getId());
            generated++;
        }
        return generated;
    }

    private int endStartedRoomAndGenerateReport(MatchingRoom room, Set<Long> processedRoomIds) {
        if (room.getId() == null || !processedRoomIds.add(room.getId())) {
            return 0;
        }
        room.setStatus("ended");
        matchingRoomRepository.save(room);
        if (reportRepository.existsBySessionId(room.getId())) {
            return 0;
        }
        generateForRoom(room.getId());
        return 1;
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> findByRoom(Long roomId) {
        return reportRepository.findBySessionId(roomId)
                .map(report -> toResponse(report, memberFeedbackRepository.findByReportOrderBySortOrderAsc(report)));
    }

    public Map<String, Object> toResponse(GroupInvestmentFeedbackReport report,
                                          List<GroupInvestmentMemberFeedback> memberFeedbacks) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reportId", report.getId());
        body.put("sessionId", report.getSessionId());
        body.put("roomId", report.getRoomId());
        body.put("status", report.getStatus());
        body.put("endedAt", report.getEndedAt() != null ? report.getEndedAt().toString() : null);
        body.put("returnRate", report.getReturnRate());
        body.put("returnRateText", signedDecimal(report.getReturnRate()) + "%");
        body.put("finalEquity", report.getFinalEquity());
        body.put("finalEquityText", won(report.getFinalEquity()));
        body.put("profitAmount", report.getProfitAmount());
        body.put("profitAmountText", signedWon(report.getProfitAmount()));
        body.put("bestTrade", jsonToMap(report.getBestTradeJson()));
        body.put("worstTrade", jsonToMap(report.getWorstTradeJson()));
        body.put("aiComment", report.getAiComment());
        body.put("aiSource", report.getAiSource());
        body.put("pointSettlementStatus", report.getPointSettlementStatus());
        body.put("pointSettledAt", report.getPointSettledAt() != null ? report.getPointSettledAt().toString() : null);
        List<Map<String, Object>> memberResponse = toMemberResponse(memberFeedbacks);
        body.put("totalSettledPoint", memberResponse.stream()
                .map(item -> item.get("settledPoint"))
                .filter(Integer.class::isInstance)
                .map(Integer.class::cast)
                .reduce(0, Integer::sum));
        body.put("totalSettledExp", memberResponse.stream()
                .map(item -> item.get("settledExp"))
                .filter(Integer.class::isInstance)
                .map(Integer.class::cast)
                .reduce(0, Integer::sum));
        body.put("memberAnalyses", memberResponse);
        body.put("topMembers", memberResponse.stream().limit(3).toList());
        return body;
    }

    private boolean isExecutedVote(Vote vote) {
        return vote != null
                && "executed".equals(vote.getStatus())
                && vote.getStockCode() != null
                && !vote.getStockCode().isBlank()
                && vote.getQuantity() > 0
                && (vote.getExecutionPrice() != null || vote.getProposedPrice() != null);
    }

    private ExecutedTradeSnapshot toTradeSnapshot(Vote vote) {
        BigDecimal price = vote.getExecutionPrice() != null ? vote.getExecutionPrice() : vote.getProposedPrice();
        return new ExecutedTradeSnapshot(
                vote.getId(),
                vote.getId(),
                vote.getProposerId(),
                vote.getStockCode(),
                vote.getStockName(),
                "매도".equals(vote.getType()) ? TradeSide.SELL : TradeSide.BUY,
                vote.getQuantity(),
                price,
                vote.getReason(),
                BigDecimal.ZERO,
                vote.getExecutedAt() != null ? vote.getExecutedAt() : vote.getCreatedAt()
        );
    }

    private Map<String, BigDecimal> resolveEndPrices(List<ExecutedTradeSnapshot> trades, Instant endedAt) {
        Map<String, BigDecimal> fallbackPrices = new HashMap<>();
        for (ExecutedTradeSnapshot trade : trades) {
            fallbackPrices.put(trade.stockCode(), trade.executedPrice());
        }
        Map<String, BigDecimal> endPrices = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : fallbackPrices.entrySet()) {
            endPrices.put(entry.getKey(), endPriceProvider.resolveEndPrice(entry.getKey(), endedAt, entry.getValue()));
        }
        return endPrices;
    }

    private Map<Long, DecisionVoteSnapshot> buildDecisionSnapshots(List<Vote> executedVotes) {
        Map<Long, DecisionVoteSnapshot> decisions = new HashMap<>();
        for (Vote vote : executedVotes) {
            List<VoteParticipant> participants = voteParticipantRepository.findByVote_IdOrderById(vote.getId());
            Set<Long> approveVoterIds = participants.stream()
                    .filter(participant -> "찬성".equals(participant.getVoteChoice()))
                    .map(VoteParticipant::getUserId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Set<Long> participantIds = participants.stream()
                    .map(VoteParticipant::getUserId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (vote.getProposerId() != null) {
                approveVoterIds.add(vote.getProposerId());
                participantIds.add(vote.getProposerId());
            }
            decisions.put(vote.getId(), new DecisionVoteSnapshot(
                    vote.getId(),
                    vote.getProposerId(),
                    "매도".equals(vote.getType()) ? TradeSide.SELL : TradeSide.BUY,
                    vote.getStockName(),
                    approveVoterIds,
                    participantIds
            ));
        }
        return decisions;
    }

    private MemberSnapshot toMemberSnapshot(MatchingRoomMember member) {
        var user = member.getUser();
        return new MemberSnapshot(
                user.getId(),
                user.getNickname() != null ? user.getNickname() : "",
                user.getProfileImageUrl()
        );
    }

    private List<GroupInvestmentMemberFeedback> toMemberEntities(GroupInvestmentFeedbackReport report,
                                                                 List<MemberDecisionFeedback> memberFeedbacks) {
        return memberFeedbacks.stream()
                .map(item -> GroupInvestmentMemberFeedback.builder()
                        .report(report)
                        .memberId(item.memberId())
                        .nickname(item.nickname())
                        .avatarUrl(item.avatarUrl())
                        .representativeDecision(item.representativeDecision())
                        .level(item.level())
                        .contributionAmount(item.contributionAmount())
                        .contributionRate(item.contributionRate())
                        .participatedDecisionCount(item.participatedDecisionCount())
                        .totalDecisionCount(item.totalDecisionCount())
                        .participationRate(item.participationRate())
                        .sortOrder(item.sortOrder())
                        .build())
                .toList();
    }

    private String toJson(TradePnlSnapshot trade) {
        if (trade == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(tradeToMap(trade));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize trade snapshot", e);
        }
    }

    private Map<String, Object> tradeToMap(TradePnlSnapshot trade) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tradeId", trade.tradeId());
        map.put("decisionId", trade.decisionId());
        map.put("proposerId", trade.proposerId());
        map.put("stockCode", trade.stockCode());
        map.put("stockName", trade.stockName());
        String logoUrl = null;
        map.put("logoUrl", logoUrl);
        map.put("visual", stockVisualAssetResolver.resolve("KRX", trade.stockCode(), trade.stockName(), logoUrl));
        map.put("newsContext", tradeNewsContextToMap(tradeNewsContextService.summarize(
                trade.stockCode(),
                trade.stockName(),
                trade.executedAt()
        )));
        map.put("side", trade.side().name());
        map.put("quantity", trade.quantity());
        map.put("executedPrice", trade.executedPrice());
        map.put("pnlAmount", trade.pnlAmount());
        map.put("pnlRate", trade.pnlRate());
        map.put("executedAt", trade.executedAt() != null ? trade.executedAt().toString() : null);
        return map;
    }

    private Map<String, Object> tradeNewsContextToMap(TradeNewsContext context) {
        if (context == null || (context.beforeNewsCount() == 0 && context.afterNewsCount() == 0)) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("beforeNewsCount", context.beforeNewsCount());
        map.put("afterNewsCount", context.afterNewsCount());
        map.put("beforeSentiment", context.beforeSentiment());
        map.put("afterSentiment", context.afterSentiment());
        map.put("feedbackHint", context.feedbackHint());
        map.put("beforeHeadlines", context.beforeHeadlines());
        map.put("afterHeadlines", context.afterHeadlines());
        return map;
    }

    private Map<String, Object> jsonToMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<Map<String, Object>> toMemberResponse(List<GroupInvestmentMemberFeedback> memberFeedbacks) {
        if (memberFeedbacks == null) {
            return List.of();
        }
        return memberFeedbacks.stream()
                .sorted(Comparator.comparingInt(GroupInvestmentMemberFeedback::getSortOrder))
                .map(item -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("memberId", item.getMemberId());
                    map.put("nickname", item.getNickname());
                    map.put("avatarUrl", item.getAvatarUrl());
                    map.put("representativeDecision", item.getRepresentativeDecision());
                    map.put("level", item.getLevel());
                    map.put("contributionAmount", item.getContributionAmount());
                    map.put("contributionRate", item.getContributionRate());
                    map.put("participatedDecisionCount", item.getParticipatedDecisionCount());
                    map.put("totalDecisionCount", item.getTotalDecisionCount());
                    map.put("participationRate", item.getParticipationRate());
                    map.put("settledPoint", nonNegative(item.getSettledPoint()));
                    map.put("settledExp", nonNegative(item.getSettledExp()));
                    map.put("pointSettlementStatus", item.getPointSettlementStatus());
                    map.put("pointTransactionId", item.getPointTransactionId());
                    map.put("sortOrder", item.getSortOrder());
                    return map;
                })
                .toList();
    }

    private int totalSettledPoint(List<GroupInvestmentMemberFeedback> memberFeedbacks) {
        if (memberFeedbacks == null) {
            return 0;
        }
        return memberFeedbacks.stream()
                .map(GroupInvestmentMemberFeedback::getSettledPoint)
                .filter(Objects::nonNull)
                .map(GenerateGroupInvestmentFeedbackReportUseCase::nonNegative)
                .reduce(0, Integer::sum);
    }

    private int totalSettledExp(List<GroupInvestmentMemberFeedback> memberFeedbacks) {
        if (memberFeedbacks == null) {
            return 0;
        }
        return memberFeedbacks.stream()
                .map(GroupInvestmentMemberFeedback::getSettledExp)
                .filter(Objects::nonNull)
                .map(GenerateGroupInvestmentFeedbackReportUseCase::nonNegative)
                .reduce(0, Integer::sum);
    }

    private void sendPointSettlementNotifications(Long roomId,
                                                  Long reportId,
                                                  List<GroupInvestmentMemberFeedback> memberFeedbacks) {
        if (memberFeedbacks == null) {
            return;
        }
        for (GroupInvestmentMemberFeedback memberFeedback : memberFeedbacks) {
            if (memberFeedback == null || memberFeedback.getMemberId() == null) {
                continue;
            }
            pushNotificationService.sendGroupInvestmentPointSettlement(
                    roomId,
                    reportId,
                    memberFeedback.getMemberId(),
                    nonNegative(memberFeedback.getSettledPoint()),
                    nonNegative(memberFeedback.getSettledExp())
            );
        }
    }

    private static int nonNegative(Integer value) {
        return Math.max(value != null ? value : 0, 0);
    }

    private static String won(BigDecimal value) {
        return String.format("%,d원", value.setScale(0, RoundingMode.HALF_UP).longValue());
    }

    private static String signedWon(BigDecimal value) {
        String sign = value.compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
        return sign + won(value);
    }

    private static String signedDecimal(BigDecimal value) {
        String sign = value.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        return sign + value.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }
}
