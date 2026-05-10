package com.uniport.service.feedback;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class MemberDecisionFeedbackAnalyzer {

    private static final BigDecimal PROPOSER_SHARE = new BigDecimal("0.7");
    private static final BigDecimal VOTER_SHARE = new BigDecimal("0.3");

    public List<MemberDecisionFeedback> analyze(BigDecimal initialCapital,
                                                List<TradePnlSnapshot> tradePnls,
                                                Map<Long, DecisionVoteSnapshot> decisions,
                                                List<MemberSnapshot> members) {
        BigDecimal capital = initialCapital != null && initialCapital.compareTo(BigDecimal.ZERO) > 0
                ? initialCapital
                : BigDecimal.ONE;
        Map<Long, Accumulator> byMemberId = new LinkedHashMap<>();
        if (members != null) {
            for (MemberSnapshot member : members) {
                if (member != null && member.memberId() != null) {
                    byMemberId.put(member.memberId(), new Accumulator(member));
                }
            }
        }

        Map<Long, DecisionVoteSnapshot> decisionMap = decisions != null ? decisions : Map.of();
        int totalDecisionCount = decisionMap.size();
        Map<Long, Integer> participatedDecisionCounts = participatedDecisionCounts(decisionMap);
        if (tradePnls != null) {
            for (TradePnlSnapshot tradePnl : tradePnls) {
                if (tradePnl == null) {
                    continue;
                }
                DecisionVoteSnapshot decision = decisionMap.get(tradePnl.decisionId());
                Long proposerId = decision != null && decision.proposerId() != null
                        ? decision.proposerId()
                        : tradePnl.proposerId();
                if (proposerId == null) {
                    continue;
                }
                byMemberId.computeIfAbsent(proposerId, id -> new Accumulator(new MemberSnapshot(id, "", null)));

                Set<Long> approveVoters = decision != null && decision.approveVoterIds() != null
                        ? decision.approveVoterIds()
                        : Set.of();
                List<Long> votersExcludingProposer = approveVoters.stream()
                        .filter(Objects::nonNull)
                        .filter(id -> !id.equals(proposerId))
                        .distinct()
                        .toList();

                if (votersExcludingProposer.isEmpty()) {
                    addContribution(byMemberId, proposerId, tradePnl, tradePnl.pnlAmount());
                } else {
                    BigDecimal proposalAmount = tradePnl.pnlAmount().multiply(PROPOSER_SHARE);
                    BigDecimal votePool = tradePnl.pnlAmount().multiply(VOTER_SHARE);
                    BigDecimal voterAmount = votePool.divide(BigDecimal.valueOf(votersExcludingProposer.size()), 8, RoundingMode.HALF_UP);
                    addContribution(byMemberId, proposerId, tradePnl, proposalAmount);
                    for (Long voterId : votersExcludingProposer) {
                        byMemberId.computeIfAbsent(voterId, id -> new Accumulator(new MemberSnapshot(id, "", null)));
                        addContribution(byMemberId, voterId, tradePnl, voterAmount);
                    }
                }
            }
        }

        List<MemberDecisionFeedback> raw = new ArrayList<>();
        for (Accumulator accumulator : byMemberId.values()) {
            BigDecimal amount = money(accumulator.contributionAmount);
            BigDecimal rate = amount.divide(capital, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
            int participatedDecisionCount = participatedDecisionCounts.getOrDefault(accumulator.member.memberId(), 0);
            raw.add(new MemberDecisionFeedback(
                    accumulator.member.memberId(),
                    accumulator.member.nickname() != null ? accumulator.member.nickname() : "",
                    accumulator.member.avatarUrl(),
                    accumulator.representativeDecision != null ? accumulator.representativeDecision : "관망 의견 유지",
                    level(rate),
                    amount,
                    rate,
                    participatedDecisionCount,
                    totalDecisionCount,
                    participationRate(participatedDecisionCount, totalDecisionCount),
                    Integer.MAX_VALUE
            ));
        }

        Map<Long, Integer> sortOrders = sortOrders(raw);
        return raw.stream()
                .map(item -> new MemberDecisionFeedback(
                        item.memberId(),
                        item.nickname(),
                        item.avatarUrl(),
                        item.representativeDecision(),
                        item.level(),
                        item.contributionAmount(),
                        item.contributionRate(),
                        item.participatedDecisionCount(),
                        item.totalDecisionCount(),
                        item.participationRate(),
                        sortOrders.getOrDefault(item.memberId(), Integer.MAX_VALUE)
                ))
                .sorted(Comparator.comparingInt(MemberDecisionFeedback::sortOrder)
                        .thenComparing(MemberDecisionFeedback::contributionAmount, Comparator.reverseOrder()))
                .toList();
    }

    private Map<Long, Integer> participatedDecisionCounts(Map<Long, DecisionVoteSnapshot> decisionMap) {
        Map<Long, Integer> counts = new HashMap<>();
        for (DecisionVoteSnapshot decision : decisionMap.values()) {
            if (decision == null) {
                continue;
            }
            Set<Long> participantIds = decision.participantIds() != null ? decision.participantIds() : Set.of();
            participantIds.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .forEach(memberId -> counts.merge(memberId, 1, Integer::sum));
        }
        return counts;
    }

    private void addContribution(Map<Long, Accumulator> byMemberId,
                                 Long memberId,
                                 TradePnlSnapshot tradePnl,
                                 BigDecimal amount) {
        Accumulator accumulator = byMemberId.get(memberId);
        if (accumulator == null || amount == null) {
            return;
        }
        accumulator.contributionAmount = accumulator.contributionAmount.add(amount);
        if (accumulator.representativeImpact == null
                || amount.abs().compareTo(accumulator.representativeImpact.abs()) > 0) {
            accumulator.representativeImpact = amount;
            accumulator.representativeDecision = representativeDecision(tradePnl);
        }
    }

    private Map<Long, Integer> sortOrders(List<MemberDecisionFeedback> feedbacks) {
        Map<Long, Integer> sortOrders = new HashMap<>();
        int order = 0;
        order = putFirstByLevel(feedbacks, sortOrders, order, "HIGH", Comparator.comparing(MemberDecisionFeedback::contributionAmount).reversed());
        order = putFirstByLevel(feedbacks, sortOrders, order, "MEDIUM", Comparator.comparing(MemberDecisionFeedback::contributionAmount).reversed());
        order = putFirstByLevel(feedbacks, sortOrders, order, "LOW", Comparator.comparing(MemberDecisionFeedback::contributionAmount));

        List<MemberDecisionFeedback> remaining = feedbacks.stream()
                .filter(item -> !sortOrders.containsKey(item.memberId()))
                .sorted(Comparator.comparing(MemberDecisionFeedback::contributionAmount).reversed())
                .toList();
        for (MemberDecisionFeedback item : remaining) {
            sortOrders.put(item.memberId(), order++);
        }
        return sortOrders;
    }

    private int putFirstByLevel(List<MemberDecisionFeedback> feedbacks,
                                Map<Long, Integer> sortOrders,
                                int order,
                                String level,
                                Comparator<MemberDecisionFeedback> comparator) {
        feedbacks.stream()
                .filter(item -> level.equals(item.level()))
                .filter(item -> !sortOrders.containsKey(item.memberId()))
                .sorted(comparator)
                .findFirst()
                .ifPresent(item -> sortOrders.put(item.memberId(), order));
        return sortOrders.containsValue(order) ? order + 1 : order;
    }

    private String representativeDecision(TradePnlSnapshot tradePnl) {
        if (tradePnl == null) {
            return "관망 의견 유지";
        }
        boolean positive = tradePnl.pnlAmount() != null && tradePnl.pnlAmount().compareTo(BigDecimal.ZERO) >= 0;
        if (tradePnl.side() == TradeSide.BUY) {
            return positive ? safeStockName(tradePnl.stockName()) + " 매수 제안" : "공격적 매수 판단";
        }
        if (tradePnl.side() == TradeSide.SELL) {
            return positive ? "위험 자산 비중 축소" : "매도 타이밍 지연";
        }
        return "관망 의견 유지";
    }

    private static String safeStockName(String stockName) {
        return stockName != null && !stockName.isBlank() ? stockName : "종목";
    }

    private static String level(BigDecimal contributionRate) {
        if (contributionRate.compareTo(BigDecimal.valueOf(5)) >= 0) {
            return "HIGH";
        }
        if (contributionRate.compareTo(BigDecimal.ZERO) >= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    private static BigDecimal participationRate(int participatedDecisionCount, int totalDecisionCount) {
        if (totalDecisionCount <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(participatedDecisionCount)
                .divide(BigDecimal.valueOf(totalDecisionCount), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private static final class Accumulator {
        private final MemberSnapshot member;
        private BigDecimal contributionAmount = BigDecimal.ZERO;
        private BigDecimal representativeImpact;
        private String representativeDecision;

        private Accumulator(MemberSnapshot member) {
            this.member = member;
        }
    }
}
