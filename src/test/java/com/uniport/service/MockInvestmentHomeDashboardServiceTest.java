package com.uniport.service;

import com.uniport.dto.MockInvestmentHomeResponseDTO;
import com.uniport.dto.MockInvestmentLeaderboardItemDTO;
import com.uniport.dto.MockInvestmentTopGroupInsightItemDTO;
import com.uniport.entity.MatchingRoom;
import com.uniport.entity.TeamGameSnapshot;
import com.uniport.entity.User;
import com.uniport.entity.Vote;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import com.uniport.repository.TeamGameSnapshotRepository;
import com.uniport.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MockInvestmentHomeDashboardServiceTest {

    @Mock
    private RankingService rankingService;
    @Mock
    private CompetitionService competitionService;
    @Mock
    private MatchingRoomRepository matchingRoomRepository;
    @Mock
    private MatchingRoomMemberRepository matchingRoomMemberRepository;
    @Mock
    private VoteRepository voteRepository;
    @Mock
    private TeamGameSnapshotRepository teamGameSnapshotRepository;

    private MockInvestmentHomeDashboardService service;

    @BeforeEach
    void setUp() {
        service = new MockInvestmentHomeDashboardService(
                rankingService,
                competitionService,
                matchingRoomRepository,
                matchingRoomMemberRepository,
                voteRepository,
                teamGameSnapshotRepository
        );
    }

    @Test
    void notParticipatingUserGetsParticipationLabelAndStartCta() {
        User user = user(1L, null);
        when(matchingRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(1L)).thenReturn(List.of());
        when(rankingService.getActiveTeamGameLeaderboard(anyInt())).thenReturn(List.of());
        when(teamGameSnapshotRepository.findBySnapshotDateForRanking(any(LocalDate.class))).thenReturn(List.of());
        when(voteRepository.findByCreatedAtAfterOrderByCreatedAtDesc(any(Instant.class))).thenReturn(List.of());
        when(competitionService.findOngoing()).thenReturn(Optional.empty());

        MockInvestmentHomeResponseDTO response = service.getHome(user, "unexpected");

        assertThat(response.getMode()).isEqualTo("ALWAYS_ON");
        assertThat(response.getHeroStatus().getRankLabel()).isEqualTo("참여 전");
        assertThat(response.getHeroStatus().getStatus()).isEqualTo("not_participating");
        assertThat(response.getHeroStatus().getRemainingSeconds()).isNull();
        assertThat(response.getHeroStatus().getCta().getLabel()).isEqualTo("모의투자 시작하기");
        assertThat(response.getHeroStatus().getCta().getAction()).isEqualTo("START_MOCK_INVESTMENT");
        assertThat(response.getHeroStatus().getCta().isEnabled()).isTrue();
    }

    @Test
    void activeStartedRoomGetsRankEndsAtRemainingSecondsAndPortfolioCta() {
        User user = user(2L, "team-7");
        MatchingRoom room = room(7L, "미래투자팀", "started", Instant.now().minusSeconds(600), Instant.now().plusSeconds(3600));
        when(matchingRoomRepository.findById(7L)).thenReturn(Optional.of(room));
        when(rankingService.getActiveTeamGameLeaderboard(anyInt())).thenReturn(List.of(
                leaderboardItem(11L, 1, "앞선팀"),
                leaderboardItem(7L, 2, "미래투자팀")
        ));
        when(teamGameSnapshotRepository.findBySnapshotDateForRanking(any(LocalDate.class))).thenReturn(List.of());
        when(voteRepository.findByCreatedAtAfterOrderByCreatedAtDesc(any(Instant.class))).thenReturn(List.of());
        when(competitionService.findOngoing()).thenReturn(Optional.empty());

        MockInvestmentHomeResponseDTO response = service.getHome(user, "TOURNAMENT");

        assertThat(response.getMode()).isEqualTo("TOURNAMENT");
        assertThat(response.getHeroStatus().getTeamId()).isEqualTo(7L);
        assertThat(response.getHeroStatus().getTeamName()).isEqualTo("미래투자팀");
        assertThat(response.getHeroStatus().getRank()).isEqualTo(2);
        assertThat(response.getHeroStatus().getRankLabel()).isEqualTo("2위");
        assertThat(response.getHeroStatus().getTotalParticipants()).isEqualTo(2);
        assertThat(response.getHeroStatus().getStatus()).isEqualTo("active");
        assertThat(response.getHeroStatus().getEndsAt()).isNotBlank();
        assertThat(response.getHeroStatus().getRemainingSeconds()).isBetween(1L, 3600L);
        assertThat(response.getHeroStatus().getCta().getLabel()).isEqualTo("내 포트폴리오 보기");
        assertThat(response.getHeroStatus().getCta().getAction()).isEqualTo("VIEW_MOCK_PORTFOLIO");
        assertThat(response.getHeroStatus().getCta().isEnabled()).isTrue();
    }

    @Test
    void startedRoomPastEndedAtGetsSettlingStatusAndDisabledResultCta() {
        User user = user(5L, "team-17");
        MatchingRoom room = room(17L, "정산대기팀", "started", Instant.now().minusSeconds(7200), Instant.now().minusSeconds(60));
        when(matchingRoomRepository.findById(17L)).thenReturn(Optional.of(room));
        when(rankingService.getActiveTeamGameLeaderboard(anyInt())).thenReturn(List.of(
                leaderboardItem(17L, 4, "정산대기팀")
        ));
        when(teamGameSnapshotRepository.findBySnapshotDateForRanking(any(LocalDate.class))).thenReturn(List.of());
        when(voteRepository.findByCreatedAtAfterOrderByCreatedAtDesc(any(Instant.class))).thenReturn(List.of());
        when(competitionService.findOngoing()).thenReturn(Optional.empty());

        MockInvestmentHomeResponseDTO response = service.getHome(user, "ALWAYS_ON");

        assertThat(response.getHeroStatus().getStatus()).isEqualTo("settling");
        assertThat(response.getHeroStatus().getRank()).isEqualTo(4);
        assertThat(response.getHeroStatus().getRankLabel()).isEqualTo("4위");
        assertThat(response.getHeroStatus().getRemainingSeconds()).isNull();
        assertThat(response.getHeroStatus().getCta().getLabel()).isEqualTo("결과 보기");
        assertThat(response.getHeroStatus().getCta().getAction()).isEqualTo("VIEW_RESULT");
        assertThat(response.getHeroStatus().getCta().isEnabled()).isFalse();
    }

    @Test
    void endedRoomGetsEndedStatusAndEnabledResultCta() {
        User user = user(6L, "team-23");
        MatchingRoom room = room(23L, "종료팀", "ended", Instant.now().minusSeconds(10800), Instant.now().minusSeconds(3600));
        when(matchingRoomRepository.findById(23L)).thenReturn(Optional.of(room));
        when(matchingRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(6L)).thenReturn(List.of());
        when(rankingService.getActiveTeamGameLeaderboard(anyInt())).thenReturn(List.of());
        when(teamGameSnapshotRepository.findBySnapshotDateForRanking(any(LocalDate.class))).thenReturn(List.of());
        when(voteRepository.findByCreatedAtAfterOrderByCreatedAtDesc(any(Instant.class))).thenReturn(List.of());
        when(competitionService.findOngoing()).thenReturn(Optional.empty());

        MockInvestmentHomeResponseDTO response = service.getHome(user, "ALWAYS_ON");

        assertThat(response.getHeroStatus().getStatus()).isEqualTo("ended");
        assertThat(response.getHeroStatus().getRankLabel()).isEqualTo("집계 중");
        assertThat(response.getHeroStatus().getRemainingSeconds()).isNull();
        assertThat(response.getHeroStatus().getCta().getLabel()).isEqualTo("결과 보기");
        assertThat(response.getHeroStatus().getCta().getAction()).isEqualTo("VIEW_RESULT");
        assertThat(response.getHeroStatus().getCta().isEnabled()).isTrue();
    }

    @Test
    void topThreeInsightsAreUnlockedAndRanksFourToTwentyAreLocked() {
        User user = user(3L, null);
        when(matchingRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(3L)).thenReturn(List.of());
        when(rankingService.getActiveTeamGameLeaderboard(anyInt())).thenReturn(List.of());
        when(teamGameSnapshotRepository.findBySnapshotDateForRanking(any(LocalDate.class))).thenReturn(List.of(
                snapshot(1L, "알파팀", "12.3000"),
                snapshot(2L, "브라보팀", "10.1000"),
                snapshot(3L, "찰리팀", "8.5000"),
                snapshot(4L, "델타팀", "7.7000")
        ));
        when(voteRepository.findTop20ByRoomIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(
                vote(1L, "매수", "삼성전자", "005930", "실적 회복 기대", Instant.now())
        ));
        when(voteRepository.findTop20ByRoomIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(
                vote(2L, "매도", "SK하이닉스", "000660", "단기 급등 부담", Instant.now())
        ));
        when(voteRepository.findTop20ByRoomIdOrderByCreatedAtDesc(3L)).thenReturn(List.of(
                vote(3L, "매수", "네이버", "035420", "AI 성장성", Instant.now()),
                vote(3L, "매도", "네이버", "035420", "차익 실현", Instant.now().minusSeconds(60))
        ));
        when(voteRepository.findByCreatedAtAfterOrderByCreatedAtDesc(any(Instant.class))).thenReturn(List.of());
        when(competitionService.findOngoing()).thenReturn(Optional.empty());

        MockInvestmentHomeResponseDTO response = service.getHome(user, "ALWAYS_ON");

        assertThat(response.getTopGroupInsights().getRankingBasis()).isEqualTo("YESTERDAY_RETURN_RATE");
        assertThat(response.getTopGroupInsights().getTotalCount()).isEqualTo(4);
        assertThat(response.getTopGroupInsights().getFreeCount()).isEqualTo(3);
        assertThat(response.getTopGroupInsights().getLockedCount()).isEqualTo(1);
        assertThat(response.getTopGroupInsights().getItems()).hasSize(4);
        assertThat(response.getTopGroupInsights().getItems().subList(0, 3))
                .extracting(MockInvestmentTopGroupInsightItemDTO::isLocked)
                .containsExactly(false, false, false);
        MockInvestmentTopGroupInsightItemDTO locked = response.getTopGroupInsights().getItems().get(3);
        assertThat(locked.isLocked()).isTrue();
        assertThat(locked.getLockedTitle()).isEqualTo("전일 수익률 4위 팀 인사이트");
        assertThat(locked.getLockedDescription()).isEqualTo("프리미엄에서 매수/매도 근거를 확인할 수 있어요.");
        assertThat(response.getTopGroupInsights().getItems().get(0).getBuyReason()).isEqualTo("실적 회복 기대");
        assertThat(response.getTopGroupInsights().getItems().get(1).getSellReason()).isEqualTo("단기 급등 부담");
        assertThat(response.getTopGroupInsights().getItems().get(2).getAction()).isEqualTo("SELL");
    }

    @Test
    void collectiveSignalUsesRecentVotesAndMinimumCount() {
        User user = user(4L, null);
        when(matchingRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(4L)).thenReturn(List.of());
        when(rankingService.getActiveTeamGameLeaderboard(anyInt())).thenReturn(List.of());
        when(teamGameSnapshotRepository.findBySnapshotDateForRanking(any(LocalDate.class))).thenReturn(List.of());
        when(voteRepository.findByCreatedAtAfterOrderByCreatedAtDesc(any(Instant.class))).thenReturn(List.of(
                votes(12, 10L, "매수", "삼성전자", "005930", "실적 개선 기대", Instant.now()),
                votes(3, 30L, "매도", "삼성전자", "005930", "차익 실현", Instant.now().minusSeconds(30)),
                votes(9, 50L, "매수", "네이버", "035420", "AI 성장성", Instant.now().minusSeconds(60))
        ).stream().flatMap(List::stream).toList());
        when(competitionService.findOngoing()).thenReturn(Optional.empty());

        MockInvestmentHomeResponseDTO response = service.getHome(user, "ALWAYS_ON");

        assertThat(response.getCollectiveSignal()).isNotNull();
        assertThat(response.getCollectiveSignal().getStockName()).isEqualTo("삼성전자");
        assertThat(response.getCollectiveSignal().getTicker()).isEqualTo("005930");
        assertThat(response.getCollectiveSignal().getAction()).isEqualTo("BUY");
        assertThat(response.getCollectiveSignal().getParticipantCount()).isEqualTo(12);
        assertThat(response.getCollectiveSignal().getConsensusRate()).isEqualTo(80);
        assertThat(response.getCollectiveSignal().getSummary()).isEqualTo("실적 개선 기대 의견이 높아요.");
    }

    private static User user(Long id, String teamId) {
        return User.builder()
                .id(id)
                .studentId("S" + id)
                .password("password")
                .nickname("user" + id)
                .teamId(teamId)
                .build();
    }

    private static MatchingRoom room(Long id, String name, String status, Instant createdAt, Instant endedAt) {
        return MatchingRoom.builder()
                .id(id)
                .name(name)
                .memberCount(3)
                .status(status)
                .createdAt(createdAt)
                .endedAt(endedAt)
                .build();
    }

    private static MockInvestmentLeaderboardItemDTO leaderboardItem(Long groupId, Integer rank, String groupName) {
        return MockInvestmentLeaderboardItemDTO.builder()
                .rank(rank)
                .groupId(groupId)
                .groupName(groupName)
                .teamGameId("team_game_" + groupId)
                .startedAt("2026-05-19T00:00:00+09:00")
                .endsAt("2026-05-20T00:00:00+09:00")
                .totalAssetAmount(new BigDecimal("11000000.0000"))
                .returnRate(new BigDecimal("10.0000"))
                .build();
    }

    private static TeamGameSnapshot snapshot(Long teamId, String teamName, String returnRate) {
        Instant startedAt = Instant.parse("2026-05-18T00:00:00Z");
        return TeamGameSnapshot.builder()
                .teamId(teamId)
                .teamName(teamName)
                .teamGameId("team_game_" + teamId)
                .memberCount(3)
                .startedAt(startedAt)
                .endsAt(startedAt.plusSeconds(86400))
                .totalAssetAmount(new BigDecimal("11000000.0000").add(BigDecimal.valueOf(teamId)))
                .returnRate(new BigDecimal(returnRate))
                .snapshotAt(startedAt.plusSeconds(86399))
                .snapshotDate(LocalDate.of(2026, 5, 18))
                .build();
    }

    private static Vote vote(Long roomId, String type, String stockName, String stockCode, String reason, Instant createdAt) {
        return Vote.builder()
                .roomId(roomId)
                .proposerId(100L + roomId)
                .proposerName("proposer")
                .type(type)
                .stockName(stockName)
                .stockCode(stockCode)
                .quantity(1)
                .proposedPrice(BigDecimal.TEN)
                .reason(reason)
                .createdAt(createdAt)
                .expiresAt(createdAt.plusSeconds(600))
                .totalMembers(3)
                .build();
    }

    private static List<Vote> votes(int count,
                                    Long roomId,
                                    String type,
                                    String stockName,
                                    String stockCode,
                                    String reason,
                                    Instant createdAt) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> vote(roomId + i, type, stockName, stockCode, reason, createdAt.minusSeconds(i)))
                .toList();
    }
}
