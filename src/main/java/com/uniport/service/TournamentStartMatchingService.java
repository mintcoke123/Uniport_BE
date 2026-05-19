package com.uniport.service;

import com.uniport.entity.Competition;
import com.uniport.entity.CompetitionApplication;
import com.uniport.entity.User;
import com.uniport.repository.CompetitionApplicationRepository;
import com.uniport.repository.CompetitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class TournamentStartMatchingService {

    private static final Logger log = LoggerFactory.getLogger(TournamentStartMatchingService.class);

    private final CompetitionRepository competitionRepository;
    private final CompetitionApplicationRepository applicationRepository;
    private final CompetitionService competitionService;
    private final MatchingRoomService matchingRoomService;
    private final PushNotificationService pushNotificationService;
    private final Clock clock;

    @Autowired
    public TournamentStartMatchingService(CompetitionRepository competitionRepository,
                                          CompetitionApplicationRepository applicationRepository,
                                          CompetitionService competitionService,
                                          MatchingRoomService matchingRoomService,
                                          PushNotificationService pushNotificationService) {
        this(
                competitionRepository,
                applicationRepository,
                competitionService,
                matchingRoomService,
                pushNotificationService,
                Clock.system(CompetitionService.COMPETITION_ZONE)
        );
    }

    TournamentStartMatchingService(CompetitionRepository competitionRepository,
                                   CompetitionApplicationRepository applicationRepository,
                                   CompetitionService competitionService,
                                   MatchingRoomService matchingRoomService,
                                   PushNotificationService pushNotificationService,
                                   Clock clock) {
        this.competitionRepository = competitionRepository;
        this.applicationRepository = applicationRepository;
        this.competitionService = competitionService;
        this.matchingRoomService = matchingRoomService;
        this.pushNotificationService = pushNotificationService;
        this.clock = clock;
    }

    @Transactional
    public int processDueTournaments() {
        int completedCount = 0;
        for (Competition competition : competitionRepository.findAll()) {
            if (!shouldProcess(competition)) {
                continue;
            }
            if (processCompetition(competition)) {
                completedCount++;
            }
        }
        return completedCount;
    }

    private boolean shouldProcess(Competition competition) {
        if (competition == null || competition.getId() == null) {
            return false;
        }
        if (!"ongoing".equals(competitionService.resolveStatus(competition))) {
            return false;
        }
        String status = normalizeMatchingStatus(competition.getMatchingStatus());
        return !Competition.MATCHING_STATUS_COMPLETED.equals(status)
                && !Competition.MATCHING_STATUS_PROCESSING.equals(status);
    }

    private boolean processCompetition(Competition competition) {
        try {
            competition.setMatchingStatus(Competition.MATCHING_STATUS_PROCESSING);
            competition.setMatchingStartedAt(Instant.now(clock));
            competition.setMatchingErrorMessage(null);
            competitionRepository.save(competition);

            List<User> applicants = applicationRepository.findByCompetition_IdAndStatus(competition.getId(), "APPLIED")
                    .stream()
                    .map(CompetitionApplication::getUser)
                    .filter(Objects::nonNull)
                    .filter(user -> user.getId() != null)
                    .distinct()
                    .toList();

            matchingRoomService.createStartedTournamentRooms(competition, applicants);

            List<Long> recipientUserIds = applicants.stream()
                    .map(User::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!recipientUserIds.isEmpty()) {
                pushNotificationService.sendTournamentStarted(competition, recipientUserIds);
                competition.setStartNotificationSentAt(Instant.now(clock));
            }
            competition.setMatchingStatus(Competition.MATCHING_STATUS_COMPLETED);
            competition.setMatchingCompletedAt(Instant.now(clock));
            competition.setMatchingErrorMessage(null);
            competitionRepository.save(competition);
            return true;
        } catch (RuntimeException e) {
            competition.setMatchingStatus(Competition.MATCHING_STATUS_FAILED);
            competition.setMatchingErrorMessage(truncate(e.getMessage()));
            competitionRepository.save(competition);
            log.warn("[tournament-start-matching] failed competitionId={} message={}", competition.getId(), e.getMessage());
            return false;
        }
    }

    private static String normalizeMatchingStatus(String status) {
        if (status == null || status.isBlank()) {
            return Competition.MATCHING_STATUS_PENDING;
        }
        return status.trim().toUpperCase();
    }

    private static String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown tournament matching failure";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
