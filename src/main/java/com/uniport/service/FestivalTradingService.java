package com.uniport.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.dto.FestivalAdminOverviewDTO;
import com.uniport.dto.FestivalAdminSessionItemDTO;
import com.uniport.dto.FestivalLeaderboardItemDTO;
import com.uniport.dto.FestivalSessionCompleteRequestDTO;
import com.uniport.dto.FestivalSessionCompleteResponseDTO;
import com.uniport.dto.FestivalSessionStartRequestDTO;
import com.uniport.dto.FestivalSessionStartResponseDTO;
import com.uniport.entity.FestivalTradingSession;
import com.uniport.exception.ApiException;
import com.uniport.repository.FestivalTradingSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class FestivalTradingService {

    private static final BigDecimal START_CASH = new BigDecimal("100000000");
    private static final BigDecimal KEYRING_THRESHOLD = new BigDecimal("1.5");

    private final FestivalTradingSessionRepository sessionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FestivalTradingService(FestivalTradingSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public FestivalSessionStartResponseDTO startSession(FestivalSessionStartRequestDTO request) {
        String department = requireText(request.getDepartment(), "department");
        String studentId = requireText(request.getStudentId(), "studentId");
        String participantName = requireText(request.getName(), "name");
        String phoneNumber = requireText(request.getPhoneNumber(), "phoneNumber");
        if (!Boolean.TRUE.equals(request.getPrivacyAgreed())) {
            throw new ApiException("privacy agreement is required", HttpStatus.BAD_REQUEST);
        }

        long duplicateCount = sessionRepository.countByParticipantName(participantName);
        String displayName = duplicateCount == 0 ? participantName : participantName + (duplicateCount + 1);

        FestivalTradingSession session = sessionRepository.save(FestivalTradingSession.builder()
                .participantName(participantName)
                .displayName(displayName)
                .department(department)
                .studentId(studentId)
                .phoneNumber(phoneNumber)
                .privacyAgreed(true)
                .startCash(START_CASH)
                .tradeCount(0)
                .unfilledOrderCount(0)
                .startedAt(LocalDateTime.now())
                .build());

        return FestivalSessionStartResponseDTO.builder()
                .sessionId(session.getId())
                .displayName(session.getDisplayName())
                .startCash(session.getStartCash())
                .startedAt(session.getStartedAt())
                .build();
    }

    @Transactional
    public FestivalSessionCompleteResponseDTO completeSession(Long sessionId, FestivalSessionCompleteRequestDTO request) {
        FestivalTradingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ApiException("festival session not found", HttpStatus.NOT_FOUND));
        if (session.getEndedAt() != null) {
            throw new ApiException("festival session already completed", HttpStatus.BAD_REQUEST);
        }

        BigDecimal endCash = requireAmount(request.getEndCash(), "endCash");
        BigDecimal endPortfolioValue = requireAmount(request.getEndPortfolioValue(), "endPortfolioValue");
        BigDecimal endTotalValue = requireAmount(request.getEndTotalValue(), "endTotalValue");
        BigDecimal returnRate = request.getReturnRate() != null
                ? request.getReturnRate().setScale(4, RoundingMode.HALF_UP)
                : calculateReturnRate(endTotalValue);

        session.setEndCash(endCash);
        session.setEndPortfolioValue(endPortfolioValue);
        session.setEndTotalValue(endTotalValue);
        session.setReturnRate(returnRate);
        session.setMainStockName(trimToNull(request.getMainStockName()));
        session.setTradeCount(request.getTradeCount() != null ? Math.max(0, request.getTradeCount()) : 0);
        session.setUnfilledOrderCount(request.getUnfilledOrderCount() != null ? Math.max(0, request.getUnfilledOrderCount()) : 0);
        session.setHoldingsSnapshotJson(writeJson(request.getHoldingsSnapshot()));
        session.setTradeHistoryJson(writeJson(request.getTradeHistory()));
        session.setBasePrize(resolveBasePrize(returnRate));
        session.setEndedAt(LocalDateTime.now());
        sessionRepository.save(session);

        List<FestivalLeaderboardItemDTO> leaderboard = buildLeaderboard();
        Integer currentRank = leaderboard.stream()
                .filter(item -> item.getSessionId().equals(session.getId()))
                .map(FestivalLeaderboardItemDTO::getRank)
                .findFirst()
                .orElse(null);
        String finalPrize = leaderboard.stream()
                .filter(item -> item.getSessionId().equals(session.getId()))
                .map(FestivalLeaderboardItemDTO::getPrize)
                .findFirst()
                .orElse(session.getBasePrize());

        session.setFinalPrize(finalPrize);
        sessionRepository.save(session);

        return FestivalSessionCompleteResponseDTO.builder()
                .sessionId(session.getId())
                .displayName(session.getDisplayName())
                .startCash(session.getStartCash())
                .endTotalValue(session.getEndTotalValue())
                .returnRate(session.getReturnRate())
                .basePrize(session.getBasePrize())
                .finalPrize(finalPrize)
                .currentRank(currentRank)
                .leaderboard(leaderboard)
                .build();
    }

    @Transactional(readOnly = true)
    public List<FestivalLeaderboardItemDTO> getLeaderboard() {
        return buildLeaderboard();
    }

    @Transactional(readOnly = true)
    public FestivalAdminOverviewDTO getAdminOverview() {
        List<FestivalTradingSession> sessions = sessionRepository.findAllByOrderByStartedAtDesc();
        List<FestivalTradingSession> completedSessions = sessions.stream()
                .filter(session -> session.getEndedAt() != null)
                .toList();

        BigDecimal averageReturnRate = completedSessions.stream()
                .map(FestivalTradingSession::getReturnRate)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!completedSessions.isEmpty()) {
            averageReturnRate = averageReturnRate.divide(
                    BigDecimal.valueOf(completedSessions.size()),
                    4,
                    RoundingMode.HALF_UP
            );
        }

        BigDecimal bestReturnRate = completedSessions.stream()
                .map(FestivalTradingSession::getReturnRate)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(null);

        LocalDateTime lastCompletedAt = completedSessions.stream()
                .map(FestivalTradingSession::getEndedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        int qualifiedParticipants = (int) completedSessions.stream()
                .map(FestivalTradingSession::getReturnRate)
                .filter(Objects::nonNull)
                .filter(rate -> rate.compareTo(KEYRING_THRESHOLD) >= 0)
                .count();

        List<FestivalAdminSessionItemDTO> sessionItems = sessions.stream()
                .map(this::toAdminSessionItem)
                .toList();

        return FestivalAdminOverviewDTO.builder()
                .totalParticipants(sessions.size())
                .completedParticipants(completedSessions.size())
                .activeParticipants(sessions.size() - completedSessions.size())
                .qualifiedParticipants(qualifiedParticipants)
                .averageReturnRate(averageReturnRate)
                .bestReturnRate(bestReturnRate)
                .lastCompletedAt(lastCompletedAt)
                .sessions(sessionItems)
                .build();
    }

    private List<FestivalLeaderboardItemDTO> buildLeaderboard() {
        List<FestivalTradingSession> sessions = sessionRepository.findByEndedAtIsNotNullOrderByEndTotalValueDescEndedAtAsc();
        List<FestivalLeaderboardItemDTO> items = new ArrayList<>();
        for (int i = 0; i < sessions.size(); i++) {
            FestivalTradingSession session = sessions.get(i);
            int rank = i + 1;
            items.add(FestivalLeaderboardItemDTO.builder()
                    .sessionId(session.getId())
                    .rank(rank)
                    .displayName(session.getDisplayName())
                    .mainStockName(session.getMainStockName())
                    .endTotalValue(session.getEndTotalValue())
                    .returnRate(session.getReturnRate())
                    .prize(resolveFinalPrize(rank, session.getBasePrize()))
                    .endedAt(session.getEndedAt())
                    .build());
        }
        return items;
    }

    private FestivalAdminSessionItemDTO toAdminSessionItem(FestivalTradingSession session) {
        return FestivalAdminSessionItemDTO.builder()
                .sessionId(session.getId())
                .status(session.getEndedAt() == null ? "IN_PROGRESS" : "COMPLETED")
                .participantName(session.getParticipantName())
                .displayName(session.getDisplayName())
                .department(session.getDepartment())
                .studentId(session.getStudentId())
                .phoneNumber(session.getPhoneNumber())
                .startCash(session.getStartCash())
                .endCash(session.getEndCash())
                .endPortfolioValue(session.getEndPortfolioValue())
                .endTotalValue(session.getEndTotalValue())
                .returnRate(session.getReturnRate())
                .mainStockName(session.getMainStockName())
                .basePrize(session.getBasePrize())
                .finalPrize(session.getFinalPrize())
                .tradeCount(session.getTradeCount())
                .unfilledOrderCount(session.getUnfilledOrderCount())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .holdingsSnapshot(readJson(session.getHoldingsSnapshotJson()))
                .tradeHistory(readJson(session.getTradeHistoryJson()))
                .build();
    }

    private BigDecimal calculateReturnRate(BigDecimal endTotalValue) {
        return endTotalValue.subtract(START_CASH)
                .multiply(BigDecimal.valueOf(100))
                .divide(START_CASH, 4, RoundingMode.HALF_UP);
    }

    private String resolveBasePrize(BigDecimal returnRate) {
        return returnRate.compareTo(KEYRING_THRESHOLD) >= 0 ? "키링" : "간식";
    }

    private String resolveFinalPrize(int rank, String basePrize) {
        if (rank == 1) {
            return "상품권 3만 원";
        }
        if (rank == 2) {
            return "상품권 1만 원";
        }
        if (rank == 3) {
            return "커피 쿠폰";
        }
        return basePrize != null && !basePrize.isBlank() ? basePrize : "간식";
    }

    private String requireText(String value, String fieldName) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new ApiException(fieldName + " is required", HttpStatus.BAD_REQUEST);
        }
        return trimmed;
    }

    private BigDecimal requireAmount(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new ApiException(fieldName + " is required", HttpStatus.BAD_REQUEST);
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private String writeJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new ApiException("failed to serialize festival session payload", HttpStatus.BAD_REQUEST);
        }
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new ApiException("failed to read stored festival session payload", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
