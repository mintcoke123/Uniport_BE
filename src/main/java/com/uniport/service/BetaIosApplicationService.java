package com.uniport.service;

import com.uniport.dto.BetaIosApplicationRequestDTO;
import com.uniport.dto.BetaIosApplicationResponseDTO;
import com.uniport.entity.BetaIosApplication;
import com.uniport.entity.BetaIosApplicationStatus;
import com.uniport.exception.ApiException;
import com.uniport.repository.BetaIosApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class BetaIosApplicationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final List<BetaIosApplicationStatus> GROUP_SYNC_STATUSES = List.of(
            BetaIosApplicationStatus.USER_INVITE_SENT,
            BetaIosApplicationStatus.USER_INVITE_FAILED,
            BetaIosApplicationStatus.TESTFLIGHT_GROUP_FAILED
    );

    private final BetaIosApplicationRepository repository;
    private final AppStoreConnectUserInvitationClient invitationClient;
    private final AppStoreConnectBetaGroupClient betaGroupClient;

    public BetaIosApplicationService(BetaIosApplicationRepository repository,
                                     AppStoreConnectUserInvitationClient invitationClient,
                                     AppStoreConnectBetaGroupClient betaGroupClient) {
        this.repository = repository;
        this.invitationClient = invitationClient;
        this.betaGroupClient = betaGroupClient;
    }

    @Transactional
    public BetaIosApplicationResponseDTO submit(BetaIosApplicationRequestDTO request) {
        if (request == null || !Boolean.TRUE.equals(request.getConsent())) {
            throw new ApiException("iOS TestFlight 초대를 위한 개인정보 수집 및 이용 동의가 필요합니다.", HttpStatus.BAD_REQUEST);
        }

        String name = requireText(request.getName(), "name is required");
        String appleIdEmail = normalizeEmail(request.getAppleIdEmail(), "appleIdEmail is required");
        String contactEmail = normalizeOptionalEmail(request.getContactEmail(), appleIdEmail);
        String device = normalizeDevice(request.getDevice());

        BetaIosApplication application = repository.findByAppleIdEmail(appleIdEmail)
                .orElseGet(() -> BetaIosApplication.builder()
                        .appleIdEmail(appleIdEmail)
                        .status(BetaIosApplicationStatus.REQUESTED)
                        .build());
        application.setName(name);
        application.setContactEmail(contactEmail);
        application.setDevice(device);
        application.setConsent(true);
        application.setStatus(BetaIosApplicationStatus.REQUESTED);
        application.setInviteFailureMessage(null);
        application.setTestflightGroupFailureMessage(null);
        repository.save(application);

        AppStoreConnectUserInvitationResult inviteResult = invitationClient.inviteUser(
                new AppStoreConnectUserInvitationRequest(name, appleIdEmail)
        );
        applyInviteResult(application, inviteResult);
        BetaIosApplication saved = repository.save(application);
        return toResponse(saved, inviteResult);
    }

    @Transactional
    public void syncPendingInternalTesters() {
        List<BetaIosApplication> applications = repository.findTop50ByStatusInOrderByUpdatedAtAsc(GROUP_SYNC_STATUSES);
        for (BetaIosApplication application : applications) {
            AppStoreConnectBetaGroupSyncResult result = betaGroupClient.addTesterToInternalGroup(application.getAppleIdEmail());
            applyGroupSyncResult(application, result);
        }
    }

    private void applyInviteResult(BetaIosApplication application, AppStoreConnectUserInvitationResult inviteResult) {
        if (inviteResult.sent()) {
            application.setStatus(BetaIosApplicationStatus.USER_INVITE_SENT);
            if (inviteResult.invitationId() != null) {
                application.setAppStoreConnectInvitationId(inviteResult.invitationId());
            }
            application.setInvitedAt(LocalDateTime.now());
            return;
        }

        if (inviteResult.skipped()) {
            application.setStatus(BetaIosApplicationStatus.USER_INVITE_SKIPPED);
        } else {
            application.setStatus(BetaIosApplicationStatus.USER_INVITE_FAILED);
        }
        application.setInviteFailureMessage(inviteResult.message());
    }

    private void applyGroupSyncResult(BetaIosApplication application, AppStoreConnectBetaGroupSyncResult result) {
        if (result.pending()) {
            application.setTestflightGroupFailureMessage(result.message());
            return;
        }

        if (result.added()) {
            application.setStatus(BetaIosApplicationStatus.TESTFLIGHT_GROUP_ADDED);
            if (result.betaTesterId() != null) {
                application.setBetaTesterId(result.betaTesterId());
            }
            if (result.groupId() != null) {
                application.setTestflightGroupId(result.groupId());
            }
            application.setTestflightGroupFailureMessage(null);
            application.setTestflightGroupAddedAt(LocalDateTime.now());
            repository.save(application);
            return;
        }

        if (result.skipped()) {
            application.setTestflightGroupFailureMessage(result.message());
            return;
        }

        application.setStatus(BetaIosApplicationStatus.TESTFLIGHT_GROUP_FAILED);
        application.setTestflightGroupFailureMessage(result.message());
        repository.save(application);
    }

    private BetaIosApplicationResponseDTO toResponse(
            BetaIosApplication application,
            AppStoreConnectUserInvitationResult inviteResult
    ) {
        return BetaIosApplicationResponseDTO.builder()
                .id(application.getId())
                .name(application.getName())
                .appleIdEmail(application.getAppleIdEmail())
                .contactEmail(application.getContactEmail())
                .device(application.getDevice())
                .status(application.getStatus().name())
                .message(messageFor(application.getStatus(), inviteResult.message()))
                .build();
    }

    private String messageFor(BetaIosApplicationStatus status, String fallback) {
        return switch (status) {
            case USER_INVITE_SENT -> "Apple 초대 메일을 보냈습니다. 메일에서 App Store Connect 초대를 수락하면 TestFlight 내부 그룹 추가를 자동으로 시도합니다.";
            case USER_INVITE_SKIPPED -> "신청은 저장됐습니다. 서버의 App Store Connect 자동 초대 설정이 완료되면 초대를 보낼 수 있습니다.";
            case USER_INVITE_FAILED -> "신청은 저장됐지만 Apple 초대 자동 발송에 실패했습니다. 현장 담당자에게 문의해 주세요.";
            case TESTFLIGHT_GROUP_ADDED -> "TestFlight 내부 그룹에 추가됐습니다. TestFlight 앱에서 Uniport를 설치하세요.";
            case TESTFLIGHT_GROUP_FAILED -> "신청은 저장됐지만 TestFlight 내부 그룹 추가에 실패했습니다. 현장 담당자에게 문의해 주세요.";
            case REQUESTED -> fallback;
        };
    }

    private String requireText(String value, String message) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank()) {
            throw new ApiException(message, HttpStatus.BAD_REQUEST);
        }
        return trimmed;
    }

    private String normalizeEmail(String value, String message) {
        String email = requireText(value, message).toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ApiException("valid email is required", HttpStatus.BAD_REQUEST);
        }
        return email;
    }

    private String normalizeOptionalEmail(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return normalizeEmail(value, "contactEmail is invalid");
    }

    private String normalizeDevice(String value) {
        String device = value == null ? "" : value.trim();
        if (device.equalsIgnoreCase("iPad")) {
            return "iPad";
        }
        return "iPhone";
    }
}
