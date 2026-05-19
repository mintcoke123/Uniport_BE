package com.uniport.service;

import com.uniport.entity.Competition;
import com.uniport.entity.User;
import com.uniport.repository.MatchingRoomMemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TournamentHomeService {

    private final CompetitionService competitionService;
    private final CompetitionParticipationService competitionParticipationService;
    private final MatchingRoomMemberRepository matchingRoomMemberRepository;
    private final Clock clock;

    @Autowired
    public TournamentHomeService(CompetitionService competitionService,
                                 CompetitionParticipationService competitionParticipationService,
                                 MatchingRoomMemberRepository matchingRoomMemberRepository) {
        this(
                competitionService,
                competitionParticipationService,
                matchingRoomMemberRepository,
                Clock.system(CompetitionService.COMPETITION_ZONE)
        );
    }

    TournamentHomeService(CompetitionService competitionService,
                          CompetitionParticipationService competitionParticipationService,
                          MatchingRoomMemberRepository matchingRoomMemberRepository,
                          Clock clock) {
        this.competitionService = competitionService;
        this.competitionParticipationService = competitionParticipationService;
        this.matchingRoomMemberRepository = matchingRoomMemberRepository;
        this.clock = clock;
    }

    public Map<String, Object> getHome(User user) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("serverTime", OffsetDateTime.now(clock).toString());
        response.put("upcomingTournaments", upcomingTournaments(user));
        Map<String, Object> myOngoingTournament = myOngoingTournament(user);
        if (myOngoingTournament != null) {
            response.put("myOngoingTournament", myOngoingTournament);
        }
        return response;
    }

    private List<Map<String, Object>> upcomingTournaments(User user) {
        String participantTeamId = user != null ? user.getTeamId() : null;
        return competitionService.findByStatus("upcoming").stream()
                .map(competition -> {
                    Map<String, Object> item = baseTournamentMap(competition, "upcoming");
                    item.put(
                            "application",
                            competitionParticipationService.getApplicationStatus(
                                    competition.getId(),
                                    user,
                                    participantTeamId
                            )
                    );
                    return item;
                })
                .toList();
    }

    private Map<String, Object> myOngoingTournament(User user) {
        if (user == null || user.getId() == null) {
            return null;
        }
        return competitionService.findByStatus("ongoing").stream()
                .filter(competition -> Competition.MATCHING_STATUS_COMPLETED.equals(competition.getMatchingStatus()))
                .filter(competition -> matchingRoomMemberRepository.existsByUserIdAndMatchingRoom_CompetitionIdAndMatchingRoom_Status(
                        user.getId(),
                        competition.getId(),
                        "started"
                ))
                .findFirst()
                .map(competition -> {
                    Map<String, Object> item = baseTournamentMap(competition, "ongoing");
                    item.put("rankingAvailable", true);
                    return item;
                })
                .orElse(null);
    }

    private Map<String, Object> baseTournamentMap(Competition competition, String effectiveStatus) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("competitionId", competition.getId());
        item.put("name", competition.getName());
        item.put("startDate", competition.getStartDate());
        item.put("endDate", competition.getEndDate());
        item.put("effectiveStatus", effectiveStatus);
        return item;
    }
}
