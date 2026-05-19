package com.uniport.service;

import com.uniport.dto.MockInvestmentCollectiveSignalDTO;
import com.uniport.dto.MockInvestmentCtaDTO;
import com.uniport.dto.MockInvestmentHeroStatusDTO;
import com.uniport.dto.MockInvestmentHomeResponseDTO;
import com.uniport.dto.MockInvestmentLeaderboardItemDTO;
import com.uniport.dto.MockInvestmentLeaderboardTabDTO;
import com.uniport.dto.MockInvestmentLeaderboardsDTO;
import com.uniport.dto.MockInvestmentTopGroupInsightItemDTO;
import com.uniport.dto.MockInvestmentTopGroupInsightsDTO;
import com.uniport.entity.Competition;
import com.uniport.entity.MatchingRoom;
import com.uniport.entity.MatchingRoomMember;
import com.uniport.entity.TeamGameSnapshot;
import com.uniport.entity.User;
import com.uniport.entity.Vote;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import com.uniport.repository.TeamGameSnapshotRepository;
import com.uniport.repository.VoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MockInvestmentHomeDashboardService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int HERO_LEADERBOARD_LIMIT = 1000;
    private static final int TOP_INSIGHT_LIMIT = 20;
    private static final int FREE_INSIGHT_COUNT = 3;
    private static final int MIN_SIGNAL_COUNT = 10;

    private final RankingService rankingService;
    private final CompetitionService competitionService;
    private final MatchingRoomRepository matchingRoomRepository;
    private final MatchingRoomMemberRepository matchingRoomMemberRepository;
    private final VoteRepository voteRepository;
    private final TeamGameSnapshotRepository teamGameSnapshotRepository;

    public MockInvestmentHomeDashboardService(RankingService rankingService,
                                              CompetitionService competitionService,
                                              MatchingRoomRepository matchingRoomRepository,
                                              MatchingRoomMemberRepository matchingRoomMemberRepository,
                                              VoteRepository voteRepository,
                                              TeamGameSnapshotRepository teamGameSnapshotRepository) {
        this.rankingService = rankingService;
        this.competitionService = competitionService;
        this.matchingRoomRepository = matchingRoomRepository;
        this.matchingRoomMemberRepository = matchingRoomMemberRepository;
        this.voteRepository = voteRepository;
        this.teamGameSnapshotRepository = teamGameSnapshotRepository;
    }

    @Transactional(readOnly = true)
    public MockInvestmentHomeResponseDTO getHome(User user, String mode) {
        String normalizedMode = normalizeMode(mode);
        OffsetDateTime serverTime = OffsetDateTime.now(SEOUL);
        Instant now = serverTime.toInstant();
        String serverTimeText = serverTime.toString();
        List<MockInvestmentLeaderboardItemDTO> activeLeaderboard =
                rankingService.getLiveActiveTeamGameLeaderboard(HERO_LEADERBOARD_LIMIT);

        return MockInvestmentHomeResponseDTO.builder()
                .mode(normalizedMode)
                .serverTime(serverTimeText)
                .heroStatus(buildHeroStatus(user, now, activeLeaderboard))
                .collectiveSignal(buildCollectiveSignal(now, serverTimeText))
                .topGroupInsights(buildTopGroupInsights(serverTime, serverTimeText))
                .leaderboards(buildLeaderboards(user, activeLeaderboard, serverTimeText))
                .build();
    }

    private MockInvestmentHeroStatusDTO buildHeroStatus(User user,
                                                        Instant now,
                                                        List<MockInvestmentLeaderboardItemDTO> activeLeaderboard) {
        Optional<MatchingRoom> room = resolveUserRoom(user);
        if (room.isEmpty()) {
            return MockInvestmentHeroStatusDTO.builder()
                    .rankLabel("참여 전")
                    .status("not_participating")
                    .cta(MockInvestmentCtaDTO.builder()
                            .label("모의투자 시작하기")
                            .action("START_MOCK_INVESTMENT")
                            .enabled(true)
                            .build())
                    .build();
        }

        MatchingRoom matchingRoom = room.get();
        MockInvestmentLeaderboardItemDTO myLeaderboardItem = activeLeaderboard.stream()
                .filter(item -> matchingRoom.getId() != null && matchingRoom.getId().equals(item.getGroupId()))
                .findFirst()
                .orElse(null);
        Integer rank = myLeaderboardItem != null ? myLeaderboardItem.getRank() : null;
        String status = heroStatus(matchingRoom, now);

        return MockInvestmentHeroStatusDTO.builder()
                .teamId(matchingRoom.getId())
                .teamName(nonBlankOr(matchingRoom.getName(), "팀 " + matchingRoom.getId()))
                .teamGameId(myLeaderboardItem != null ? myLeaderboardItem.getTeamGameId() : "team_game_" + matchingRoom.getId())
                .rank(rank)
                .rankLabel(rankLabel(rank))
                .totalParticipants(activeLeaderboard.size())
                .status(status)
                .startedAt(formatInstant(matchingRoom.getCreatedAt()))
                .endsAt(formatInstant(matchingRoom.getEndedAt()))
                .remainingSeconds(remainingSeconds(matchingRoom, now))
                .cta(heroCta(status))
                .build();
    }

    private Optional<MatchingRoom> resolveUserRoom(User user) {
        if (user == null) {
            return Optional.empty();
        }

        Long teamRoomId = parseTeamId(user.getTeamId());
        if (teamRoomId != null) {
            Optional<MatchingRoom> teamRoom = matchingRoomRepository.findById(teamRoomId)
                    .filter(this::isStartedOrEnded);
            if (teamRoom.isPresent() && "started".equals(teamRoom.get().getStatus())) {
                return teamRoom;
            }
            Optional<MatchingRoom> memberStarted = findMemberRoom(user, "started");
            if (memberStarted.isPresent()) {
                return memberStarted;
            }
            if (teamRoom.isPresent()) {
                return teamRoom;
            }
            return findMemberRoom(user, "ended");
        }

        Optional<MatchingRoom> started = findMemberRoom(user, "started");
        if (started.isPresent()) {
            return started;
        }
        return findMemberRoom(user, "ended");
    }

    private Optional<MatchingRoom> findMemberRoom(User user, String status) {
        if (user.getId() == null) {
            return Optional.empty();
        }
        return matchingRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(user.getId()).stream()
                .map(MatchingRoomMember::getMatchingRoom)
                .filter(room -> room != null && status.equals(room.getStatus()))
                .findFirst();
    }

    private boolean isStartedOrEnded(MatchingRoom room) {
        return room != null && ("started".equals(room.getStatus()) || "ended".equals(room.getStatus()));
    }

    private String heroStatus(MatchingRoom room, Instant now) {
        if ("ended".equals(room.getStatus())) {
            return "ended";
        }
        if ("started".equals(room.getStatus()) && room.getEndedAt() != null && !room.getEndedAt().isAfter(now)) {
            return "settling";
        }
        return "active";
    }

    private MockInvestmentCtaDTO heroCta(String status) {
        if ("ended".equals(status)) {
            return MockInvestmentCtaDTO.builder()
                    .label("결과 보기")
                    .action("VIEW_RESULT")
                    .enabled(true)
                    .build();
        }
        if ("settling".equals(status)) {
            return MockInvestmentCtaDTO.builder()
                    .label("결과 보기")
                    .action("VIEW_RESULT")
                    .enabled(false)
                    .build();
        }
        return MockInvestmentCtaDTO.builder()
                .label("내 포트폴리오 보기")
                .action("VIEW_MOCK_PORTFOLIO")
                .enabled(true)
                .build();
    }

    private Long remainingSeconds(MatchingRoom room, Instant now) {
        if (!"started".equals(room.getStatus()) || room.getEndedAt() == null || !room.getEndedAt().isAfter(now)) {
            return null;
        }
        return Math.max(0L, Duration.between(now, room.getEndedAt()).getSeconds());
    }

    private MockInvestmentCollectiveSignalDTO buildCollectiveSignal(Instant now, String serverTimeText) {
        List<Vote> recentVotes = voteRepository.findByCreatedAtAfterOrderByCreatedAtDesc(now.minus(Duration.ofHours(1)));
        if (recentVotes.isEmpty()) {
            return null;
        }

        Map<String, Integer> totalByStock = new HashMap<>();
        Map<String, SignalBucket> buckets = new LinkedHashMap<>();
        for (Vote vote : recentVotes) {
            String stockKey = stockKey(vote);
            if (stockKey == null) {
                continue;
            }
            totalByStock.merge(stockKey, 1, Integer::sum);
            String action = normalizeAction(vote.getType());
            String bucketKey = stockKey + "|" + action;
            buckets.computeIfAbsent(bucketKey, key -> new SignalBucket(stockKey, action, vote))
                    .add(vote);
        }

        return buckets.values().stream()
                .filter(bucket -> bucket.count() >= MIN_SIGNAL_COUNT)
                .max(Comparator.comparingInt(SignalBucket::count)
                        .thenComparing(SignalBucket::latestCreatedAt))
                .map(bucket -> toCollectiveSignal(bucket, totalByStock.getOrDefault(bucket.stockKey(), bucket.count()), serverTimeText))
                .orElse(null);
    }

    private MockInvestmentCollectiveSignalDTO toCollectiveSignal(SignalBucket bucket, int totalForStock, String serverTimeText) {
        Vote latest = bucket.latestVote();
        int consensusRate = totalForStock > 0
                ? Math.round(bucket.count() * 100.0f / totalForStock)
                : 0;
        String stockName = nonBlankOr(latest.getStockName(), bucket.stockKey());
        String ticker = hasText(latest.getStockCode()) ? latest.getStockCode() : stockName;
        String reason = bucket.votes().stream()
                .map(Vote::getReason)
                .filter(this::hasText)
                .findFirst()
                .orElse(null);
        String summary = reason != null
                ? reason + " 의견이 높아요."
                : stockName + "에 대한 " + actionLabel(bucket.action()) + " 의견이 높아요.";

        return MockInvestmentCollectiveSignalDTO.builder()
                .stockName(stockName)
                .ticker(ticker)
                .action(bucket.action())
                .consensusRate(consensusRate)
                .participantCount(bucket.count())
                .summary(summary)
                .updatedAt(serverTimeText)
                .build();
    }

    private MockInvestmentTopGroupInsightsDTO buildTopGroupInsights(OffsetDateTime serverTime, String serverTimeText) {
        LocalDate yesterdayKst = serverTime.toLocalDate().minusDays(1);
        List<TopGroupSource> sources = teamGameSnapshotRepository.findBySnapshotDateForRanking(yesterdayKst).stream()
                .limit(TOP_INSIGHT_LIMIT)
                .map(TopGroupSource::fromSnapshot)
                .toList();
        String updatedAt = sources.isEmpty() || !hasText(sources.get(0).updatedAt())
                ? serverTimeText
                : sources.get(0).updatedAt();

        if (sources.isEmpty()) {
            sources = rankingService.getLiveActiveTeamGameLeaderboard(TOP_INSIGHT_LIMIT).stream()
                    .limit(TOP_INSIGHT_LIMIT)
                    .map(TopGroupSource::fromLeaderboard)
                    .toList();
        }

        List<MockInvestmentTopGroupInsightItemDTO> items = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            int rank = i + 1;
            TopGroupSource source = sources.get(i);
            if (rank <= FREE_INSIGHT_COUNT) {
                items.add(unlockedInsight(rank, source));
            } else {
                items.add(lockedInsight(rank, source));
            }
        }

        int freeCount = Math.min(FREE_INSIGHT_COUNT, items.size());
        int lockedCount = Math.max(0, items.size() - freeCount);
        return MockInvestmentTopGroupInsightsDTO.builder()
                .rankingBasis("YESTERDAY_RETURN_RATE")
                .totalCount(items.size())
                .freeCount(freeCount)
                .lockedCount(lockedCount)
                .items(items)
                .updatedAt(updatedAt)
                .build();
    }

    private MockInvestmentTopGroupInsightItemDTO unlockedInsight(int rank, TopGroupSource source) {
        List<Vote> votes = voteRepository.findTop20ByRoomIdOrderByCreatedAtDesc(source.groupId());
        Vote buyVote = votes.stream()
                .filter(vote -> "BUY".equals(normalizeAction(vote.getType())))
                .filter(vote -> hasText(vote.getReason()))
                .findFirst()
                .orElse(null);
        Vote sellVote = votes.stream()
                .filter(vote -> "SELL".equals(normalizeAction(vote.getType())))
                .filter(vote -> hasText(vote.getReason()))
                .findFirst()
                .orElse(null);
        Vote stockVote = sellVote != null ? sellVote : buyVote != null ? buyVote : votes.stream().findFirst().orElse(null);
        String stockName = stockVote != null ? nonBlankOr(stockVote.getStockName(), "보유 종목") : "보유 종목";
        String ticker = stockVote != null && hasText(stockVote.getStockCode()) ? stockVote.getStockCode() : stockName;
        String buyReason = buyVote != null ? buyVote.getReason() : null;
        String sellReason = sellVote != null ? sellVote.getReason() : null;
        String action = sellVote != null ? "SELL" : buyVote != null ? "BUY" : "HOLD";

        return MockInvestmentTopGroupInsightItemDTO.builder()
                .rank(rank)
                .groupId(source.groupId())
                .groupName(source.groupName())
                .memberCount(source.memberCount())
                .stockName(stockName)
                .ticker(ticker)
                .action(action)
                .yesterdayReturnRate(source.returnRate())
                .buyReason(buyReason)
                .sellReason(sellReason)
                .summary(insightSummary(source.groupName(), stockName, buyReason, sellReason))
                .locked(false)
                .build();
    }

    private MockInvestmentTopGroupInsightItemDTO lockedInsight(int rank, TopGroupSource source) {
        return MockInvestmentTopGroupInsightItemDTO.builder()
                .rank(rank)
                .groupId(source.groupId())
                .groupName(source.groupName())
                .memberCount(source.memberCount())
                .yesterdayReturnRate(source.returnRate())
                .locked(true)
                .lockedTitle("전일 수익률 " + rank + "위 팀 인사이트")
                .lockedDescription("프리미엄에서 매수/매도 근거를 확인할 수 있어요.")
                .build();
    }

    private String insightSummary(String groupName, String stockName, String buyReason, String sellReason) {
        String name = nonBlankOr(groupName, "상위 그룹");
        if (hasText(buyReason) && hasText(sellReason)) {
            return name + "은 " + buyReason + "로 " + stockName + "을 매수했고, "
                    + sellReason + " 때문에 매도/익절했어요.";
        }
        if (hasText(buyReason)) {
            return name + "은 " + buyReason + "로 " + stockName + "을 매수했어요.";
        }
        if (hasText(sellReason)) {
            return name + "은 " + sellReason + " 때문에 " + stockName + "을 매도/익절했어요.";
        }
        return name + "은 " + stockName + " 포지션을 관찰했어요.";
    }

    private MockInvestmentLeaderboardsDTO buildLeaderboards(User user,
                                                            List<MockInvestmentLeaderboardItemDTO> activeLeaderboard,
                                                            String serverTimeText) {
        List<MockInvestmentLeaderboardItemDTO> alwaysOnItems = activeLeaderboard.stream()
                .limit(5)
                .toList();
        Optional<Competition> ongoing = Optional.ofNullable(competitionService.findOngoing())
                .flatMap(optional -> optional);
        Long tournamentId = ongoing.map(Competition::getId).orElse(null);
        List<MockInvestmentLeaderboardItemDTO> tournamentItems = ongoing
                .map(competition -> rankingService.getCompetingTeams(competition.getId(), user).stream()
                        .limit(5)
                        .map(this::toTournamentLeaderboardItem)
                        .toList())
                .orElse(List.of());

        return MockInvestmentLeaderboardsDTO.builder()
                .updatedAt(serverTimeText)
                .tabs(List.of(
                        MockInvestmentLeaderboardTabDTO.builder()
                                .type("ALWAYS_ON")
                                .label("상시 모의투자")
                                .leaderboardScope("ACTIVE_TEAM_GAMES")
                                .items(alwaysOnItems)
                                .build(),
                        MockInvestmentLeaderboardTabDTO.builder()
                                .type("TOURNAMENT")
                                .label("토너먼트")
                                .leaderboardScope("ONGOING_TOURNAMENT")
                                .tournamentId(tournamentId)
                                .items(tournamentItems)
                                .build()
                ))
                .build();
    }

    private MockInvestmentLeaderboardItemDTO toTournamentLeaderboardItem(Map<String, Object> row) {
        return MockInvestmentLeaderboardItemDTO.builder()
                .rank(asInteger(row.get("rank")))
                .groupId(parseTeamId(stringValue(row.get("teamId"))))
                .groupName(stringValue(row.get("groupName")))
                .teamGameId(stringValue(row.get("teamId")))
                .totalAssetAmount(asBigDecimal(row.get("totalValue")))
                .returnRate(asBigDecimal(row.get("profitLossPercentage")))
                .build();
    }

    private String normalizeMode(String mode) {
        return mode != null && "TOURNAMENT".equalsIgnoreCase(mode) ? "TOURNAMENT" : "ALWAYS_ON";
    }

    private String normalizeAction(String type) {
        if (type == null) {
            return "UNKNOWN";
        }
        String normalized = type.trim().toUpperCase();
        if ("BUY".equals(normalized) || "매수".equals(type.trim())) {
            return "BUY";
        }
        if ("SELL".equals(normalized) || "TAKE_PROFIT".equals(normalized)
                || "매도".equals(type.trim()) || "익절".equals(type.trim())) {
            return "SELL";
        }
        return normalized.isBlank() ? "UNKNOWN" : normalized;
    }

    private String actionLabel(String action) {
        return "SELL".equals(action) ? "매도" : "BUY".equals(action) ? "매수" : "보유";
    }

    private String rankLabel(Integer rank) {
        if (rank == null) {
            return "집계 중";
        }
        return rank > 999 ? "999+위" : rank + "위";
    }

    private String stockKey(Vote vote) {
        if (hasText(vote.getStockCode())) {
            return vote.getStockCode();
        }
        if (hasText(vote.getStockName())) {
            return vote.getStockName();
        }
        return null;
    }

    private Long parseTeamId(String teamId) {
        if (!hasText(teamId) || !teamId.startsWith("team-")) {
            return null;
        }
        try {
            return Long.parseLong(teamId.substring(5));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer asInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return null;
    }

    private String formatInstant(Instant instant) {
        return instant != null ? instant.atZone(SEOUL).toOffsetDateTime().toString() : null;
    }

    private String nonBlankOr(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static final class SignalBucket {
        private final String stockKey;
        private final String action;
        private final List<Vote> votes = new ArrayList<>();
        private Vote latestVote;

        private SignalBucket(String stockKey, String action, Vote latestVote) {
            this.stockKey = stockKey;
            this.action = action;
            this.latestVote = latestVote;
        }

        private void add(Vote vote) {
            votes.add(vote);
            if (latestVote == null || vote.getCreatedAt().isAfter(latestVote.getCreatedAt())) {
                latestVote = vote;
            }
        }

        private String stockKey() {
            return stockKey;
        }

        private String action() {
            return action;
        }

        private int count() {
            return votes.size();
        }

        private Instant latestCreatedAt() {
            return latestVote != null ? latestVote.getCreatedAt() : Instant.EPOCH;
        }

        private Vote latestVote() {
            return latestVote;
        }

        private List<Vote> votes() {
            return votes;
        }
    }

    private record TopGroupSource(
            Long groupId,
            String groupName,
            Integer memberCount,
            BigDecimal returnRate,
            String updatedAt
    ) {
        private static TopGroupSource fromSnapshot(TeamGameSnapshot snapshot) {
            return new TopGroupSource(
                    snapshot.getTeamId(),
                    snapshot.getTeamName(),
                    snapshot.getMemberCount(),
                    snapshot.getReturnRate(),
                    snapshot.getSnapshotAt() != null
                            ? snapshot.getSnapshotAt().atZone(SEOUL).toOffsetDateTime().toString()
                            : null
            );
        }

        private static TopGroupSource fromLeaderboard(MockInvestmentLeaderboardItemDTO item) {
            return new TopGroupSource(
                    item.getGroupId(),
                    item.getGroupName(),
                    null,
                    item.getReturnRate(),
                    null
            );
        }
    }
}
