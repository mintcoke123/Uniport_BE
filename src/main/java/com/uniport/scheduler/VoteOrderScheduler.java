package com.uniport.scheduler;

import com.uniport.service.VoteService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class VoteOrderScheduler {

    private final VoteService voteService;

    public VoteOrderScheduler(VoteService voteService) {
        this.voteService = voteService;
    }

    /** 10초마다: 표결 만료(ongoing → expired) + pending 체결/만료 */
    @Scheduled(fixedDelay = 10000)
    public void run() {
        voteService.processExpiredOngoingVotes();
        voteService.processPendingVotes();
    }
}
