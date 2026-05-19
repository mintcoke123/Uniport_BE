package com.uniport.scheduler;

import com.uniport.entity.MatchingRoom;
import com.uniport.repository.MatchingRoomRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class MatchingRoomEndingScheduler {

    private static final String STARTED_STATUS = "started";
    private static final String ENDED_STATUS = "ended";
    private static final Duration SESSION_DURATION = Duration.ofDays(7);

    private final MatchingRoomRepository matchingRoomRepository;

    public MatchingRoomEndingScheduler(MatchingRoomRepository matchingRoomRepository) {
        this.matchingRoomRepository = matchingRoomRepository;
    }

    @Scheduled(fixedDelayString = "${mock-investment.ending.fixed-delay-ms:60000}")
    @Transactional
    public void run() {
        Instant now = Instant.now();
        List<MatchingRoom> rooms = matchingRoomRepository
                .findByStatusAndEndedAtLessThanEqualOrderByEndedAtAsc(STARTED_STATUS, now);
        for (MatchingRoom room : rooms) {
            room.setStatus(ENDED_STATUS);
            matchingRoomRepository.save(room);
        }

        List<MatchingRoom> legacyRooms = matchingRoomRepository
                .findByStatusAndEndedAtIsNullAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
                        STARTED_STATUS,
                        now.minus(SESSION_DURATION)
                );
        for (MatchingRoom room : legacyRooms) {
            room.setEndedAt(room.getCreatedAt().plus(SESSION_DURATION));
            room.setStatus(ENDED_STATUS);
            matchingRoomRepository.save(room);
        }
    }
}
