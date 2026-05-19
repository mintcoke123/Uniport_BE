package com.uniport.service;

import com.uniport.entity.Competition;
import com.uniport.entity.CompetitionApplication;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.CompetitionApplicationRepository;
import com.uniport.repository.CompetitionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class CompetitionParticipationService {

    private final CompetitionRepository competitionRepository;
    private final CompetitionApplicationRepository applicationRepository;
    private final CompetitionService competitionService;

    public CompetitionParticipationService(CompetitionRepository competitionRepository,
                                           CompetitionApplicationRepository applicationRepository,
                                           CompetitionService competitionService) {
        this.competitionRepository = competitionRepository;
        this.applicationRepository = applicationRepository;
        this.competitionService = competitionService;
    }

    @Transactional
    public Map<String, Object> apply(Long competitionId, User user, String participantTeamId, String participantName) {
        Competition competition = getCompetition(competitionId);
        if (user == null || user.getId() == null) {
            throw new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED);
        }
        if (!"upcoming".equals(competitionService.resolveStatus(competition))) {
            throw new ApiException("시작 전 토너먼트만 참가 신청할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }

        String teamId = participantTeamId != null && !participantTeamId.isBlank()
                ? participantTeamId.trim()
                : fallbackParticipantTeamId(user);
        String teamName = participantName != null && !participantName.isBlank()
                ? participantName.trim()
                : fallbackParticipantName(user);

        CompetitionApplication application = applicationRepository.findByCompetition_IdAndUser_Id(competitionId, user.getId())
                .orElseGet(() -> CompetitionApplication.builder()
                        .competition(competition)
                        .user(user)
                        .appliedAt(Instant.now())
                        .build());
        application.setTeamId(teamId);
        application.setTeamName(teamName);
        application.setStatus("APPLIED");
        application.setCancelledAt(null);
        CompetitionApplication saved = applicationRepository.save(application);

        return toApplicationMap(competition, saved, true, competition.getName() + " 참가 신청이 완료되었어요.");
    }

    @Transactional
    public Map<String, Object> cancel(Long competitionId, User user, String participantTeamId) {
        Competition competition = getCompetition(competitionId);
        if (user == null || user.getId() == null) {
            throw new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED);
        }
        if (!"upcoming".equals(competitionService.resolveStatus(competition))) {
            throw new ApiException("시작된 토너먼트는 참가 신청을 취소할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        CompetitionApplication application = applicationRepository.findByCompetition_IdAndUser_Id(competitionId, user.getId())
                .filter(candidate -> "APPLIED".equals(candidate.getStatus()))
                .orElseThrow(() -> new ApiException("참가 신청 내역이 없습니다.", HttpStatus.NOT_FOUND));
        application.setStatus("CANCELLED");
        application.setCancelledAt(Instant.now());
        CompetitionApplication saved = applicationRepository.save(application);

        return toApplicationMap(competition, saved, false, competition.getName() + " 참가 신청을 취소했어요.");
    }

    public Map<String, Object> getApplicationStatus(Long competitionId, User user, String participantTeamId) {
        Competition competition = getCompetition(competitionId);
        String teamId = participantTeamId != null && !participantTeamId.isBlank()
                ? participantTeamId.trim()
                : fallbackParticipantTeamId(user);
        CompetitionApplication application = user != null && user.getId() != null
                ? applicationRepository.findByCompetition_IdAndUser_Id(competitionId, user.getId()).orElse(null)
                : null;

        if (application == null || !"APPLIED".equals(application.getStatus())) {
            return Map.of(
                    "competitionId", competition.getId(),
                    "teamId", teamId,
                    "applied", false,
                    "status", "NOT_APPLIED",
                    "statusLabel", "참가 신청",
                    "message", "아직 참가 신청하지 않았어요."
            );
        }

        return toApplicationMap(competition, application, true, competition.getName() + " 참가 신청이 완료되었어요.");
    }

    public List<Map<String, Object>> getMyApplications(User user) {
        if (user == null || user.getId() == null) {
            throw new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED);
        }
        return applicationRepository.findByUser_IdOrderByAppliedAtDesc(user.getId()).stream()
                .map(application -> {
                    Competition competition = application.getCompetition();
                    return Map.<String, Object>of(
                            "competitionId", competition.getId(),
                            "competitionName", competition.getName(),
                            "teamId", application.getTeamId(),
                            "teamName", application.getTeamName(),
                            "status", application.getStatus(),
                            "statusLabel", "APPLIED".equals(application.getStatus()) ? "신청 완료" : "신청 취소",
                            "appliedAt", application.getAppliedAt().toString(),
                            "startDate", competition.getStartDate(),
                            "endDate", competition.getEndDate()
                    );
                })
                .toList();
    }

    public boolean isApplied(Long competitionId, String participantTeamId) {
        return applicationRepository.findAll().stream()
                .anyMatch(application -> application.getCompetition().getId().equals(competitionId)
                        && application.getTeamId().equals(participantTeamId)
                        && "APPLIED".equals(application.getStatus()));
    }

    public boolean isApplied(Long competitionId, User user) {
        if (competitionId == null || user == null || user.getId() == null) {
            return false;
        }
        return applicationRepository.existsByCompetition_IdAndUser_IdAndStatus(competitionId, user.getId(), "APPLIED");
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

    private Map<String, Object> toApplicationMap(Competition competition,
                                                 CompetitionApplication application,
                                                 boolean applied,
                                                 String message) {
        return Map.of(
                "competitionId", competition.getId(),
                "teamId", application.getTeamId(),
                "teamName", application.getTeamName(),
                "applied", applied,
                "status", application.getStatus(),
                "statusLabel", applied ? "신청 완료" : "신청 취소",
                "appliedAt", application.getAppliedAt().toString(),
                "message", message
        );
    }
}
