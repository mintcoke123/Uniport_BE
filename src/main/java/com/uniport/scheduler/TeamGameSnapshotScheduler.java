package com.uniport.scheduler;

import com.uniport.entity.MatchingRoom;
import com.uniport.entity.TeamGameSnapshot;
import com.uniport.repository.MatchingRoomRepository;
import com.uniport.repository.TeamGameSnapshotRepository;
import com.uniport.service.RankingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
public class TeamGameSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(TeamGameSnapshotScheduler.class);
    private static final String STARTED_STATUS = "started";
    private static final ZoneId SNAPSHOT_ZONE = ZoneId.of("Asia/Seoul");

    private final MatchingRoomRepository matchingRoomRepository;
    private final RankingService rankingService;
    private final TeamGameSnapshotRepository teamGameSnapshotRepository;
    private final Clock clock;

    @Autowired
    public TeamGameSnapshotScheduler(MatchingRoomRepository matchingRoomRepository,
                                     RankingService rankingService,
                                     TeamGameSnapshotRepository teamGameSnapshotRepository) {
        this(matchingRoomRepository, rankingService, teamGameSnapshotRepository, Clock.systemUTC());
    }

    TeamGameSnapshotScheduler(MatchingRoomRepository matchingRoomRepository,
                              RankingService rankingService,
                              TeamGameSnapshotRepository teamGameSnapshotRepository,
                              Clock clock) {
        this.matchingRoomRepository = matchingRoomRepository;
        this.rankingService = rankingService;
        this.teamGameSnapshotRepository = teamGameSnapshotRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "${mock-investment.snapshot.cron:0 0 * * * *}", zone = "${mock-investment.snapshot.zone:Asia/Seoul}")
    public void run() {
        Instant snapshotAt = Instant.now(clock);
        LocalDate snapshotDate = snapshotAt.atZone(SNAPSHOT_ZONE).toLocalDate();
        List<MatchingRoom> rooms = matchingRoomRepository.findByStatusOrderByCreatedAtDesc(STARTED_STATUS);

        for (MatchingRoom room : rooms) {
            saveSnapshot(room, snapshotAt, snapshotDate);
        }
    }

    private void saveSnapshot(MatchingRoom room, Instant snapshotAt, LocalDate snapshotDate) {
        Long roomId = room.getId();
        if (room.getCreatedAt() == null || room.getEndedAt() == null) {
            log.warn(
                    "Skipping team game snapshot for roomId={} because createdAt or endedAt is null",
                    roomId
            );
            return;
        }

        try {
            RankingService.TeamValuation valuation = rankingService.evaluateTeam(roomId, false);
            teamGameSnapshotRepository.save(TeamGameSnapshot.builder()
                    .teamId(roomId)
                    .teamName(room.getName() != null ? room.getName() : "팀 " + roomId)
                    .teamGameId("team_game_" + roomId)
                    .memberCount(room.getMemberCount())
                    .startedAt(room.getCreatedAt())
                    .endsAt(room.getEndedAt())
                    .totalAssetAmount(valuation.totalValue())
                    .returnRate(valuation.returnRatePercent())
                    .snapshotAt(snapshotAt)
                    .snapshotDate(snapshotDate)
                    .build());
        } catch (RuntimeException e) {
            log.warn(
                    "Skipping team game snapshot for roomId={} because snapshot processing failed: {}",
                    roomId,
                    e.getMessage(),
                    e
            );
        }
    }
}
