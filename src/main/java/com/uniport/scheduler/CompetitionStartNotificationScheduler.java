package com.uniport.scheduler;

import com.uniport.service.CompetitionStartNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CompetitionStartNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(CompetitionStartNotificationScheduler.class);

    private final CompetitionStartNotificationService notificationService;

    public CompetitionStartNotificationScheduler(CompetitionStartNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelayString = "${competition.start-notification.job.fixed-delay-ms:60000}")
    public void run() {
        try {
            int sent = notificationService.sendDueStartNotifications();
            if (sent > 0) {
                log.info("[competition-start-notification] sent={}", sent);
            }
        } catch (Exception e) {
            log.warn("[competition-start-notification] job failed: {}", e.getMessage());
        }
    }
}
