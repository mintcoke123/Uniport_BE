package com.uniport.service;

import com.uniport.entity.Competition;
import com.uniport.entity.CompetitionApplication;
import com.uniport.entity.User;
import com.uniport.repository.CompetitionApplicationRepository;
import com.uniport.repository.CompetitionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class CompetitionStartNotificationService {

    private final CompetitionRepository competitionRepository;
    private final CompetitionApplicationRepository applicationRepository;
    private final CompetitionService competitionService;
    private final PushNotificationService pushNotificationService;
    private final Clock clock;

    @Autowired
    public CompetitionStartNotificationService(CompetitionRepository competitionRepository,
                                               CompetitionApplicationRepository applicationRepository,
                                               CompetitionService competitionService,
                                               PushNotificationService pushNotificationService) {
        this(
                competitionRepository,
                applicationRepository,
                competitionService,
                pushNotificationService,
                Clock.system(CompetitionService.COMPETITION_ZONE)
        );
    }

    CompetitionStartNotificationService(CompetitionRepository competitionRepository,
                                        CompetitionApplicationRepository applicationRepository,
                                        CompetitionService competitionService,
                                        PushNotificationService pushNotificationService,
                                        Clock clock) {
        this.competitionRepository = competitionRepository;
        this.applicationRepository = applicationRepository;
        this.competitionService = competitionService;
        this.pushNotificationService = pushNotificationService;
        this.clock = clock;
    }

    @Transactional
    public int sendDueStartNotifications() {
        int processedCount = 0;
        List<Competition> competitions = competitionRepository.findAll();
        for (Competition competition : competitions) {
            if (competition == null || competition.getId() == null || competition.getStartNotificationSentAt() != null) {
                continue;
            }
            if (!"ongoing".equals(competitionService.resolveStatus(competition))) {
                continue;
            }

            List<Long> recipientUserIds = applicationRepository.findByCompetition_IdAndStatus(competition.getId(), "APPLIED")
                    .stream()
                    .map(CompetitionApplication::getUser)
                    .filter(Objects::nonNull)
                    .map(User::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            pushNotificationService.sendTournamentStarted(competition, recipientUserIds);
            competition.setStartNotificationSentAt(Instant.now(clock));
            competitionRepository.save(competition);
            processedCount++;
        }
        return processedCount;
    }
}
