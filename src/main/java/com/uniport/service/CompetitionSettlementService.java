package com.uniport.service;

import com.uniport.entity.Competition;
import com.uniport.entity.CompetitionResult;
import com.uniport.entity.MatchingRoom;
import com.uniport.entity.MatchingRoomMember;
import com.uniport.entity.PointTransaction;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.CompetitionRepository;
import com.uniport.repository.CompetitionResultRepository;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CompetitionSettlementService {

    private static final String SOURCE_TYPE = "COMPETITION_REWARD";

    private final CompetitionRepository competitionRepository;
    private final MatchingRoomRepository matchingRoomRepository;
    private final MatchingRoomMemberRepository matchingRoomMemberRepository;
    private final CompetitionResultRepository resultRepository;
    private final RankingService rankingService;
    private final PointLedgerService pointLedgerService;

    public CompetitionSettlementService(CompetitionRepository competitionRepository,
                                        MatchingRoomRepository matchingRoomRepository,
                                        MatchingRoomMemberRepository matchingRoomMemberRepository,
                                        CompetitionResultRepository resultRepository,
                                        RankingService rankingService,
                                        PointLedgerService pointLedgerService) {
        this.competitionRepository = competitionRepository;
        this.matchingRoomRepository = matchingRoomRepository;
        this.matchingRoomMemberRepository = matchingRoomMemberRepository;
        this.resultRepository = resultRepository;
        this.rankingService = rankingService;
        this.pointLedgerService = pointLedgerService;
    }

    @Transactional
    public Map<String, Object> settleCompetition(Long competitionId) {
        Competition competition = getCompetition(competitionId);
        List<Map<String, Object>> rankings = rankingService.getCompetingTeamsSnapshot(competitionId, null);
        Instant settledAt = Instant.now();
        int createdResults = 0;
        int rewardedMembers = 0;
        int totalRewardPoint = 0;

        for (Map<String, Object> ranking : rankings) {
            Long roomId = parseTeamRoomId(String.valueOf(ranking.get("teamId")));
            MatchingRoom room = matchingRoomRepository.findById(roomId)
                    .orElseThrow(() -> new ApiException("대회 팀을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
            int rank = ((Number) ranking.get("rank")).intValue();
            int rewardPoint = rewardPointForRank(rank);

            CompetitionResult result = resultRepository.findByCompetition_IdAndMatchingRoom_Id(competitionId, roomId)
                    .orElseGet(() -> {
                        CompetitionResult created = CompetitionResult.builder()
                                .competition(competition)
                                .matchingRoom(room)
                                .rank(rank)
                                .teamName(String.valueOf(ranking.get("groupName")))
                                .totalValue(toBigDecimal(ranking.get("totalValue")))
                                .investmentAmount(toBigDecimal(ranking.get("investmentAmount")))
                                .profitLoss(toBigDecimal(ranking.get("profitLoss")))
                                .profitLossPercentage(toBigDecimal(ranking.get("profitLossPercentage")))
                                .rewardPoint(rewardPoint)
                                .settledAt(settledAt)
                                .build();
                        return resultRepository.save(created);
                    });
            if (result.getSettledAt().equals(settledAt)) {
                createdResults++;
            }

            if (rewardPoint > 0) {
                for (MatchingRoomMember member : matchingRoomMemberRepository.findByMatchingRoomIdWithUser(roomId)) {
                    User user = member.getUser();
                    if (user == null || user.getId() == null) {
                        continue;
                    }
                    PointTransaction transaction = pointLedgerService.earn(
                            user,
                            rewardPoint,
                            SOURCE_TYPE,
                            sourceId(competitionId, roomId, user.getId()),
                            competition.getName() + " " + rank + "위 보상"
                    );
                    if (transaction != null) {
                        rewardedMembers++;
                        totalRewardPoint += Math.max(transaction.getAmount() != null ? transaction.getAmount() : 0, 0);
                    }
                }
            }

            room.setStatus("ended");
            room.setEndedAt(settledAt);
            matchingRoomRepository.save(room);
        }

        competition.setStatus("ended");
        competitionRepository.save(competition);

        return Map.of(
                "competitionId", competition.getId(),
                "settledTeamCount", rankings.size(),
                "createdResultCount", createdResults,
                "rewardedMemberCount", rewardedMembers,
                "totalRewardPoint", totalRewardPoint
        );
    }

    @Transactional
    public int settleExpiredCompetitions() {
        LocalDateTime now = LocalDateTime.now();
        int settled = 0;
        for (Competition competition : competitionRepository.findAll()) {
            if ("ended".equals(competition.getStatus())) {
                continue;
            }
            LocalDateTime end = parseDateTime(competition.getEndDate());
            if (end != null && !now.isBefore(end)) {
                settleCompetition(competition.getId());
                settled++;
            }
        }
        return settled;
    }

    public List<Map<String, Object>> getMyRecords(User user) {
        if (user == null || user.getId() == null) {
            throw new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED);
        }
        List<Long> myRoomIds = matchingRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(user.getId()).stream()
                .map(MatchingRoomMember::getMatchingRoom)
                .map(MatchingRoom::getId)
                .toList();
        if (myRoomIds.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (CompetitionResult result : resultRepository.findAllByOrderBySettledAtDesc()) {
            MatchingRoom room = result.getMatchingRoom();
            if (room == null || !myRoomIds.contains(room.getId())) {
                continue;
            }
            Competition competition = result.getCompetition();
            Map<String, Object> record = new HashMap<>();
            record.put("competitionId", competition.getId());
            record.put("competitionName", competition.getName());
            record.put("teamId", "team-" + room.getId());
            record.put("teamName", result.getTeamName());
            record.put("rank", result.getRank());
            record.put("totalValue", result.getTotalValue());
            record.put("profitLoss", result.getProfitLoss());
            record.put("profitLossPercentage", result.getProfitLossPercentage());
            record.put("rewardPoint", result.getRewardPoint());
            record.put("settledAt", result.getSettledAt().toString());
            record.put("recordLabel", competition.getName() + " 최종 " + result.getRank() + "위 달성");
            records.add(record);
        }
        records.sort(Comparator.comparing(item -> String.valueOf(item.get("settledAt")), Comparator.reverseOrder()));
        return records;
    }

    public Map<String, Object> getTeamDetail(Long competitionId, String teamId, User currentUser) {
        Long roomId = parseTeamRoomId(teamId);
        MatchingRoom room = matchingRoomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException("대회 팀을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        if (!competitionId.equals(room.getCompetitionId())) {
            throw new ApiException("해당 대회의 팀이 아닙니다.", HttpStatus.NOT_FOUND);
        }

        Map<String, Object> ranking = rankingService.getCompetingTeams(competitionId, currentUser).stream()
                .filter(item -> teamId.equals(String.valueOf(item.get("teamId"))))
                .findFirst()
                .orElseThrow(() -> new ApiException("대회 팀 랭킹을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        List<Map<String, Object>> members = matchingRoomMemberRepository.findByMatchingRoomIdWithUser(roomId).stream()
                .map(member -> {
                    User user = member.getUser();
                    Map<String, Object> item = new HashMap<>();
                    item.put("userId", user != null ? user.getId() : null);
                    item.put("nickname", user != null ? user.getNickname() : "팀원");
                    item.put("isMe", currentUser != null && user != null && currentUser.getId().equals(user.getId()));
                    return item;
                })
                .toList();

        Map<String, Object> detail = new HashMap<>(ranking);
        detail.put("competitionId", competitionId);
        detail.put("members", members);
        return detail;
    }

    private Competition getCompetition(Long competitionId) {
        return competitionRepository.findById(competitionId)
                .orElseThrow(() -> new ApiException("대회를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }

    private Long parseTeamRoomId(String teamId) {
        if (teamId == null || !teamId.startsWith("team-")) {
            throw new ApiException("팀 ID가 올바르지 않습니다.", HttpStatus.BAD_REQUEST);
        }
        try {
            return Long.parseLong(teamId.substring("team-".length()));
        } catch (NumberFormatException e) {
            throw new ApiException("팀 ID가 올바르지 않습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private int rewardPointForRank(int rank) {
        return switch (rank) {
            case 1 -> 3000;
            case 2 -> 2000;
            case 3 -> 1000;
            default -> 0;
        };
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private String sourceId(Long competitionId, Long roomId, Long userId) {
        return "competition-" + competitionId + "-team-" + roomId + "-user-" + userId;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
