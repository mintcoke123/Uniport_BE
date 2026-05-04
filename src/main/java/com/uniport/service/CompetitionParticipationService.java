package com.uniport.service;

import com.uniport.entity.Competition;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.CompetitionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CompetitionParticipationService {

    private final CompetitionRepository competitionRepository;
    private final Map<Long, LinkedHashMap<String, CompetitionApplication>> applicationsByCompetitionId = new ConcurrentHashMap<>();

    public CompetitionParticipationService(CompetitionRepository competitionRepository) {
        this.competitionRepository = competitionRepository;
    }

    public Map<String, Object> apply(Long competitionId, User user, String participantTeamId, String participantName) {
        Competition competition = getCompetition(competitionId);
        if (user == null || user.getId() == null) {
            throw new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED);
        }

        LinkedHashMap<String, CompetitionApplication> applications =
                applicationsByCompetitionId.computeIfAbsent(competitionId, ignored -> new LinkedHashMap<>());

        String teamId = participantTeamId != null && !participantTeamId.isBlank()
                ? participantTeamId.trim()
                : fallbackParticipantTeamId(user);
        String teamName = participantName != null && !participantName.isBlank()
                ? participantName.trim()
                : fallbackParticipantName(user);

        CompetitionApplication application = new CompetitionApplication(
                competitionId,
                user.getId(),
                teamId,
                teamName,
                "APPLIED",
                Instant.now()
        );
        applications.put(teamId, application);

        return Map.of(
                "competitionId", competition.getId(),
                "teamId", teamId,
                "teamName", teamName,
                "status", application.status(),
                "statusLabel", "신청 완료",
                "appliedAt", application.appliedAt().toString(),
                "message", competition.getName() + " 참가 신청이 완료되었어요."
        );
    }

    public Map<String, Object> cancel(Long competitionId, User user, String participantTeamId) {
        Competition competition = getCompetition(competitionId);
        if (user == null || user.getId() == null) {
            throw new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED);
        }

        LinkedHashMap<String, CompetitionApplication> applications = applicationsByCompetitionId.get(competitionId);
        if (applications == null || applications.isEmpty()) {
            throw new ApiException("참가 신청 내역이 없습니다.", HttpStatus.NOT_FOUND);
        }

        String teamId = participantTeamId != null && !participantTeamId.isBlank()
                ? participantTeamId.trim()
                : fallbackParticipantTeamId(user);
        CompetitionApplication removed = applications.remove(teamId);
        if (removed == null) {
            throw new ApiException("참가 신청 내역이 없습니다.", HttpStatus.NOT_FOUND);
        }

        return Map.of(
                "competitionId", competition.getId(),
                "teamId", teamId,
                "status", "CANCELLED",
                "statusLabel", "신청 취소",
                "message", competition.getName() + " 참가 신청을 취소했어요."
        );
    }

    public Map<String, Object> getApplicationStatus(Long competitionId, User user, String participantTeamId) {
        Competition competition = getCompetition(competitionId);
        String teamId = participantTeamId != null && !participantTeamId.isBlank()
                ? participantTeamId.trim()
                : fallbackParticipantTeamId(user);
        CompetitionApplication application = applicationsByCompetitionId
                .getOrDefault(competitionId, new LinkedHashMap<>())
                .get(teamId);

        if (application == null) {
            return Map.of(
                    "competitionId", competition.getId(),
                    "teamId", teamId,
                    "applied", false,
                    "status", "NOT_APPLIED",
                    "statusLabel", "참가 신청",
                    "message", "아직 참가 신청하지 않았어요."
            );
        }

        return Map.of(
                "competitionId", competition.getId(),
                "teamId", application.teamId(),
                "teamName", application.teamName(),
                "applied", true,
                "status", application.status(),
                "statusLabel", "신청 완료",
                "appliedAt", application.appliedAt().toString(),
                "message", competition.getName() + " 참가 신청이 완료되었어요."
        );
    }

    public List<Map<String, Object>> getMyApplications(User user) {
        String fallbackTeamId = fallbackParticipantTeamId(user);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map.Entry<Long, LinkedHashMap<String, CompetitionApplication>> entry : applicationsByCompetitionId.entrySet()) {
            CompetitionApplication application = entry.getValue().get(fallbackTeamId);
            if (application == null) {
                continue;
            }

            Competition competition = competitionRepository.findById(entry.getKey()).orElse(null);
            if (competition == null) {
                continue;
            }

            result.add(Map.of(
                    "competitionId", competition.getId(),
                    "competitionName", competition.getName(),
                    "teamId", application.teamId(),
                    "teamName", application.teamName(),
                    "status", application.status(),
                    "statusLabel", "신청 완료",
                    "appliedAt", application.appliedAt().toString(),
                    "startDate", competition.getStartDate(),
                    "endDate", competition.getEndDate()
            ));
        }

        result.sort(Comparator.comparing(item -> String.valueOf(item.get("appliedAt")), Comparator.reverseOrder()));
        return result;
    }

    public boolean isApplied(Long competitionId, String participantTeamId) {
        return applicationsByCompetitionId
                .getOrDefault(competitionId, new LinkedHashMap<>())
                .containsKey(participantTeamId);
    }

    private Competition getCompetition(Long competitionId) {
        return competitionRepository.findById(competitionId)
                .orElseThrow(() -> new ApiException("대회를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }

    private String fallbackParticipantTeamId(User user) {
        if (user != null && user.getTeamId() != null && !user.getTeamId().isBlank()) {
            return user.getTeamId();
        }
        if (user != null && user.getId() != null) {
            return "solo-" + user.getId();
        }
        return "solo-anonymous";
    }

    private String fallbackParticipantName(User user) {
        if (user != null && user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname() + " 팀";
        }
        return "개인 참가";
    }

    private record CompetitionApplication(
            Long competitionId,
            Long userId,
            String teamId,
            String teamName,
            String status,
            Instant appliedAt
    ) {
    }
}
