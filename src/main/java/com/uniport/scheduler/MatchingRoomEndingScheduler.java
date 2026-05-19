package com.uniport.scheduler;

import com.uniport.entity.MatchingRoom;
import com.uniport.repository.MatchingRoomRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class MatchingRoomEndingScheduler {

    private final MatchingRoomRepository matchingRoomRepository;

    public MatchingRoomEndingScheduler(MatchingRoomRepository matchingRoomRepository) {
        this.matchingRoomRepository = matchingRoomRepository;
    }

    @Scheduled(fixedDelayString = "${mock-investment.ending.fixed-delay-ms:60000}")
    @Transactional
    public void run() {
        List<MatchingRoom> rooms = matchingRoomRepository
                .findByStatusAndEndedAtLessThanEqualOrderByEndedAtAsc("started", Instant.now());
        for (MatchingRoom room : rooms) {
            room.setStatus("ended");
            matchingRoomRepository.save(room);
        }
    }
}
