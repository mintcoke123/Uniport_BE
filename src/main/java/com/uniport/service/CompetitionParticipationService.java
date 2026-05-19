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
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CompetitionParticipationService {

    private static final String ACTIVE_TOURNAMENT_APPLICATION_BLOCK_MESSAGE =
            "이미 다른 토너먼트를 참여하고 있다면 새로운 토너먼트에 참여할 수 없어요";

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
        assertNoOverlappingOngoingTournamentApplication(competition, user);

        CompetitionApplication application = applicationRepository.findByCompetition_IdAndUser_Id(competitionId, user.getId())
                .orElseGet(() -> CompetitionApplication.builder()
                        .competition(competition)
                        .user(user)
                        .appliedAt(Instant.now())
                        .build());
        application.setTeamId(null);
        application.setTeamName(null);
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
        CompetitionApplication application = user != null && user.getId() != null
                ? applicationRepository.findByCompetition_IdAndUser_Id(competitionId, user.getId()).orElse(null)
                : null;

        if (application == null || !"APPLIED".equals(application.getStatus())) {
            Map<String, Object> response = new HashMap<>();
            response.put("competitionId", competition.getId());
            response.put("teamId", null);
            response.put("teamName", null);
            response.put("applied", false);
            response.put("status", "NOT_APPLIED");
            response.put("statusLabel", "참가 신청");
            response.put("message", "아직 참가 신청하지 않았어요.");
            return response;
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
                    Map<String, Object> response = new HashMap<>();
                    response.put("competitionId", competition.getId());
                    response.put("competitionName", competition.getName());
                    response.put("teamId", null);
                    response.put("teamName", null);
                    response.put("status", application.getStatus());
                    response.put("statusLabel", "APPLIED".equals(application.getStatus()) ? "신청 완료" : "신청 취소");
                    response.put("appliedAt", application.getAppliedAt().toString());
                    response.put("startDate", competition.getStartDate());
                    response.put("endDate", competition.getEndDate());
                    return response;
                })
                .toList();
    }

    public boolean isApplied(Long competitionId, User user) {
        if (competitionId == null || user == null || user.getId() == null) {
            return false;
        }
        return applicationRepository.existsByCompetition_IdAndUser_IdAndStatus(competitionId, user.getId(), "APPLIED");
    }

    private void assertNoOverlappingOngoingTournamentApplication(Competition requestedCompetition, User user) {
        List<CompetitionApplication> applications = applicationRepository.findByUser_IdOrderByAppliedAtDesc(user.getId());
        if (applications == null || applications.isEmpty()) {
            return;
        }
        boolean hasOtherActiveApplication = applications.stream()
                .filter(application -> "APPLIED".equals(application.getStatus()))
                .map(CompetitionApplication::getCompetition)
                .anyMatch(competition -> competition != null
                        && competition.getId() != null
                        && !competition.getId().equals(requestedCompetition.getId())
                        && "ongoing".equals(competitionService.resolveStatus(competition))
                        && overlaps(competition, requestedCompetition));
        if (hasOtherActiveApplication) {
            throw new ApiException(ACTIVE_TOURNAMENT_APPLICATION_BLOCK_MESSAGE, HttpStatus.BAD_REQUEST);
        }
    }

    private boolean overlaps(Competition left, Competition right) {
        LocalDateTime leftStart = parseDateTime(left.getStartDate());
        LocalDateTime leftEnd = parseDateTime(left.getEndDate());
        LocalDateTime rightStart = parseDateTime(right.getStartDate());
        LocalDateTime rightEnd = parseDateTime(right.getEndDate());
        if (leftStart == null || leftEnd == null || rightStart == null || rightEnd == null) {
            return false;
        }
        return leftStart.isBefore(rightEnd) && rightStart.isBefore(leftEnd);
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Competition getCompetition(Long competitionId) {
        return competitionRepository.findById(competitionId)
                .orElseThrow(() -> new ApiException("대회를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }

    private Map<String, Object> toApplicationMap(Competition competition,
                                                 CompetitionApplication application,
                                                 boolean applied,
                                                 String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("competitionId", competition.getId());
        response.put("teamId", null);
        response.put("teamName", null);
        response.put("applied", applied);
        response.put("status", application.getStatus());
        response.put("statusLabel", applied ? "신청 완료" : "신청 취소");
        response.put("appliedAt", application.getAppliedAt().toString());
        response.put("message", message);
        return response;
    }
}
